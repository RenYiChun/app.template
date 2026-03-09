<template>
  <!-- 用户页布局由实体元数据 uiLayout 驱动：User 配置了 masterDetailTree，故默认呈现左部门树、右用户表 -->
  <div class="user-list-container">
    <EntityCrudPage
        :locale="dataforgeUiLocale"
        entity="users"
        @create="handleAdd"
        @delete="handleDelete"
        @edit="handleEdit"
        @export="handleExport"
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
            :row-actions-labels="[t('common.view'), t('common.edit'), t('system.user.assignRoles'), t('common.delete')]"
            @selection-change="handleSelectionChange"
        >
          <!-- 自定义状态列 -->
          <template #column-status="{ value }">
            <el-tag :type="value === '1' ? 'success' : 'danger'">
              {{ value === '1' ? $t('common.enable') : $t('common.disable') }}
            </el-tag>
          </template>

          <!-- 自定义行操作：详情、编辑、分配角色、删除 -->
          <template #row-actions="{ row }">
            <el-button link type="primary" @click="handleView(row)">{{ $t('common.view') }}</el-button>
            <el-button link type="primary" @click="handleEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="warning" @click="handleAssignRoles(row)">{{
                $t('system.user.assignRoles')
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

    <!-- 新增/编辑/查看对话框：查看时复用表单，只读展示 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="680px"
        class="elegant-dialog user-form-dialog"
        destroy-on-close
        @close="handleDialogClose"
    >
      <EntityForm
          ref="entityFormRef"
          :schema="formSchema"
          :initial="form"
          :label-width="80"
          :group-cols="entityMeta?.formGroupCols"
          :rules-override="formRulesOverride"
          :rules-mode="formRulesOverride ? 'replace' : undefined"
          :readonly="formDialogMode === 'view'"
          :loading="submitting"
          @submit="handleFormSubmit"
      >
        <!-- 部门选择插槽：只读时显示部门名 -->
        <template #field-departmentId="{ model, readonly }">
          <span v-if="readonly">{{ model.deptName ?? model.departmentId ?? '-' }}</span>
          <el-cascader
              v-else
              v-model="model.departmentId"
              :options="deptTreeData"
              :placeholder="$t('system.user.selectDept')"
              :props="{ label: 'name', value: 'id', checkStrictly: true, emitPath: false }"
              class="elegant-cascader"
              clearable
              style="width: 100%"
          />
        </template>

        <!-- 状态选择插槽 -->
        <template #field-status="{ model, readonly }">
          <div class="switch-container">
            <el-switch
                v-model="model.status"
                :active-value="'1'"
                :inactive-value="'0'"
                :active-text="$t('common.enable')"
                :inactive-text="$t('common.disable')"
                :disabled="readonly"
                inline-prompt
                class="modern-switch"
            />
          </div>
        </template>

        <!-- 底部按钮：查看时显示关闭+编辑，编辑/新增时显示取消+确定 -->
        <template #footer>
          <template v-if="formDialogMode === 'view'">
            <el-button @click="dialogVisible = false">{{ $t('common.close') }}</el-button>
            <el-button type="primary" @click="handleSwitchToEdit">{{ $t('common.edit') }}</el-button>
          </template>
          <template v-else>
            <el-button class="action-btn action-btn-cancel" @click="dialogVisible = false">
              {{ $t('common.cancel') }}
            </el-button>
            <el-button :loading="submitting" class="action-btn action-btn-submit" type="primary" @click="triggerSubmit">
              <el-icon class="btn-icon"><Check /></el-icon>
              {{ $t('common.confirm') }}
            </el-button>
          </template>
        </template>
      </EntityForm>
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
        <el-button :loading="submitting" type="primary" @click="handleSaveRoles">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {computed, reactive, ref} from 'vue';
import {Check} from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElIcon,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTag
} from 'element-plus';
import {BusinessError, useDataforge, useEntityMeta} from '@lrenyi/dataforge-headless/vue';
import {EntityCrudPage, EntityForm, EntitySearchBar, EntityTable, EntityToolbar} from '@lrenyi/dataforge-ui';
import {onMounted} from 'vue';
import {useI18n} from 'vue-i18n';
import {useDataforgeUiLocale} from '@/i18n';

const {t} = useI18n();
const dataforge = useDataforge();
const {client, meta} = dataforge;
const {meta: entityMeta, refresh: refreshMeta} = useEntityMeta(meta, 'users');

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

const submitting = ref(false);
const rolesLoading = ref(false);
const allRoles = ref<any[]>([]);
const selectedRoles = ref<number[]>([]);
const currentUser = ref<any>(null);
const deptTreeData = ref<any[]>([]);

