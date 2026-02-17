<template>
  <div class="dept-list-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>部门管理</span>
          <el-button type="primary" @click="handleAdd(null)">
            <el-icon><Plus /></el-icon> 新增顶级部门
          </el-button>
          <el-button type="success" @click="handleExport">
            导出 Excel
          </el-button>
        </div>
      </template>

      <!-- 树形表格 -->
      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="name" label="部门名称" width="200" />
        <el-table-column prop="leader" label="负责人" />
        <el-table-column prop="phone" label="联系电话" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'">
              {{ row.status === '1' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleAdd(row)">新增子部门</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级部门">
          <el-input v-model="parentDeptName" disabled />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="负责人" prop="leader">
          <el-input v-model="form.leader" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="1">启用</el-radio>
            <el-radio label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
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

const loading = ref(false);
const submitting = ref(false);
const tableData = ref<any[]>([]);
const allDepts = ref<any[]>([]);

const dialogVisible = ref(false);
const dialogTitle = ref('新增部门');
const formRef = ref();
const parentDeptName = ref('无');

const form = reactive({
  id: null as number | null,
  name: '',
  parentId: null as number | null,
  leader: '',
  phone: '',
  email: '',
  sortOrder: 0,
  status: '1',
});

const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
};

const loadData = async () => {
  loading.value = true;
  try {
    const result = await client.search('departments', { page: 0, size: 1000 });
    allDepts.value = result.content || [];
    tableData.value = buildTree(allDepts.value);
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

const buildTree = (list: any[]) => {
  const map: any = {};
  const roots: any[] = [];

  list.forEach((item) => {
    map[item.id] = { ...item, children: [] };
  });

  list.forEach((item) => {
    if (item.parentId && map[item.parentId]) {
      map[item.parentId].children.push(map[item.id]);
    } else {
      roots.push(map[item.id]);
    }
  });

  return roots;
};

const handleAdd = (parent: any) => {
  dialogTitle.value = parent ? '新增子部门' : '新增顶级部门';
  parentDeptName.value = parent ? parent.name : '无';
  Object.assign(form, {
    id: null,
    name: '',
    parentId: parent?.id || null,
    leader: '',
    phone: '',
    email: '',
    sortOrder: 0,
    status: '1',
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑部门';
  const parent = allDepts.value.find((d) => d.id === row.parentId);
  parentDeptName.value = parent ? parent.name : '无';
  Object.assign(form, row);
  dialogVisible.value = true;
};

const handleDialogClose = () => {
  formRef.value?.resetFields();
};

const handleSubmit = async () => {
  await formRef.value.validate();
  submitting.value = true;
  try {
    if (form.id) {
      await client.update('departments', form.id, form);
      ElMessage.success('更新成功');
    } else {
      await client.create('departments', form);
      ElMessage.success('创建成功');
    }
    dialogVisible.value = false;
    loadData();
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定删除该部门吗？', '提示', { type: 'warning' });
  try {
    await client.delete('departments', row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败');
  }
};

const handleExport = async () => {
  try {
    const blob = await client.export('departments', {});
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'departments.xlsx');
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error: any) {
    ElMessage.error('导出失败');
  }
};

onMounted(() => {
  loadData();
});
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
