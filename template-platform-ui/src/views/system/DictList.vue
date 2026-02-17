<template>
  <div class="dict-list-container">
    <el-row :gutter="20">
      <!-- 左侧：字典列表 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典列表</span>
              <el-button type="primary" @click="handleAddDict">
                <el-icon><Plus /></el-icon> 新增字典
              </el-button>
              <el-button type="success" @click="handleExportDict">
                导出
              </el-button>
            </div>
          </template>

          <el-table
            :data="dictList"
            v-loading="dictLoading"
            border
            stripe
            highlight-current-row
            @current-change="handleDictSelect"
          >
            <el-table-column prop="dictCode" label="字典编码" />
            <el-table-column prop="dictName" label="字典名称" />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleEditDict(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteDict(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：字典项列表 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典项列表 {{ currentDict ? `(${currentDict.dictName})` : '' }}</span>
              <el-button type="primary" @click="handleAddItem" :disabled="!currentDict">
                <el-icon><Plus /></el-icon> 新增字典项
              </el-button>
              <el-button type="success" @click="handleExportItem" :disabled="!currentDict">
                导出
              </el-button>
            </div>
          </template>

          <el-table :data="itemList" v-loading="itemLoading" border stripe>
            <el-table-column prop="itemText" label="显示文本" />
            <el-table-column prop="itemValue" label="实际值" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === '1' ? 'success' : 'danger'">
                  {{ row.status === '1' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleEditItem(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
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
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { getPlatform } from '@lrenyi/template-platform-frontend/vue';

const { client } = getPlatform();

const dictLoading = ref(false);
const itemLoading = ref(false);
const submitting = ref(false);
const dictList = ref<any[]>([]);
const itemList = ref<any[]>([]);
const currentDict = ref<any>(null);

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

const loadDicts = async () => {
  dictLoading.value = true;
  try {
    const result = await client.search('sys_dicts', { page: 0, size: 1000 });
    dictList.value = result.content || [];
  } catch (error: any) {
    ElMessage.error(error.message || '加载字典失败');
  } finally {
    dictLoading.value = false;
  }
};

const loadItems = async (dictCode: string) => {
  itemLoading.value = true;
  try {
    const result = await client.search('sys_dict_items', {
      filters: [{ field: 'dictCode', op: 'eq', value: dictCode }],
      page: 0,
      size: 1000,
    });
    itemList.value = result.content || [];
  } catch (error: any) {
    ElMessage.error(error.message || '加载字典项失败');
  } finally {
    itemLoading.value = false;
  }
};

const handleDictSelect = (row: any) => {
  currentDict.value = row;
  if (row) {
    loadItems(row.dictCode);
  } else {
    itemList.value = [];
  }
};

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

const handleDictDialogClose = () => {
  dictFormRef.value?.resetFields();
};

const handleSubmitDict = async () => {
  await dictFormRef.value.validate();
  submitting.value = true;
  try {
    if (dictForm.id) {
      await client.update('sys_dicts', dictForm.id, dictForm);
      ElMessage.success('更新成功');
    } else {
      await client.create('sys_dicts', dictForm);
      ElMessage.success('创建成功');
    }
    dictDialogVisible.value = false;
    loadDicts();
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

const handleDeleteDict = async (row: any) => {
  await ElMessageBox.confirm('确定删除该字典吗？', '提示', { type: 'warning' });
  try {
    await client.delete('sys_dicts', row.id);
    ElMessage.success('删除成功');
    if (currentDict.value?.id === row.id) {
      currentDict.value = null;
      itemList.value = [];
    }
    loadDicts();
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败');
  }
};

const handleExportDict = async () => {
  try {
    const blob = await client.export('sys_dicts', {});
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'dicts.xlsx');
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error: any) {
    ElMessage.error('导出失败');
  }
};

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

const handleItemDialogClose = () => {
  itemFormRef.value?.resetFields();
};

const handleSubmitItem = async () => {
  await itemFormRef.value.validate();
  submitting.value = true;
  try {
    if (itemForm.id) {
      await client.update('sys_dict_items', itemForm.id, itemForm);
      ElMessage.success('更新成功');
    } else {
      await client.create('sys_dict_items', itemForm);
      ElMessage.success('创建成功');
    }
    itemDialogVisible.value = false;
    loadItems(currentDict.value.dictCode);
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

const handleDeleteItem = async (row: any) => {
  await ElMessageBox.confirm('确定删除该字典项吗？', '提示', { type: 'warning' });
  try {
    await client.delete('sys_dict_items', row.id);
    ElMessage.success('删除成功');
    loadItems(currentDict.value.dictCode);
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败');
  }
};

const handleExportItem = async () => {
  if (!currentDict.value) return;
  try {
    const filters: any[] = [{ field: 'dictCode', op: 'eq', value: currentDict.value.dictCode }];
    const blob = await client.export('sys_dict_items', { filters });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `dict_items_${currentDict.value.dictCode}.xlsx`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error: any) {
    ElMessage.error('导出失败');
  }
};

onMounted(() => {
  loadDicts();
});
</script>

<style scoped>
.dict-list-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
