<template>
  <div class="dept-list-container">
    <EntityCrudPage
      ref="crudRef"
      entity="departments"
      :columns="columns"
      :search-fields="['name']"
      :show-create="true"
      @create="handleAdd"
      @edit="handleEdit"
      @delete="handleDelete"
    >
      <template #header-actions>
        <el-button type="primary" @click="handleAdd(null)">{{ $t('system.dept.addTop') }}</el-button>
        <el-button type="success" @click="handleExport">{{ $t('common.export') }}</el-button>
      </template>

      <template #row-actions="{ row }">
        <el-button link type="primary" @click="handleAdd(row)">{{ $t('system.dept.addChild') }}</el-button>
        <el-button link type="primary" @click="handleEdit(row)">{{ $t('common.edit') }}</el-button>
        <el-button link type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
      </template>
    </EntityCrudPage>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      @close="handleDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="$t('system.dept.parent')" v-if="form.parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="deptTreeData"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            :render-after-expand="false"
          />
        </el-form-item>
        <el-form-item :label="$t('system.dept.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('system.dept.leader')" prop="leader">
          <el-input v-model="form.leader" />
        </el-form-item>
        <el-form-item :label="$t('system.dept.phone')" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item :label="$t('system.dept.email')" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="$t('system.dept.sort')" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
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
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePlatform, BusinessError } from '@lrenyi/platform-headless/vue';
import { EntityCrudPage } from '@lrenyi/platform-ui';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const { client } = usePlatform();

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
const crudRef = ref();
const submitting = ref(false);
const dialogVisible = ref(false);
const dialogTitle = ref(t('system.dept.addTop'));
const formRef = ref();
const deptTreeData = ref<Department[]>([]);

const columns = computed(() => [
  { prop: 'name', label: t('system.dept.name'), width: 200 },
  { prop: 'leader', label: t('system.dept.leader') },
  { prop: 'phone', label: t('system.dept.phone') },
  { prop: 'email', label: t('system.dept.email') },
  { prop: 'sort', label: t('system.dept.sort'), width: 80 },
  { 
    prop: 'status', 
    label: t('system.dept.status'), 
    width: 80,
    formatter: (row: any) => row.status === '1' ? t('common.enable') : t('common.disable')
  },
]);

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
  name: [{ required: true, message: t('system.dept.name'), trigger: 'blur' }],
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
    const submitData = { ...form };
    if (form.id) {
      await deptClient.update(form.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await deptClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    dialogVisible.value = false;
    crudRef.value?.refresh();
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
  await ElMessageBox.confirm(t('system.dept.deleteConfirm'), t('common.tips'), { type: 'warning' });
  try {
    await deptClient.delete(row.id);
    ElMessage.success(t('system.dept.deleteSuccess'));
    crudRef.value?.refresh();
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('system.dept.deleteFailed'));
  }
};

const handleExport = async () => {
  try {
    // 导出逻辑需要根据实际后端API调整
    // const blob = await deptClient.exportExcel();
    // download(blob, 'departments.xlsx');
    ElMessage.success('导出功能待实现');
  } catch {
    ElMessage.error(t('system.dept.deleteFailed').replace('删除', '导出'));
  }
};

const loadDeptTree = async () => {
  try {
    const result = await deptClient.search({ page: 0, size: 1000 });
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
  const nodeList = list.map(item => ({ ...item, children: [] }));
  
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
