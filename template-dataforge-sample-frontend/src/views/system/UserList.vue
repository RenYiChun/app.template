<template>
  <div class="user-list-container">
    <EntityCrudPage
      entity="users"
      :columns="columns"
      :locale="dataforgeUiLocale"
      @create="handleAdd"
      @edit="handleEdit"
      @delete="handleDelete"
      @export="handleExport"
    >

      <!-- 自定义状态列 -->
      <template #column-status="{ value }">
        <el-tag :type="value === '1' ? 'success' : 'danger'">
          {{ value === '1' ? $t('common.enable') : $t('common.disable') }}
        </el-tag>
      </template>

      <!-- 自定义行操作 -->
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">{{ $t('common.edit') }}</el-button>
        <el-button link type="warning" @click="handleAssignRoles(row)">{{ $t('system.user.assignRoles') }}</el-button>
        <el-button link type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
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
        <el-form-item :label="$t('system.user.username')" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item :label="$t('system.user.nickname')" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item :label="$t('system.user.password')" prop="password" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item :label="$t('system.user.email')" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="$t('system.user.phone')" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item :label="$t('system.user.dept')" prop="departmentId">
          <el-cascader
            v-model="form.departmentId"
            :options="deptTreeData"
            :props="{ label: 'name', value: 'id', checkStrictly: true, emitPath: false }"
            :placeholder="$t('system.user.selectDept')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('system.role.remark')" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
        <el-form-item :label="$t('system.user.status')" prop="status">
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

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="$t('system.user.assignRoles')" width="500px">
      <el-checkbox-group v-model="selectedRoles" v-loading="rolesLoading">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveRoles" :loading="submitting">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useDataforge, BusinessError } from '@lrenyi/dataforge-headless/vue';
import { EntityCrudPage } from '@lrenyi/dataforge-ui';
import { useI18n } from 'vue-i18n';
import { useDataforgeUiLocale } from '@/i18n';

const { t } = useI18n();
const { client } = useDataforge();

const dataforgeUiLocale = useDataforgeUiLocale();
interface User {
  id: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  status: string;
  departmentId: number | null;
  remark: string;
  createTime: string;
  password?: string;
}

const userClient = client.define<User>('users');
const deptClient = client.define<any>('departments');
const roleClient = client.define<any>('roles');
const userRoleClient = client.define<any>('user_roles');

const crudRef = ref();
const submitting = ref(false);
const rolesLoading = ref(false);
const allRoles = ref<any[]>([]);
const selectedRoles = ref<number[]>([]);
const currentUser = ref<any>(null);
const deptTreeData = ref<any[]>([]);

const columns = computed(() => [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'username', label: t('system.user.username') },
  { prop: 'nickname', label: t('system.user.nickname') },
  { prop: 'email', label: t('system.user.email') },
  { prop: 'phone', label: t('system.user.phone') },
  { prop: 'status', label: t('system.user.status'), width: 80 },
  { prop: 'createTime', label: t('system.user.createTime'), width: 180 },
]);

const dialogVisible = ref(false);
const roleDialogVisible = ref(false);
const dialogTitle = ref(t('system.user.add'));
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
  username: [{ required: true, message: t('system.user.inputUsername'), trigger: 'blur' }],
  password: [{ required: true, message: t('system.user.inputPassword'), trigger: 'blur' }],
};

const handleAdd = () => {
  dialogTitle.value = t('system.user.add');
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
  ElMessage.info(t('common.operationFailed'));
};

const handleEdit = (row: any) => {
  dialogTitle.value = t('system.user.edit');
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
      await userClient.update(form.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await userClient.create(submitData);
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
  await ElMessageBox.confirm(t('system.user.deleteConfirm'), t('common.tips'), { type: 'warning' });
  try {
    await userClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    crudRef.value?.refresh();
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
  }
};

const loadDeptTree = async () => {
  try {
    const result = await deptClient.search({ page: 0, size: 1000 });
    const list = result.content || [];
    deptTreeData.value = buildTree(list);
  } catch (error: any) {
    console.error(t('system.user.loadDeptTreeFailed'), error);
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
    const result = await roleClient.search({ page: 0, size: 1000 });
    allRoles.value = result.content || [];
  } catch {
    ElMessage.error(t('system.user.loadRolesFailed'));
  } finally {
    rolesLoading.value = false;
  }
};

const loadUserRoles = async (username: string) => {
  rolesLoading.value = true;
  try {
    const result = await userRoleClient.search({
      filters: [{ field: 'userId', op: 'eq', value: username }],
      page: 0,
      size: 1000,
    });
    selectedRoles.value = (result.content || []).map((ur: any) => ur.role?.id).filter(Boolean);
  } catch {
    ElMessage.error(t('system.user.loadUserRolesFailed'));
  } finally {
    rolesLoading.value = false;
  }
};

const handleSaveRoles = async () => {
  submitting.value = true;
  try {
    // 删除所有现有角色
    const existingResult = await userRoleClient.search({
      filters: [{ field: 'userId', op: 'eq', value: currentUser.value.username }],
      page: 0,
      size: 1000,
    });
    const existing = existingResult.content || [];
    for (const ur of existing) {
      await userRoleClient.delete(ur.id as string | number);
    }

    // 添加新角色
    for (const roleId of selectedRoles.value) {
      await userRoleClient.create({
        userId: currentUser.value.username,
        role: { id: roleId },
      });
    }

    ElMessage.success(t('system.user.assignRolesSuccess'));
    roleDialogVisible.value = false;
  } catch (error: any) {
    ElMessage.error(error.message || t('common.operationFailed'));
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.user-list-container {
  padding: 0;
}
</style>
