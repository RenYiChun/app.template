<template>
  <div class="dict-list-container">
    <el-row :gutter="20">
      <!-- 左侧：字典列表 -->
      <el-col :span="10">
        <EntityCrudPage
          ref="dictCrudRef"
          entity="sys_dicts"
          :search-fields="['dictCode', 'dictName']"
          @create="handleAddDict"
          @edit="handleEditDict"
          @delete="handleDeleteDict"
          @row-click="handleDictSelect"
        >
          <template #row-actions="{ row }">
            <el-button link type="primary" @click="handleEditDict(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDeleteDict(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </EntityCrudPage>
      </el-col>

      <!-- 右侧：字典项列表 -->
      <el-col :span="14">
        <EntityCrudPage
          ref="itemCrudRef"
          entity="sys_dict_items"
          :base-filters="itemFilters"
          :immediate="false"
          :enable-create="!!currentDict"
          :search-fields="['itemText', 'itemValue']"
          @create="handleAddItem"
          @edit="handleEditItem"
          @delete="handleDeleteItem"
        >
          <template #header>
            <span>{{ $t('system.dict.itemTitle') }} {{ currentDict ? `(${currentDict.dictName})` : '' }}</span>
          </template>
          <template #column-status="{ value }">
            <el-tag :type="value === '1' ? 'success' : 'danger'">
              {{ value === '1' ? $t('common.enable') : $t('common.disable') }}
            </el-tag>
          </template>
          <template #row-actions="{ row }">
            <el-button link type="primary" @click="handleEditItem(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDeleteItem(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </EntityCrudPage>
      </el-col>
    </el-row>

    <!-- 新增/编辑字典对话框 -->
    <el-dialog
      v-model="dictDialogVisible"
      :title="dictDialogTitle"
      width="500px"
      @close="handleDictDialogClose"
    >
      <el-form :model="dictForm" :rules="dictRules" ref="dictFormRef" label-width="100px">
        <el-form-item :label="$t('system.dict.code')" prop="dictCode">
          <el-input v-model="dictForm.dictCode" :disabled="!!dictForm.id" />
        </el-form-item>
        <el-form-item :label="$t('system.dict.name')" prop="dictName">
          <el-input v-model="dictForm.dictName" />
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

    <!-- 新增/编辑字典项对话框 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogTitle"
      width="500px"
      @close="handleItemDialogClose"
    >
      <el-form :model="itemForm" :rules="itemRules" ref="itemFormRef" label-width="100px">
        <el-form-item :label="$t('system.dictItem.text')" prop="itemText">
          <el-input v-model="itemForm.itemText" />
        </el-form-item>
        <el-form-item :label="$t('system.dictItem.value')" prop="itemValue">
          <el-input v-model="itemForm.itemValue" />
        </el-form-item>
        <el-form-item :label="$t('system.dictItem.sort')" prop="sortOrder">
          <el-input-number v-model="itemForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="$t('system.dictItem.status')" prop="status">
          <el-radio-group v-model="itemForm.status">
            <el-radio label="1">{{ $t('common.enable') }}</el-radio>
            <el-radio label="0">{{ $t('common.disable') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('system.dictItem.description')" prop="description">
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
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePlatform, type FilterCondition, BusinessError } from '@lrenyi/platform-headless/vue';
import { EntityCrudPage } from '@lrenyi/platform-ui';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const { client } = usePlatform();

interface Dict {
  id: number;
  dictCode: string;
  dictName: string;
  description: string;
  createTime: string;
}

interface DictItem {
  id: number;
  dictCode: string;
  itemText: string;
  itemValue: string;
  sortOrder: number;
  status: string;
  description: string;
}

const dictClient = client.define<Dict>('sys_dicts');
const itemClient = client.define<DictItem>('sys_dict_items');

const dictCrudRef = ref();
const itemCrudRef = ref();
const submitting = ref(false);
const currentDict = ref<any>(null);

// 字典项过滤条件
const itemFilters = computed<FilterCondition[]>(() => {
  if (!currentDict.value) return [];
  return [{ field: 'dictCode', op: 'eq', value: currentDict.value.dictCode }];
});

const dictDialogVisible = ref(false);
const itemDialogVisible = ref(false);
const dictDialogTitle = ref(t('system.dict.add'));
const itemDialogTitle = ref(t('system.dictItem.add'));
const dictFormRef = ref();
const itemFormRef = ref();

const dictForm = reactive({
  id: null as number | null,
  dictCode: '',
  dictName: '',
  description: '',
});

const itemForm = reactive({
  id: null as number | null,
  dictCode: '',
  itemText: '',
  itemValue: '',
  sortOrder: 0,
  status: '1',
  description: '',
});

const dictRules = computed(() => ({
  dictCode: [{ required: true, message: t('system.dict.inputCode'), trigger: 'blur' }],
  dictName: [{ required: true, message: t('system.dict.inputName'), trigger: 'blur' }],
}));

const itemRules = computed(() => ({
  itemText: [{ required: true, message: t('system.dictItem.inputText'), trigger: 'blur' }],
  itemValue: [{ required: true, message: t('system.dictItem.inputValue'), trigger: 'blur' }],
}));

// 字典相关操作
const handleAddDict = () => {
  dictDialogTitle.value = t('system.dict.add');
  Object.assign(dictForm, {
    id: null,
    dictCode: '',
    dictName: '',
    description: '',
  });
  dictDialogVisible.value = true;
};

const handleEditDict = (row: any) => {
  dictDialogTitle.value = t('system.dict.edit');
  Object.assign(dictForm, row);
  dictDialogVisible.value = true;
};

const handleDeleteDict = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('system.dict.deleteConfirm'), t('common.tips'), {
      type: 'warning',
    });
    await dictClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    dictCrudRef.value?.refresh();
    if (currentDict.value?.id === row.id) {
      currentDict.value = null;
      // 这里的 refresh 会因为 itemFilters 为空而清空列表（或者不查询，取决于 EntityCrudPage 实现）
      // 我们希望清空，或者重置
      itemCrudRef.value?.refresh();
    }
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
  }
};

