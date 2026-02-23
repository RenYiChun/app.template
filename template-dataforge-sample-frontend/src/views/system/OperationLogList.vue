<template>
  <div class="operation-log-auto-container">
    <EntityCrudPage
      entity="operation_log"
      :enable-create="false"
      :row-actions="['view']"
      :columns="columns"
      :locale="dataforgeUiLocale"
      @view="handleView"
    >
      <template #alert="{ error }">
        <el-alert v-if="error" :title="error.message" type="error" show-icon class="mb-4" />
      </template>

      <template #toolbar="scope">
          <EntityToolbar
            :selected-ids="scope.selectedIds"
            :can-create="false"
            :can-batch-delete="false"
            :can-export="true"
            :export-text="$t('common.export')"
            :show-search="scope.showSearch"
            :all-columns="scope.allColumns"
            :display-columns="scope.displayColumns"
            :visible-column-props="scope.visibleColumnProps"
            :set-visible-column-props="scope.setVisibleColumnProps"
            @export="scope.handleExport"
            @toggle-search="scope.toggleSearch"
            @refresh="scope.handleSearch"
          />
        </template>

        <template #search="{ filters, handleSearch, showSearch }">
          <EntitySearchBar
            v-if="showSearch"
            :filters="filters"
            :handle-search="handleSearch"
          />
        </template>

        <template #table="{ items, loading, displayColumns, sort, handleSortChange, handleSelectionChange }">
          <EntityTable
            :items="items"
            :loading="loading"
            :display-columns="displayColumns"
            :sort="sort"
            :handle-sort-change="handleSortChange"
            :handle-selection-change="handleSelectionChange"
          >
            <!-- 自定义状态列 -->
            <template #column-success="{ value }">
              <el-tag :type="value ? 'success' : 'danger'">
                {{ value ? $t('common.success') : $t('common.failed') }}
              </el-tag>
            </template>

            <!-- 自定义时间列 -->
            <template #column-operationTime="{ value }">
              {{ formatDate(value) }}
            </template>
          </EntityTable>
        </template>

        <template #pagination="{ total, page, size, handlePageChange, handleSizeChange }">
          <el-pagination
            class="mt-4"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :current-page="page"
            :page-size="size"
            :page-sizes="[10, 20, 50, 100]"
            @update:current-page="handlePageChange"
            @update:page-size="handleSizeChange"
          />
        </template>
      </EntityCrudPage>

    <!-- 详情对话框 (直接复用手写逻辑) -->
    <el-dialog v-model="detailDialogVisible" :title="$t('system.log.detailTitle')" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="$t('system.log.id')">{{ currentLog?.id }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.username')">{{ currentLog?.userName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.serviceName')">{{ currentLog?.serviceName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.status')">
          <el-tag :type="currentLog?.success ? 'success' : 'danger'">
            {{ currentLog?.success ? $t('common.success') : $t('common.failed') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.description')" :span="2">
          {{ currentLog?.description }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.requestMethod')">{{ currentLog?.requestMethod }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.executionTime')">{{ currentLog?.executionTimeMs }} ms</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.requestUri')" :span="2">
          {{ currentLog?.requestUri }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.requestIp')">{{ currentLog?.requestIp }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.serverIp')">{{ currentLog?.serverIp }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.log.operationTime')" :span="2">
          {{ formatDate(currentLog?.operationTime) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.targetType" :label="$t('system.log.targetType')">
          {{ currentLog?.targetType }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.targetId" :label="$t('system.log.targetId')">
          {{ currentLog?.targetId }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.affectedCount !== null" :label="$t('system.log.affectedCount')" :span="2">
          {{ currentLog?.affectedCount }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.reason" :label="$t('system.log.reason')" :span="2">
          {{ currentLog?.reason }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.exceptionDetails" :label="$t('system.log.exceptionDetails')" :span="2">
          <pre style="max-height: 200px; overflow: auto; color: red;">{{ currentLog?.exceptionDetails }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.extra" :label="$t('system.log.extra')" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ currentLog?.extra }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { EntityCrudPage } from '@lrenyi/dataforge-ui';
import type { ColumnConfig } from '@lrenyi/dataforge-ui';
import dayjs from 'dayjs';
import { useI18n } from 'vue-i18n';
import { useDataforgeUiLocale } from '@/i18n';

const { t } = useI18n();

const dataforgeUiLocale = useDataforgeUiLocale();

interface OperationLog {
  id: number;
  userName: string;
  serviceName: string;
  description: string;
  requestMethod: string;
  requestUri: string;
  requestIp: string;
  serverIp: string;
  success: boolean;
  reason?: string;
  exceptionDetails?: string;
  executionTimeMs: number;
  operationTime: string;
  targetType?: string;
  targetId?: string;
  affectedCount?: number;
  extra?: string;
}

const handleExport = async () => {
  try {
    // 导出逻辑需要根据实际后端API调整
    // const blob = await deptClient.exportExcel();
    // download(blob, 'departments.xlsx');
    ElMessage.success('导出功能待实现');
  } catch {
    ElMessage.error(t('system.dept.deleteFailed').replace('删除', '导出'));
  }
};

// 自定义列配置：控制宽度、标题、顺序
const columns = computed<ColumnConfig[]>(() => [
  { prop: 'id', width: 80, label: t('system.log.id') },
  { prop: 'userName', width: 120, label: t('system.log.username') },
  { prop: 'description', width: 200, label: t('system.log.description') }, // TODO: tooltip supported? Need to check EntityTable
  { prop: 'serviceName', width: 120, label: t('system.log.serviceName') },
  { prop: 'requestMethod', width: 100, label: t('system.log.requestMethod') },
  { prop: 'requestIp', width: 140, label: t('system.log.requestIp') },
  { prop: 'success', width: 80, label: t('system.log.status') },
  { prop: 'executionTimeMs', width: 120, label: t('system.log.executionTime') },
  { prop: 'operationTime', width: 180, label: t('system.log.operationTime') },
]);

const detailDialogVisible = ref(false);
const currentLog = ref<OperationLog | null>(null);

const handleView = (row: Record<string, unknown>) => {
  currentLog.value = row as unknown as OperationLog;
  detailDialogVisible.value = true;
};

const formatDate = (val: string | number | Date | undefined) => {
  if (!val) return '';
  return dayjs(val).format('YYYY-MM-DD HH:mm:ss');
};
</script>

<style scoped>
.operation-log-list-container {
  /* padding: 20px; HomeView has global padding */
}
</style>
