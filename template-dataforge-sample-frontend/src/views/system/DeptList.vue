<template>
  <div class="dept-list-container">
    <EntityCrudPage
        :locale="dataforgeUiLocale"
        entity="departments"
        @create="handleAdd"
        @delete="handleDelete"
        @edit="handleEdit"
        @export="handleExport"
    >
      <template #alert="{ error }">
        <el-alert v-if="error" :title="error.message" class="mb-4" show-icon type="error"/>
      </template>

      <template #toolbar="scope">
        <EntityToolbar
            :all-columns="scope.allColumns"
            :batch-delete-text="$t('common.batchDelete')"
            :batch-update-text="$t('common.batchUpdate')"
            :can-batch-delete="scope.canBatchDelete"
            :can-batch-update="scope.canBatchUpdate"
            :can-create="true"
            :can-export="true"
            :create-text="$t('system.dept.addTop')"
            :display-columns="scope.displayColumns"
            :export-text="$t('common.export')"
            :selected-ids="scope.selectedIds"
            :set-visible-column-props="scope.setVisibleColumnProps"
            :show-search="scope.showSearch"
            :visible-column-props="scope.visibleColumnProps"
            @create="handleAdd"
            @export="scope.handleExport"
            @refresh="scope.handleSearch"
            @batch-delete="scope.handleDelete"
            @batch-update="scope.handleBatchUpdate"
            @toggle-search="scope.toggleSearch"
        />
      </template>

      <template #search="{ filters, setFilters, handleSearch, showSearch, entityMeta }">
        <EntitySearchBar
            v-if="showSearch"
            :entity-meta="entityMeta"
            :model-value="filters"
            @search="handleSearch"
            @update:modelValue="setFilters"
        />
      </template>

      <template #table="{ items, loading, displayColumns, sort, selectable, handleSortChange, handleSelectionChange }">
        <EntityTable
            :columns="displayColumns"
            :handle-sort-change="handleSortChange"
            :items="items"
            :loading="loading"
            :selectable="selectable"
            :sort="sort"
            @selection-change="handleSelectionChange"
        >
          <template #column-status="{ value }">
            <el-tag :type="value === '1' ? 'success' : 'danger'">
              {{ value === '1' ? $t('common.enable') : $t('common.disable') }}
            </el-tag>
          </template>
          <template #row-actions="{ row }">
            <el-button link type="primary" @click="handleAdd(row)">{{ $t('system.dept.addChild') }}</el-button>
            <el-button link type="primary" @click="handleEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </EntityTable>
      </template>

      <template #pagination="{ total, page, size, handlePageChange, handleSizeChange }">
        <el-pagination
            :current-page="page"
            :page-size="size"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            background
            class="mt-4"
            layout="total, sizes, prev, pager, next, jumper"
            @update:current-page="handlePageChange"
            @update:page-size="handleSizeChange"
        />
      </template>
    </EntityCrudPage>

    <!-- 新增/编辑对话框 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="500px"
        @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item v-if="form.parentId" :label="$t('system.dept.parent')">
          <el-tree-select
              v-model="form.parentId"
              :data="deptTreeData"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              :render-after-expand="false"
              check-strictly
          />
        </el-form-item>
        <el-form-item :label="$t('system.dept.name')" prop="name">
          <el-input v-model="form.name"/>
        </el-form-item>
        <el-form-item :label="$t('system.dept.leader')" prop="leader">
          <el-input v-model="form.leader"/>
        </el-form-item>
        <el-form-item :label="$t('system.dept.phone')" prop="phone">
          <el-input v-model="form.phone"/>
        </el-form-item>
        <el-form-item :label="$t('system.dept.email')" prop="email">
          <el-input v-model="form.email"/>
        </el-form-item>
        <el-form-item :label="$t('system.dept.sort')" prop="sort">
          <el-input-number v-model="form.sort" :min="0"/>
        </el-form-item>
        <el-form-item :label="$t('system.dept.status')" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="1">{{ $t('common.enable') }}</el-radio>
            <el-radio label="0">{{ $t('common.disable') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="submitting" type="primary" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {reactive, ref} from 'vue';
import {
  ElAlert,
  ElButton,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElRadio,
  ElRadioGroup,
  ElTag,
  ElTreeSelect
} from 'element-plus';
import {BusinessError, useDataforge} from '@lrenyi/dataforge-headless/vue';
import {EntityCrudPage, EntitySearchBar, EntityTable, EntityToolbar} from '@lrenyi/dataforge-ui';
import {useI18n} from 'vue-i18n';
import {useDataforgeUiLocale} from '@/i18n';

const {t} = useI18n();
const dataforge = useDataforge();
const {client} = dataforge;

const dataforgeUiLocale = useDataforgeUiLocale();

interface Department {
  id: number;
  name: string;
  parentId: number | null;
  sort: number;
  leader: string;
  phone: string;
  email: string;
  status: string;
  children?: Department[];
}

const deptClient = client.define<Department>('departments');

const submitting = ref(false);
const dialogVisible = ref(false);
const dialogTitle = ref(t('system.dept.addTop'));
const formRef = ref();
const deptTreeData = ref<Department[]>([]);

const form = reactive({
  id: null as number | null,
  parentId: null as number | null,
  name: '',
  leader: '',
  phone: '',
  email: '',
  sort: 0,
  status: '1',
});

const rules = {
  name: [{required: true, message: t('system.dept.name'), trigger: 'blur'}],
};

const handleAdd = (row: any | null) => {
  if (row) {
    dialogTitle.value = t('system.dept.addChild');
    form.parentId = row.id;
  } else {
    dialogTitle.value = t('system.dept.addTop');
    form.parentId = null;
  }
  form.id = null;
  form.name = '';
  form.leader = '';
  form.phone = '';
  form.email = '';
  form.sort = 0;
  form.status = '1';
  dialogVisible.value = true;
  loadDeptTree();
};

const handleEdit = (row: any) => {
  dialogTitle.value = t('system.dept.edit');
  Object.assign(form, row);
  dialogVisible.value = true;
  loadDeptTree();
};

const handleDialogClose = () => {
  formRef.value?.resetFields();
};

const handleSubmit = async () => {
  await formRef.value.validate();
  submitting.value = true;
  try {
    const submitData = {...form};
    if (form.id) {
      await deptClient.update(form.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await deptClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    dialogVisible.value = false;
    dataforge.refreshCrud('departments');
  } catch (error: any) {
    if (error instanceof BusinessError) {
      ElMessage.error(error.message);
    } else {
      ElMessage.error(t('common.operationFailed'));
      console.error(error);
    }
  } finally {
    submitting.value = false;
  }
};

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(t('system.dept.deleteConfirm'), t('common.tips'), {type: 'warning'});
  try {
    await deptClient.delete(row.id);
    ElMessage.success(t('system.dept.deleteSuccess'));
    dataforge.refreshCrud('departments');
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('system.dept.deleteFailed'));
  }
};

const handleExport = async () => {
  try {
    ElMessage.success('导出功能待实现');
  } catch {
    ElMessage.error(t('system.dept.deleteFailed').replace('删除', '导出'));
  }
};

const loadDeptTree = async () => {
  try {
    const result = await deptClient.search({page: 0, size: 1000});
    const list = result.content || [];
    deptTreeData.value = buildTree(list);
  } catch (error) {
    console.error('加载部门树失败', error);
  }
};

const buildTree = (list: any[]) => {
  const map: any = {};
  const roots: any[] = [];
  // Deep copy to avoid modifying original data if needed, 
  // but here we are building a new structure
  const nodeList = list.map(item => ({...item, children: []}));

  nodeList.forEach((item) => {
    map[item.id] = item;
  });

  nodeList.forEach((item) => {
    if (item.parentId && map[item.parentId]) {
      map[item.parentId].children.push(item);
    } else {
      roots.push(item);
    }
  });
  return roots;
};
</script>

<style scoped>
.dept-list-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