const dialogVisible = ref(false);
const formDialogMode = ref<'view' | 'edit' | 'create'>('create');
const roleDialogVisible = ref(false);
const dialogTitle = ref(t('system.user.add'));
const entityFormRef = ref();

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

// 表单 schema：查看模式用后端 detail schema（含完整展示元数据），编辑/新增用 create/update
const formSchema = computed(() => {
  const meta = entityMeta.value;
  if (formDialogMode.value === 'view') {
    return meta?.schemas?.detail ?? meta?.schemas?.pageResponse ?? {};
  }
  const isCreate = !form.id;
  const schema = isCreate ? meta?.schemas?.create : meta?.schemas?.update;
  if (!schema) return {};

  const result = {...schema};
  // 编辑时密码改为可选，占位符提示
  if (!isCreate && result.password) {
    result.password = {...result.password, required: false, placeholder: '留空表示不修改密码'};
  }
  return result;
});

const formRulesOverride = computed(() => {
  const isCreate = !form.id;
  if (isCreate) return undefined;
  return {password: []};
});

onMounted(() => refreshMeta());

const handleAdd = () => {
  formDialogMode.value = 'create';
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

const handleView = (row: any) => {
  formDialogMode.value = 'view';
  dialogTitle.value = t('system.user.detail');
  Object.assign(form, {...row, password: ''});
  dialogVisible.value = true;
};

const handleSwitchToEdit = () => {
  formDialogMode.value = 'edit';
  dialogTitle.value = t('system.user.edit');
  loadDeptTree();
};

const handleEdit = (row: any) => {
  formDialogMode.value = 'edit';
  dialogTitle.value = t('system.user.edit');
  Object.assign(form, {...row, password: ''});
  dialogVisible.value = true;
  loadDeptTree();
};

const handleDialogClose = () => {
  entityFormRef.value?.handleReset();
};

const triggerSubmit = () => {
  entityFormRef.value?.handleSubmit();
};

const handleFormSubmit = async (data: any) => {
  submitting.value = true;
  try {
    const submitData = {...data};
    // Ensure ID is from original form if editing, though data should contain it if initial had it?
    // EntityForm initial sets formData. If form.id was in initial, it's in data.
    // However, form.id is outside formData in EntityForm (it copies keys from schema).
    // Wait, EntityForm only copies keys present in schema? No, it copies `initial`.
    // But let's use `form.id` to be safe as it's the source of truth for "edit mode".

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
    dataforge.refreshCrud('users');
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
  await ElMessageBox.confirm(t('system.user.deleteConfirm'), t('common.tips'), {type: 'warning'});
  try {
    await userClient.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    dataforge.refreshCrud('users');
  } catch (error: any) {
    if (error === 'cancel') return;
    ElMessage.error(error instanceof Error ? error.message : t('common.deleteFailed'));
  }
};

const loadDeptTree = async () => {
  try {
    const result = await deptClient.search({page: 0, size: 1000});
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
    map[item.id] = {...item, children: []};
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
    const result = await roleClient.search({page: 0, size: 1000});
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
      filters: [{field: 'userId', op: 'eq', value: username}],
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
      filters: [{field: 'userId', op: 'eq', value: currentUser.value.username}],
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
        role: {id: roleId},
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

/* 紧凑表单样式 */
.elegant-form {
  --form-gap: 12px;
  --label-color: #374151;
  --label-font-size: 12px;
  --input-bg: #f9fafb;
  --input-border: #e5e7eb;
  --input-hover-border: #3b82f6;
  --input-focus-bg: #ffffff;
  --transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 柔和的网格布局 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--form-gap);
}

/* 表单字段 */
.form-field {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.form-field.field-full {
  grid-column: 1 / -1;
  flex-direction: column;
  align-items: flex-start;
}

/* 标签样式 */
.field-label {
  font-size: var(--label-font-size);
  font-weight: 600;
  color: var(--label-color);
  display: flex;
  align-items: center;
  gap: 4px;
  letter-spacing: -0.01em;
  white-space: nowrap;
  min-width: 70px;
}

.required-mark {
  color: #ef4444;
  font-weight: 600;
}

/* 输入框包装器 */
.field-input-wrapper {
  width: 100%;
}

/* 优雅的输入框样式 */
:deep(.elegant-input .el-input__wrapper),
:deep(.elegant-cascader .el-input__wrapper) {
  background-color: var(--input-bg);
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--input-border) inset;
  transition: var(--transition);
  padding: 0 12px;
}

:deep(.elegant-input .el-input__inner) {
  height: 36px;
  font-size: 13px;
  color: #1f2937;
}

:deep(.elegant-input .el-input__wrapper:hover),
:deep(.elegant-cascader .el-input__wrapper:hover) {
  background-color: var(--input-focus-bg);
  box-shadow: 0 0 0 1px var(--input-hover-border) inset;
}

:deep(.elegant-input .el-input__wrapper.is-focus),
:deep(.elegant-cascader .el-input__wrapper.is-focus) {
  background-color: var(--input-focus-bg);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15), 0 0 0 1px var(--input-hover-border) inset;
}

/* Textarea 样式 */
:deep(.elegant-input.el-textarea .el-textarea__inner) {
  background-color: var(--input-bg);
  border-radius: 6px;
  padding: 8px 10px;
  min-height: 56px;
  resize: vertical;
  font-size: 13px;
  line-height: 1.5;
  transition: var(--transition);
}

:deep(.elegant-input.el-textarea .el-textarea__inner:hover) {
  background-color: var(--input-focus-bg);
}

:deep(.elegant-input.el-textarea .el-textarea__inner:focus) {
  background-color: var(--input-focus-bg);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

/* Switch 容器 */
.switch-container {
  display: flex;
  align-items: center;
}

:deep(.modern-switch .el-switch__core) {
  border-radius: 10px;
  background-color: #e5e7eb;
  height: 24px;
  transition: var(--transition);
}

:deep(.modern-switch .el-switch__core::after) {
  border-radius: 50%;
  background-color: #ffffff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

:deep(.modern-switch.is-checked .el-switch__core) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

:deep(.modern-switch .el-switch__label) {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

/* 底部操作区 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

/* 优雅的按钮样式 */
.action-btn {
  padding: 9px 24px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  transition: var(--transition);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}

.action-btn-cancel {
  background-color: #f3f4f6;
  color: #6b7280;
}

.action-btn-cancel:hover {
  background-color: #e5e7eb;
  color: #374151;
  transform: translateY(-1px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.action-btn-submit {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #ffffff;
  box-shadow: 0 4px 6px -1px rgba(102, 126, 234, 0.3);
}

.action-btn-submit:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 12px -2px rgba(102, 126, 234, 0.4);
}

.action-btn-submit:active {
  transform: translateY(0);
}

.btn-icon {
  font-size: 14px;
}

/* 紧凑对话框样式 */
.elegant-dialog {
  --dialog-border-radius: 8px;
  --dialog-shadow: 0 12px 20px -5px rgba(0, 0, 0, 0.08), 0 6px 8px -5px rgba(0, 0, 0, 0.03);
  --dialog-header-padding: 14px 20px 12px;
  --dialog-body-padding: 12px 20px;
  --dialog-footer-padding: 12px 20px 14px;
}

.user-form-dialog :deep(.el-dialog__body) {
  padding: 12px 20px 16px;
  max-height: 65vh;
  overflow-y: auto;
}

:deep(.elegant-dialog .el-dialog) {
  border-radius: var(--dialog-border-radius);
  box-shadow: var(--dialog-shadow);
}

:deep(.elegant-dialog .el-dialog__header) {
  padding: var(--dialog-header-padding);
  margin-right: 0;
  /* 移除标题下横线，避免与关闭按钮区域视觉重复 */
}

:deep(.elegant-dialog .el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

:deep(.elegant-dialog .el-dialog__body) {
  padding: var(--dialog-body-padding);
}

:deep(.elegant-dialog .el-dialog__footer) {
  padding: var(--dialog-footer-padding);
  border-top: 1px solid #f3f4f6;
}

:deep(.elegant-dialog .el-dialog__headerbtn) {
  top: 14px;
  right: 16px;
}

:deep(.elegant-dialog .el-dialog__headerbtn .el-dialog__close) {
  font-size: 18px;
  color: #9ca3af;
}

:deep(.elegant-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: #6b7280;
}

/* 响应式 */
@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
  }

  /* 移动端对话框调整 */
  :deep(.elegant-dialog .el-dialog) {
    width: 90vw !important;
    max-width: 90vw;
    margin: 0 auto;
  }

  :deep(.elegant-dialog .el-dialog__header),
  :deep(.elegant-dialog .el-dialog__body),
  :deep(.elegant-dialog .el-dialog__footer) {
    padding-left: 16px;
    padding-right: 16px;
  }

  :deep(.elegant-dialog .el-dialog__headerbtn) {
    right: 16px;
  }
}
</style>
