<template>
  <div class="role-list-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>角色管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增角色
          </el-button>
        </div>
      </template>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleCode" label="角色编码" />
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="handleAssignPermissions(row)">分配权限</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      @close="handleDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="600px">
      <el-tree
        ref="permTreeRef"
        :data="permTreeData"
        :props="{ label: 'name', children: 'children' }"
        show-checkbox
        node-key="id"
        :default-checked-keys="selectedPerms"
        v-loading="permsLoading"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePerms" :loading="submitting">确定</el-button>
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
const permsLoading = ref(false);
const tableData = ref<any[]>([]);
const permTreeData = ref<any[]>([]);
const selectedPerms = ref<number[]>([]);
const currentRole = ref<any>(null);
const permTreeRef = ref();

const dialogVisible = ref(false);
const permDialogVisible = ref(false);
const dialogTitle = ref('新增角色');
const formRef = ref();

const form = reactive({
  id: null as number | null,
  roleCode: '',
  roleName: '',
});

const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
};

const loadData = async () => {
  loading.value = true;
  try {
    const result = await client.search('roles', { page: 0, size: 1000 });
    tableData.value = result.content || [];
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  dialogTitle.value = '新增角色';
  Object.assign(form, {
    id: null,
    roleCode: '',
    roleName: '',
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑角色';
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
      await client.update('roles', form.id, form);
      ElMessage.success('更新成功');
    } else {
      await client.create('roles', form);
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
  await ElMessageBox.confirm('确定删除该角色吗？', '提示', { type: 'warning' });
  try {
    await client.delete('roles', row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败');
  }
};

const handleAssignPermissions = async (row: any) => {
  currentRole.value = row;
  permDialogVisible.value = true;
  await loadPermissions();
  await loadRolePermissions(row.id);
};

const loadPermissions = async () => {
  permsLoading.value = true;
  try {
    const result = await client.search('permissions', { page: 0, size: 1000 });
    const perms = result.content || [];
    // 简单平铺展示
    permTreeData.value = perms.map((p: any) => ({
      id: p.id,
      name: `${p.name} (${p.permission})`,
    }));
  } catch (error: any) {
    ElMessage.error('加载权限失败');
  } finally {
    permsLoading.value = false;
  }
};

const loadRolePermissions = async (roleId: number) => {
  try {
    const result = await client.search('role_permissions', {
      filters: [{ field: 'roleId', op: 'eq', value: roleId }],
      page: 0,
      size: 1000,
    });
    selectedPerms.value = (result.content || []).map((rp: any) => rp.permission?.id).filter(Boolean);
  } catch (error: any) {
    ElMessage.error('加载角色权限失败');
  }
};

const handleSavePerms = async () => {
  submitting.value = true;
  try {
    const checkedKeys = permTreeRef.value.getCheckedKeys();

    // 删除所有现有权限
    const existingResult = await client.search('role_permissions', {
      filters: [{ field: 'roleId', op: 'eq', value: currentRole.value.id }],
      page: 0,
      size: 1000,
    });
    const existing = existingResult.content || [];
    for (const rp of existing) {
      await client.delete('role_permissions', rp.id);
    }

    // 添加新权限
    for (const permId of checkedKeys) {
      await client.create('role_permissions', {
        role: { id: currentRole.value.id },
        permission: { id: permId },
      });
    }

    ElMessage.success('权限分配成功');
    permDialogVisible.value = false;
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.role-list-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
