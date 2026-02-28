<template>
  <div class="role-list-container">
    <EntityCrudPage
        :locale="dataforgeUiLocale"
        entity="roles"
        @create="handleAdd"
        @delete="handleDelete"
        @edit="handleEdit"
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
            :create-text="$t('common.add')"
            :display-columns="scope.displayColumns"
            :export-text="$t('common.export')"
            :selected-ids="scope.selectedIds"
            :set-visible-column-props="scope.setVisibleColumnProps"
            :show-search="scope.showSearch"
            :visible-column-props="scope.visibleColumnProps"
            @create="handleAdd"
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

      <template #table="{ items, loading, displayColumns, sort, selectable, handleSortChange, handleSelectionChange }">
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
            <el-button link type="primary" @click="handleEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="warning" @click="handleAssignPermissions(row)">{{
                $t('system.role.assignPerms')
              }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="500px"
        @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="$t('system.role.code')" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="!!form.id"/>
        </el-form-item>
        <el-form-item :label="$t('system.role.name')" prop="roleName">
          <el-input v-model="form.roleName"/>
        </el-form-item>
        <el-form-item :label="$t('system.role.remark')" prop="remark">
          <el-input v-model="form.remark" type="textarea"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="submitting" type="primary" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permDialogVisible" :title="$t('system.role.assignPerms')" width="600px">
      <el-tree
          ref="permTreeRef"
          v-loading="permsLoading"
          :data="permTreeData"
          :default-checked-keys="selectedPerms"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          show-checkbox
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="submitting" type="primary" @click="handleSavePerms">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {reactive, ref} from 'vue';
import {ElAlert, ElButton, ElInput, ElMessage, ElMessageBox, ElPagination} from 'element-plus';
import {BusinessError, useDataforge, useEntityCrud} from '@lrenyi/dataforge-headless/vue';
import {EntityCrudPage, EntitySearchBar, EntityTable, EntityToolbar} from '@lrenyi/dataforge-ui';
import {useI18n} from 'vue-i18n';
import {useDataforgeUiLocale} from '@/i18n';

const {t} = useI18n();
const {client} = useDataforge();

const dataforgeUiLocale = useDataforgeUiLocale();

interface Role {
  id: number;
  roleCode: string;
  roleName: string;
  remark: string;
  createTime: string;
}

const roleClient = client.define<Role>('roles');
const permClient = client.define<any>('permissions');
const rolePermClient = client.define<any>('role_permissions');

const {search} = useEntityCrud<Role>('roles');


const submitting = ref(false);
const permsLoading = ref(false);
const permTreeData = ref<any[]>([]);
const selectedPerms = ref<number[]>([]);
const currentRole = ref<any>(null);
const permTreeRef = ref();

const dialogVisible = ref(false);
const permDialogVisible = ref(false);
const dialogTitle = ref(t('system.role.add'));
const formRef = ref();

const form = reactive({
  id: null as number | null,
  roleCode: '',
  roleName: '',
  remark: '',
});

const rules = {
  roleCode: [{required: true, message: t('system.role.inputCode'), trigger: 'blur'}],
  roleName: [{required: true, message: t('system.role.inputName'), trigger: 'blur'}],
};

const handleAdd = () => {
  dialogTitle.value = t('system.role.add');
  Object.assign(form, {
    id: null,
    roleCode: '',
    roleName: '',
    remark: '',
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = t('system.role.edit');
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
    const submitData = {...form};
    if (form.id) {
      await roleClient.update(form.id, submitData);
      ElMessage.success(t('common.updateSuccess'));
    } else {
      delete (submitData as any).id;
      await roleClient.create(submitData);
      ElMessage.success(t('common.createSuccess'));
    }
    dialogVisible.value = false;
    search();
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
  await ElMessageBox.confirm(t('system.role.deleteConfirm'), t('common.tips'), {type: 'warning'});
  try {
    await roleClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    search();
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
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
    const result = await permClient.search({page: 0, size: 1000});
    const perms = result.content || [];
    // 简单平铺展示
    permTreeData.value = perms.map((p: any) => ({
      id: p.id,
      name: `${p.name} (${p.permission})`,
    }));
  } catch {
    ElMessage.error(t('system.role.loadPermsFailed'));
  } finally {
    permsLoading.value = false;
  }
};

const loadRolePermissions = async (roleId: number) => {
  try {
    const result = await rolePermClient.search({
      filters: [{field: 'role.id', op: 'eq', value: roleId}],
      page: 0,
      size: 1000,
    });
    selectedPerms.value = (result.content || []).map((rp: any) => rp.permission?.id).filter(Boolean);
  } catch {
    ElMessage.error(t('system.role.loadRolePermsFailed'));
  }
};

const handleSavePerms = async () => {
  submitting.value = true;
  try {
    const checkedKeys = permTreeRef.value.getCheckedKeys();

    // 删除所有现有权限
    const existingResult = await rolePermClient.search({
      filters: [{field: 'role.id', op: 'eq', value: currentRole.value.id}],
      page: 0,
      size: 1000,
    });
    const existing = existingResult.content || [];
    for (const rp of existing) {
      await rolePermClient.delete(rp.id as string | number);
    }

    // 添加新权限
    for (const permId of checkedKeys) {
      await rolePermClient.create({
        role: {id: currentRole.value.id},
        permission: {id: permId},
      });
    }

    ElMessage.success(t('system.role.assignPermsSuccess'));
    permDialogVisible.value = false;
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
</script>

<style scoped>
.role-list-container {
  padding: 0;
}
</style>
