<template>
  <div class="user-list-container">
    <EntityCrudPage
      entity="users"
      :columns="columns"
      :search-fields="['username', 'status']"
      @create="handleAdd"
      @edit="handleEdit"
      @delete="handleDelete"
      @export="handleExport"
    >
      <!-- 自定义搜索栏插槽 (可选，如果自动生成的满足要求则不需要) -->
      <!-- 这里为了演示 status 的下拉选择，我们可以使用 slot，或者依赖元数据 -->
      <!-- 假设元数据中 status 是 dict 或 enum，EntitySearchBar 会自动生成下拉。 -->
      <!-- 如果不是，我们这里先用 slot 还原之前的搜索体验 -->
      <template #search="{ onSearch, onReset, onExport }">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="用户名">
            <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable @keyup.enter="onSearch(buildFilters())" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable @change="onSearch(buildFilters())">
              <el-option label="正常" value="1" />
              <el-option label="停用" value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch(buildFilters())">查询</el-button>
            <el-button @click="handleReset(onReset)">重置</el-button>
            <el-button type="success" @click="onExport">导出</el-button>
          </el-form-item>
        </el-form>
      </template>

      <!-- 自定义状态列 -->
      <template #column-status="{ value }">
        <el-tag :type="value === '1' ? 'success' : 'danger'">
          {{ value === '1' ? '正常' : '停用' }}
        </el-tag>
      </template>

      <!-- 自定义行操作 -->
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="warning" @click="handleAssignRoles(row)">分配角色</el-button>
        <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
    </EntityCrudPage>

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
        <el-form-item label="部门" prop="departmentId">
          <el-cascader
            v-model="form.departmentId"
            :options="deptTreeData"
            :props="{ label: 'name', value: 'id', checkStrictly: true, emitPath: false }"
            placeholder="请选择部门"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
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
import { ref, reactive } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { usePlatform, EntityCrudPage } from '@lrenyi/platform-headless/vue';

const { client } = usePlatform();

const crudRef = ref();
const submitting = ref(false);
const rolesLoading = ref(false);
const allRoles = ref<any[]>([]);
const selectedRoles = ref<number[]>([]);
const currentUser = ref<any>(null);
const deptTreeData = ref<any[]>([]);

const searchForm = reactive({
  username: '',
  status: '',
});

const columns = ref([
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'username', label: '用户名' },
  { prop: 'nickname', label: '昵称' },
  { prop: 'email', label: '邮箱' },
  { prop: 'phone', label: '手机号' },
  { prop: 'status', label: '状态', width: 80 },
  { prop: 'createTime', label: '创建时间', width: 180 },
]);

const buildFilters = () => {
  const filters: any[] = [];
  if (searchForm.username) {
    filters.push({ field: 'username', op: 'like', value: searchForm.username });
  }
  if (searchForm.status) {
    filters.push({ field: 'status', op: 'eq', value: searchForm.status });
  }
  return filters;
};

const handleReset = (resetFn: () => void) => {
  searchForm.username = '';
  searchForm.status = '';
  resetFn();
};

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
  departmentId: null as number | null,
  remark: '',
  status: '1',
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
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
    departmentId: null,
    remark: '',
    status: '1',
  });
  dialogVisible.value = true;
  loadDeptTree();
};

const handleExport = () => {
  ElMessage.info('导出功能开发中');
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑用户';
  Object.assign(form, { ...row, password: '' });
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
      if (!submitData.password) delete (submitData as any).password;
      await client.update('users', form.id, submitData);
      ElMessage.success('更新成功');
    } else {
      delete (submitData as any).id;
      await client.create('users', submitData);
      ElMessage.success('创建成功');
    }
    dialogVisible.value = false;
    crudRef.value?.refresh();
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
    crudRef.value?.refresh();
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败');
  }
};

const loadDeptTree = async () => {
  try {
    const result = await client.search('departments', { page: 0, size: 1000 });
    const list = result.content || [];
    deptTreeData.value = buildTree(list);
  } catch (error: any) {
    console.error('加载部门树失败', error);
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
