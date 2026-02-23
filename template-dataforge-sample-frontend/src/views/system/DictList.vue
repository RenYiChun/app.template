<template>
  <div class="dict-list-container">
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card class="box-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $t('system.dict.manage') }}</span>
            </div>
          </template>
          <EntityCrudPage
            entity="dicts"
            :columns="dictColumns"
            :locale="dataforgeUiLocale"
            @create="handleAddDict"
            @edit="handleEditDict"
            @delete="handleDeleteDict"
            @export="handleExportDict"
          >
            <template #alert="{ error }">
              <el-alert v-if="error" :title="error.message" type="error" show-icon class="mb-4" />
            </template>

            <template #toolbar="scope">
              <EntityToolbar
                :selected-ids="scope.selectedIds"
                :can-create="true"
                :create-text="$t('common.create')"
                :can-batch-delete="true"
                :batch-delete-text="$t('common.batchDelete')"
                :can-export="true"
                :export-text="$t('common.export')"
                :show-search="scope.showSearch"
                :all-columns="scope.allColumns"
                :display-columns="scope.displayColumns"
                :visible-column-props="scope.visibleColumnProps"
                :set-visible-column-props="scope.setVisibleColumnProps"
                @create="handleAddDict"
                @batch-delete="scope.handleDelete"
                @export="scope.handleExport"
                @toggle-search="scope.toggleSearch"
                @refresh="scope.handleSearch"
              />
            </template>

            <template #search="{ filters, handleSearch, showSearch }">
              <EntitySearchBar
                v-if="showSearch"
                :filters="filters"
                :handle-search="handleSearch"
              />
            </template>

            <template #table="{ items, loading, displayColumns, sort, handleSortChange, handleSelectionChange }">
              <EntityTable
                :items="items"
                :loading="loading"
                :display-columns="displayColumns"
                :sort="sort"
                :handle-sort-change="handleSortChange"
                :handle-selection-change="handleSelectionChange"
              >
                <template #row-actions="{ row }">
                  <el-button link type="primary" @click="handleEditDict(row)">{{ $t('common.edit') }}</el-button>
                  <el-button link type="primary" @click="handleViewDictItems(row)">{{ $t('system.dict.viewItems') }}</el-button>
                  <el-button link type="danger" @click="handleDeleteDict(row)">{{ $t('common.delete') }}</el-button>
                </template>
              </EntityTable>
            </template>

            <template #pagination="{ total, page, size, handlePageChange, handleSizeChange }">
              <el-pagination
                class="mt-4"
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="total"
                :current-page="page"
                :page-size="size"
                :page-sizes="[10, 20, 50, 100]"
                @update:current-page="handlePageChange"
                @update:page-size="handleSizeChange"
              />
            </template>
          </EntityCrudPage>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card class="box-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $t('system.dict.itemManage') }} ({{ currentDict?.name || $t('system.dict.selectDict') }})</span>
            </div>
          </template>
          <EntityCrudPage
            entity="sys_dict_items"
            :columns="itemColumns"
            :locale="dataforgeUiLocale"
            :filters="itemFilters"
            :enable-create="!!currentDict"
            @create="handleAddDictItem"
            @edit="handleEditDictItem"
            @delete="handleDeleteDictItem"
            @export="handleExportItem"
          >
            <template #alert="{ error }">
              <el-alert v-if="error" :title="error.message" type="error" show-icon class="mb-4" />
            </template>

            <template #toolbar="scope">
              <EntityToolbar
                :selected-ids="scope.selectedIds"
                :can-create="true"
                :create-text="$t('common.create')"
                :create-disabled="!currentDict"
                :can-batch-delete="true"
                :batch-delete-text="$t('common.batchDelete')"
                :can-export="true"
                :export-text="$t('common.export')"
                :show-search="scope.showSearch"
                :all-columns="scope.allColumns"
                :display-columns="scope.displayColumns"
                :visible-column-props="scope.visibleColumnProps"
                :set-visible-column-props="scope.setVisibleColumnProps"
                @create="handleAddDictItem"
                @batch-delete="scope.handleDelete"
                @export="scope.handleExport"
                @toggle-search="scope.toggleSearch"
                @refresh="scope.handleSearch"
              />
            </template>

            <template #search="{ filters, handleSearch, showSearch }">
              <EntitySearchBar
                v-if="showSearch"
                :filters="filters"
                :handle-search="handleSearch"
              />
            </template>

            <template #table="{ items, loading, displayColumns, sort, handleSortChange, handleSelectionChange }">
              <EntityTable
                :items="items"
                :loading="loading"
                :display-columns="displayColumns"
                :sort="sort"
                :handle-sort-change="handleSortChange"
                :handle-selection-change="handleSelectionChange"
              >
                <template #row-actions="{ row }">
                  <el-button link type="primary" @click="handleEditDictItem(row)">{{ $t('common.edit') }}</el-button>
                  <el-button link type="danger" @click="handleDeleteDictItem(row)">{{ $t('common.delete') }}</el-button>
                </template>
              </EntityTable>
            </template>

            <template #pagination="{ total, page, size, handlePageChange, handleSizeChange }">
              <el-pagination
                class="mt-4"
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="total"
                :current-page="page"
                :page-size="size"
                :page-sizes="[10, 20, 50, 100]"
                @update:current-page="handlePageChange"
                @update:page-size="handleSizeChange"
              />
            </template>
          </EntityCrudPage>
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典新增/编辑对话框 -->
    <el-dialog
      v-model="dictDialogVisible"
      :title="dictDialogTitle"
      width="600px"
      @close="handleDictDialogClose"
    >
      <el-form :model="dictForm" :rules="dictRules" ref="dictFormRef" label-width="100px">
        <el-form-item :label="$t('system.dict.name')" prop="name">
          <el-input v-model="dictForm.name" />
        </el-form-item>
        <el-form-item :label="$t('system.dict.code')" prop="code">
          <el-input v-model="dictForm.code" :disabled="!!dictForm.id" />
        </el-form-item>
        <el-form-item :label="$t('system.dict.description')" prop="description">
          <el-input v-model="dictForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmitDict" :loading="submitting">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典项新增/编辑对话框 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogTitle"
      width="600px"
      @close="handleItemDialogClose"
    >
      <el-form :model="itemForm" :rules="itemRules" ref="itemFormRef" label-width="100px">
        <el-form-item :label="$t('system.dict.itemLabel')" prop="label">
          <el-input v-model="itemForm.label" />
        </el-form-item>
        <el-form-item :label="$t('system.dict.itemValue')" prop="value">
          <el-input v-model="itemForm.value" />
        </el-form-item>
        <el-form-item :label="$t('system.dict.itemSort')" prop="sort">
          <el-input-number v-model="itemForm.sort" :min="0" />
        </el-form-item>
        <el-form-item :label="$t('system.dict.itemDescription')" prop="description">
          <el-input v-model="itemForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmitItem" :loading="submitting">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { ElMessage, ElMessageBox, ElCard, ElAlert, ElPagination, ElButton, ElInput, ElInputNumber, ElDialog, ElForm, ElFormItem, ElRow, ElCol } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { useDataforge, BusinessError, useEntityCrud } from '@lrenyi/dataforge-headless/vue';