const handleSubmitDict = async () => {
  if (!dictFormRef.value) return;
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
    dictCrudRef.value?.refresh();
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

const handleDictDialogClose = () => {
  dictFormRef.value?.resetFields();
};

const handleDictSelect = (row: any) => {
  currentDict.value = row;
  // itemFilters is computed, so it updates automatically.
  // EntityCrudPage watches baseFilters (itemFilters) and should auto-refresh.
};

// 字典项相关操作
const handleAddItem = () => {
  if (!currentDict.value) return;
  itemDialogTitle.value = t('system.dictItem.add');
  Object.assign(itemForm, {
    id: null,
    dictCode: currentDict.value.dictCode,
    itemText: '',
    itemValue: '',
    sortOrder: 0,
    status: '1',
    description: '',
  });
  itemDialogVisible.value = true;
};

const handleEditItem = (row: any) => {
  itemDialogTitle.value = t('system.dictItem.edit');
  Object.assign(itemForm, row);
  itemDialogVisible.value = true;
};

const handleDeleteItem = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('system.dictItem.deleteConfirm'), t('common.tips'), {
      type: 'warning',
    });
    await itemClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    itemCrudRef.value?.refresh();
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
  }
};

const handleSubmitItem = async () => {
  if (!itemFormRef.value) return;
  await itemFormRef.value.validate();
  submitting.value = true;
  try {
    const submitData = { ...itemForm };
    if (itemForm.id) {
      await itemClient.update(itemForm.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await itemClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    itemDialogVisible.value = false;
    itemCrudRef.value?.refresh();
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

const handleItemDialogClose = () => {
  itemFormRef.value?.resetFields();
};
</script>

<style scoped>
.dict-list-container {
  padding: 20px;
}
</style>
