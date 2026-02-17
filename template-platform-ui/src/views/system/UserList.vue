<template>
  <div class="user-list-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增用户
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="正常" value="1" />
            <el-option label="停用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'">
              {{ row.status === '1' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="handleAssignRoles(row)">分配角色</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
        class="pagination"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="1">正常</el-radio>
            <el-radio label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px">
      <el-checkbox-group v-model="selectedRoles" v-loading="rolesLoading">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoles" :loading="submitting">确定</el-button>
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
const rolesLoading = ref(false);
const tableData = ref<any[]>([]);
const allRoles = ref<any[]>([]);
const selectedRoles = ref<number[]>([]);
const currentUser = ref<any>(null);

const searchForm = reactive({
  username: '',
  status: '',
});

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
});

const dialogVisible = ref(false);
const roleDialogVisible = ref(false);
const dialogTitle = ref('新增用户');
const formRef = ref();

const form = reactive({
  id: null as number | null,
  username: '',
  nickname: '',
  password: '',
  email: '',
  phone: '',
  status: '1',
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

const loadData = async () => {
  loading.value = true;
  try {
    const filters: any[] = [];
    if (searchForm.username) {
      filters.push({ field: 'username', op: 'like', value: searchForm.username });
    }
    if (searchForm.status) {
      filters.push({ field: 'status', op: 'eq', value: searchForm.status });
    }

    const result = await client.search('users', {
      filters,
      page: pagination.page - 1,
      size: pagination.pageSize,
    });
    tableData.value = result.content || [];
    pagination.total = result.totalElements || 0;
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  searchForm.username = '';
  searchForm.status = '';
  pagination.page = 1;
  loadData();
};

const handleAdd = () => {
  dialogTitle.value = '新增用户';
  Object.assign(form, {
    id: null,
    username: '',
    nickname: '',
    password: '',
    email: '',
    phone: '',
    status: '1',
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑用户';
  Object.assign(form, { ...row, password: '' });
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
      await client.update('users', form.id, form);
      ElMessage.success('更新成功');
    } else {
      await client.create('users', form);
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
  await ElMessageBox.confirm('确定删除该用户吗？', '提示', { type: 'warning' });
  try {
    await client.delete('users', row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败');
  }
};

const handleAssignRoles = async (row: any) => {
  currentUser.value = row;
  roleDialogVisible.value = true;
  await loadRoles();
  await loadUserRoles(row.username);
};

const loadRoles = async () => {
  rolesLoading.value = true;
  try {
    const result = await client.search('roles', { page: 0, size: 1000 });
    allRoles.value = result.content || [];
  } catch (error: any) {
    ElMessage.error('加载角色失败');
  } finally {
    rolesLoading.value = false;
  }
};

const loadUserRoles = async (username: string) => {
  try {
    const result = await client.search('user_roles', {
      filters: [{ field: 'userId', op: 'eq', value: username }],
      page: 0,
      size: 1000,
    });
    selectedRoles.value = (result.content || []).map((ur: any) => ur.role?.id).filter(Boolean);
  } catch (error: any) {
    ElMessage.error('加载用户角色失败');
  }
};

const handleSaveRoles = async () => {
  submitting.value = true;
  try {
    // 删除所有现有角色
    const existingResult = await client.search('user_roles', {
      filters: [{ field: 'userId', op: 'eq', value: currentUser.value.username }],
      page: 0,
      size: 1000,
    });
    const existing = existingResult.content || [];
    for (const ur of existing) {
      await client.delete('user_roles', ur.id as string | number);
    }

    // 添加新角色
    for (const roleId of selectedRoles.value) {
      await client.create('user_roles', {
        userId: currentUser.value.username,
        role: { id: roleId },
      });
    }

    ElMessage.success('角色分配成功');
    roleDialogVisible.value = false;
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
.user-list-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.search-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