import { EntityCrudPage, EntityTable, EntitySearchBar, EntityToolbar } from '@lrenyi/dataforge-ui';
import { useI18n } from 'vue-i18n';
import { useDataforgeUiLocale } from '@/i18n';

const { t } = useI18n();
const { client } = useDataforge();

const dataforgeUiLocale = useDataforgeUiLocale();

interface Dict {
  id: number;
  name: string;
  code: string;
  description: string;
}

interface DictItem {
  id: number;
  dictId: number;
  label: string;
  value: string;
  sort: number;
  description: string;
}

const dictClient = client.define<Dict>('dicts');
const dictItemClient = client.define<DictItem>('sys_dict_items');

const { search: dictSearch } = useEntityCrud<Dict>('dicts');
const { search: itemSearch } = useEntityCrud<DictItem>('sys_dict_items');

const submitting = ref(false);
const currentDict = ref<Dict | null>(null);

const dictColumns = computed(() => [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'name', label: t('system.dict.name') },
  { prop: 'code', label: t('system.dict.code') },
  { prop: 'description', label: t('system.dict.description') },
]);

const itemColumns = computed(() => [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'label', label: t('system.dict.itemLabel') },
  { prop: 'value', label: t('system.dict.itemValue') },
  { prop: 'sort', label: t('system.dict.itemSort'), width: 80 },
  { prop: 'description', label: t('system.dict.itemDescription') },
]);

