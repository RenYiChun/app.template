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
              :locale="dataforgeUiLocale"
              entity="sys_dicts"
              @create="handleAddDict"
              @delete="handleDeleteDict"
              @edit="handleEditDict"
              @export="handleExportDict"
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
                  :create-text="$t('common.create')"
                  :display-columns="scope.displayColumns"
                  :export-text="$t('common.export')"
                  :selected-ids="scope.selectedIds"
                  :set-visible-column-props="scope.setVisibleColumnProps"
                  :show-search="scope.showSearch"
                  :visible-column-props="scope.visibleColumnProps"
                  @create="handleAddDict"
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

            <template
                #table="{ items, loading, displayColumns, sort, selectable, handleSortChange, handleSelectionChange }">
              <EntityTable
                  :columns="displayColumns"
                  :handle-sort-change="handleSortChange"
                  :items="items"
                  :loading="loading"
                  :selectable="selectable"
                  :sort="sort"
                  @selection-change="handleSelectionChange"
              >
                <template #row-actions="{ row }">
                  <el-button link type="primary" @click="handleEditDict(row)">{{ $t('common.edit') }}</el-button>
                  <el-button link type="primary" @click="handleViewDictItems(row)">{{
                      $t('system.dict.viewItems')
                    }}
                  </el-button>
                  <el-button link type="danger" @click="handleDeleteDict(row)">{{ $t('common.delete') }}</el-button>
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
              :enable-create="!!currentDict"
              :locale="dataforgeUiLocale"
              entity="sys_dict_items"
              @create="handleAddDictItem"
              @delete="handleDeleteDictItem"
              @edit="handleEditDictItem"
              @export="handleExportItem"
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
                  :create-disabled="!currentDict"
                  :create-text="$t('common.create')"
                  :display-columns="scope.displayColumns"
                  :export-text="$t('common.export')"
                  :selected-ids="scope.selectedIds"
                  :set-visible-column-props="scope.setVisibleColumnProps"
                  :show-search="scope.showSearch"
                  :visible-column-props="scope.visibleColumnProps"
                  @create="handleAddDictItem"
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

            <template
                #table="{ items, loading, displayColumns, sort, selectable, handleSortChange, handleSelectionChange }">
              <EntityTable
                  :columns="displayColumns"
                  :handle-sort-change="handleSortChange"
                  :items="items"
                  :loading="loading"
                  :selectable="selectable"
                  :sort="sort"
                  @selection-change="handleSelectionChange"
              >
                <template #row-actions="{ row }">
                  <el-button link type="primary" @click="handleEditDictItem(row)">{{ $t('common.edit') }}</el-button>
                  <el-button link type="danger" @click="handleDeleteDictItem(row)">{{ $t('common.delete') }}</el-button>
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
      <el-form ref="dictFormRef" :model="dictForm" :rules="dictRules" label-width="100px">
        <el-form-item :label="$t('system.dict.name')" prop="name">
          <el-input v-model="dictForm.name"/>
        </el-form-item>
        <el-form-item :label="$t('system.dict.code')" prop="code">
          <el-input v-model="dictForm.code" :disabled="!!dictForm.id"/>
        </el-form-item>
        <el-form-item :label="$t('system.dict.description')" prop="description">
          <el-input v-model="dictForm.description" type="textarea"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="submitting" type="primary" @click="handleSubmitDict">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典项新增/编辑对话框 -->
    <el-dialog
        v-model="itemDialogVisible"
        :title="itemDialogTitle"
        width="600px"
        @close="handleItemDialogClose"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
        <el-form-item :label="$t('system.dict.itemLabel')" prop="label">
          <el-input v-model="itemForm.label"/>
        </el-form-item>
        <el-form-item :label="$t('system.dict.itemValue')" prop="value">
          <el-input v-model="itemForm.value"/>
        </el-form-item>
        <el-form-item :label="$t('system.dict.itemSort')" prop="sort">
          <el-input-number v-model="itemForm.sort" :min="0"/>
        </el-form-item>
        <el-form-item :label="$t('system.dict.itemDescription')" prop="description">
          <el-input v-model="itemForm.description" type="textarea"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="submitting" type="primary" @click="handleSubmitItem">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref} from 'vue';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElRow
} from 'element-plus';
import {BusinessError, useDataforge} from '@lrenyi/dataforge-headless/vue';
import {EntityCrudPage, EntitySearchBar, EntityTable, EntityToolbar} from '@lrenyi/dataforge-ui';
import {useI18n} from 'vue-i18n';
import {useDataforgeUiLocale} from '@/i18n';

const {t} = useI18n();
const dataforge = useDataforge();
const {client} = dataforge;

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

const submitting = ref(false);
const currentDict = ref<Dict | null>(null);

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
  name: [{required: true, message: t('system.dict.inputName'), trigger: 'blur'}],
  code: [{required: true, message: t('system.dict.inputCode'), trigger: 'blur'}],
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
  label: [{required: true, message: t('system.dict.inputItemLabel'), trigger: 'blur'}],
  value: [{required: true, message: t('system.dict.inputItemValue'), trigger: 'blur'}],
};

const itemFilters = computed(() => {
  return currentDict.value ? [{field: 'dictId', op: 'eq', value: currentDict.value.id}] : [];
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
  Object.assign(dictForm, {...row});
  dictDialogVisible.value = true;
};

const handleDictDialogClose = () => {
  dictFormRef.value?.resetFields();
};

const handleSubmitDict = async () => {
  await dictFormRef.value.validate();
  submitting.value = true;
  try {
    const submitData = {...dictForm} as Partial<Dict>;
    if (dictForm.id) {
      await dictClient.update(dictForm.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await dictClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    dictDialogVisible.value = false;
    dataforge.refreshCrud('sys_dicts');
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
  await ElMessageBox.confirm(t('system.dict.deleteConfirm'), t('common.tips'), {type: 'warning'});
  try {
    await dictClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    dataforge.refreshCrud('sys_dicts');
    if (currentDict.value?.id === row.id) {
      currentDict.value = null;
      dataforge.refreshCrud('sys_dict_items');
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
  dataforge.refreshCrud('sys_dict_items');
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
  Object.assign(itemForm, {...row});
  itemDialogVisible.value = true;
};

const handleItemDialogClose = () => {
  itemFormRef.value?.resetFields();
};

const handleSubmitItem = async () => {
  await itemFormRef.value.validate();
  submitting.value = true;
  try {
    const submitData = {...itemForm} as Partial<DictItem>;
    if (itemForm.id) {
      await dictItemClient.update(itemForm.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await dictItemClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    itemDialogVisible.value = false;
    itemCrudPageRef.value?.refresh();
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
  await ElMessageBox.confirm(t('system.dict.deleteItemConfirm'), t('common.tips'), {type: 'warning'});
  try {
    await dictItemClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    dataforge.refreshCrud('sys_dict_items');
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