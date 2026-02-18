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
            <el-button link type="primary" @click="handleEditDict(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteDict(row)">删除</el-button>
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
            <span>字典项列表 {{ currentDict ? `(${currentDict.dictName})` : '' }}</span>
          </template>
          <template #column-status="{ value }">
            <el-tag :type="value === '1' ? 'success' : 'danger'">
              {{ value === '1' ? '启用' : '禁用' }}
            </el-tag>
          </template>
          <template #row-actions="{ row }">
            <el-button link type="primary" @click="handleEditItem(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteItem(row)">删除</el-button>
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
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="dictForm.dictCode" :disabled="!!dictForm.id" />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="dictForm.dictName" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="dictForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitDict" :loading="submitting">确定</el-button>
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
        <el-form-item label="显示文本" prop="itemText">
          <el-input v-model="itemForm.itemText" />
        </el-form-item>
        <el-form-item label="实际值" prop="itemValue">
          <el-input v-model="itemForm.itemValue" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="itemForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="itemForm.status">
            <el-radio label="1">启用</el-radio>
            <el-radio label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="itemForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitItem" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePlatform, EntityCrudPage, type FilterCondition, BusinessError } from '@lrenyi/platform-headless/vue';

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
const dictDialogTitle = ref('新增字典');
const itemDialogTitle = ref('新增字典项');
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

const dictRules = {
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
};

const itemRules = {
  itemText: [{ required: true, message: '请输入显示文本', trigger: 'blur' }],
  itemValue: [{ required: true, message: '请输入实际值', trigger: 'blur' }],
};

// 字典相关操作
const handleAddDict = () => {
  dictDialogTitle.value = '新增字典';
  Object.assign(dictForm, {
    id: null,
    dictCode: '',
    dictName: '',
    description: '',
  });
  dictDialogVisible.value = true;
};

const handleEditDict = (row: any) => {
  dictDialogTitle.value = '编辑字典';
  Object.assign(dictForm, row);
  dictDialogVisible.value = true;
};

const handleDeleteDict = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认删除该字典?', '提示', {
      type: 'warning',
    });
    await dictClient.delete(row.id);
    ElMessage.success('删除成功');
    dictCrudRef.value?.refresh();
    if (currentDict.value?.id === row.id) {
      currentDict.value = null;
      // 这里的 refresh 会因为 itemFilters 为空而清空列表（或者不查询，取决于 EntityCrudPage 实现）
      // 我们希望清空，或者重置
      itemCrudRef.value?.refresh();
    }
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : '删除失败');
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
      ElMessage.success('更新成功');
    } else {
      delete (submitData as any).id;
      await dictClient.create(submitData);
      ElMessage.success('创建成功');
    }
    dictDialogVisible.value = false;
    dictCrudRef.value?.refresh();
  } catch (error: any) {
    if (error instanceof BusinessError) {
      ElMessage.error(error.message);
    } else {
      ElMessage.error('操作失败');
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
  itemDialogTitle.value = '新增字典项';
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
  itemDialogTitle.value = '编辑字典项';
  Object.assign(itemForm, row);
  itemDialogVisible.value = true;
};

const handleDeleteItem = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认删除该字典项?', '提示', {
      type: 'warning',
    });
    await itemClient.delete(row.id);
    ElMessage.success('删除成功');
    itemCrudRef.value?.refresh();
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : '删除失败');
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
      ElMessage.success('更新成功');
    } else {
      delete (submitData as any).id;
      await itemClient.create(submitData);
      ElMessage.success('创建成功');
    }
    itemDialogVisible.value = false;
    itemCrudRef.value?.refresh();
  } catch (error: any) {
    if (error instanceof BusinessError) {
      ElMessage.error(error.message);
    } else {
      ElMessage.error('操作失败');
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