const dictDialogVisible = ref(false);
const dictDialogTitle = ref(t('system.dict.add'));
const dictFormRef = ref();
const dictForm = reactive({
  id: null as number | null,
  name: '',
  code: '',
  description: '',
});

const dictRules = {
  name: [{ required: true, message: t('system.dict.inputName'), trigger: 'blur' }],
  code: [{ required: true, message: t('system.dict.inputCode'), trigger: 'blur' }],
};

const itemDialogVisible = ref(false);
const itemDialogTitle = ref(t('system.dict.addItem'));
const itemFormRef = ref();
const itemForm = reactive({
  id: null as number | null,
  dictId: 0,
  label: '',
  value: '',
  sort: 0,
  description: '',
});

const itemRules = {
  label: [{ required: true, message: t('system.dict.inputItemLabel'), trigger: 'blur' }],
  value: [{ required: true, message: t('system.dict.inputItemValue'), trigger: 'blur' }],
};

const itemFilters = computed(() => {
  return currentDict.value ? [{ field: 'dictId', op: 'eq', value: currentDict.value.id }] : [];
});

// 字典操作
const handleAddDict = () => {
  dictDialogTitle.value = t('system.dict.add');
  Object.assign(dictForm, {
    id: null,
    name: '',
    code: '',
    description: '',
  });
  dictDialogVisible.value = true;
};

const handleEditDict = (row: Dict) => {
  dictDialogTitle.value = t('system.dict.edit');
  Object.assign(dictForm, { ...row });
  dictDialogVisible.value = true;
};

const handleDictDialogClose = () => {
  dictFormRef.value?.resetFields();
};

const handleSubmitDict = async () => {
  await dictFormRef.value.validate();
  submitting.value = true;
  try {
    const submitData = { ...dictForm };
    if (dictForm.id) {
      await dictClient.update(dictForm.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await dictClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    dictDialogVisible.value = false;
    dictSearch();
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

const handleDeleteDict = async (row: Dict) => {
  await ElMessageBox.confirm(t('system.dict.deleteConfirm'), t('common.tips'), { type: 'warning' });
  try {
    await dictClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    dictSearch();
    if (currentDict.value?.id === row.id) {
      currentDict.value = null;
      itemSearch(); // Refresh item list if the current dict is deleted
    }
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
  }
};

const handleExportDict = async () => {
  try {
    // 导出逻辑需要根据实际后端API调整
    ElMessage.success('字典导出功能待实现');
  } catch {
    ElMessage.error(t('common.exportFailed'));
  }
};

const handleViewDictItems = (row: Dict) => {
  currentDict.value = row;
  itemSearch();
};

// 字典项操作
const handleAddDictItem = () => {
  if (!currentDict.value) {
    ElMessage.warning(t('system.dict.selectDictFirst'));
    return;
  }
  itemDialogTitle.value = t('system.dict.addItem');
  Object.assign(itemForm, {
    id: null,
    dictId: currentDict.value.id,
    label: '',
    value: '',
    sort: 0,
    description: '',
  });
  itemDialogVisible.value = true;
};

const handleEditDictItem = (row: DictItem) => {
  itemDialogTitle.value = t('system.dict.editItem');
  Object.assign(itemForm, { ...row });
  itemDialogVisible.value = true;
};

const handleItemDialogClose = () => {
  itemFormRef.value?.resetFields();
};

const handleSubmitItem = async () => {
  await itemFormRef.value.validate();
  submitting.value = true;
  try {
    const submitData = { ...itemForm };
    if (itemForm.id) {
      await dictItemClient.update(itemForm.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await dictItemClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    itemDialogVisible.value = false;
    itemSearch();
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

const handleDeleteDictItem = async (row: DictItem) => {
  await ElMessageBox.confirm(t('system.dict.deleteItemConfirm'), t('common.tips'), { type: 'warning' });
  try {
    await dictItemClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    itemSearch();
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
  }
};

const handleExportItem = async () => {
  try {
    // 导出逻辑需要根据实际后端API调整
    ElMessage.success('字典项导出功能待实现');
  } catch {
    ElMessage.error(t('common.exportFailed'));
  }
};
</script>

<style scoped>
.dict-list-container {
  padding: 0;
}
</style>