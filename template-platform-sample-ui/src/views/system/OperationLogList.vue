<template>
  <div class="operation-log-container">
    <EntityCrudPage
      ref="crudRef"
      entity="sys_operation_log"
      :columns="columns"
      :enable-create="false"
      :row-actions="['view']"
      @view="handleViewDetail"
    >
      <template #search="{ onSearch, onReset, onExport }">
        <el-form :model="searchForm" inline class="search-form">
          <el-form-item label="用户名">
            <el-input v-model="searchForm.userName" placeholder="请输入用户名" clearable @keyup.enter="onSearch(buildFilters())" />
          </el-form-item>
          <el-form-item label="服务名称">
            <el-input v-model="searchForm.serviceName" placeholder="请输入服务名称" clearable @keyup.enter="onSearch(buildFilters())" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.success" placeholder="请选择" clearable @change="onSearch(buildFilters())">
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作时间">
            <el-date-picker
              v-model="searchForm.dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              @change="onSearch(buildFilters())"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch(buildFilters())">查询</el-button>
            <el-button @click="handleReset(onReset)">重置</el-button>
            <el-button type="success" @click="onExport">导出</el-button>
          </el-form-item>
        </el-form>
      </template>

      <template #column-success="{ value }">
        <el-tag :type="value ? 'success' : 'danger'">
          {{ value ? '成功' : '失败' }}
        </el-tag>
      </template>
      <template #column-operationTime="{ value }">
        {{ formatDate(value) }}
      </template>

      <template #row-actions="{ row }">
        <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
      </template>
    </EntityCrudPage>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="操作日志详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ currentLog?.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ currentLog?.userName }}</el-descriptions-item>
        <el-descriptions-item label="服务名称">{{ currentLog?.serviceName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentLog?.success ? 'success' : 'danger'">
            {{ currentLog?.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">
          {{ currentLog?.description }}
        </el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog?.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="执行时长">{{ currentLog?.executionTimeMs }} ms</el-descriptions-item>
        <el-descriptions-item label="请求URI" :span="2">
          {{ currentLog?.requestUri }}
        </el-descriptions-item>
        <el-descriptions-item label="请求IP">{{ currentLog?.requestIp }}</el-descriptions-item>
        <el-descriptions-item label="服务器IP">{{ currentLog?.serverIp }}</el-descriptions-item>
        <el-descriptions-item label="操作时间" :span="2">
          {{ formatDate(currentLog?.operationTime) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.targetType" label="目标类型">
          {{ currentLog?.targetType }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.targetId" label="目标ID">
          {{ currentLog?.targetId }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.affectedCount !== null" label="影响数量" :span="2">
          {{ currentLog?.affectedCount }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.reason" label="原因" :span="2">
          {{ currentLog?.reason }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.exceptionDetails" label="异常详情" :span="2">
          <pre style="max-height: 200px; overflow: auto; color: red;">{{ currentLog?.exceptionDetails }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.extra" label="额外信息" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ currentLog?.extra }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import dayjs from 'dayjs';
import { EntityCrudPage, type ColumnConfig, type FilterCondition } from '@lrenyi/platform-headless/vue';

const formatDate = (val: string | number | Date) => {
  if (!val) return '';
  return dayjs(val).format('YYYY-MM-DD HH:mm:ss');
};

const crudRef = ref();
const detailDialogVisible = ref(false);
const currentLog = ref<any>(null);

const searchForm = reactive({
  userName: '',
  serviceName: '',
  success: null as boolean | null,
  dateRange: [] as string[],
});

const columns: ColumnConfig[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'userName', label: '用户名', width: 120 },
  { prop: 'description', label: '操作描述', minWidth: 200, showOverflowTooltip: true },
  { prop: 'serviceName', label: '服务名称', width: 120 },
  { prop: 'requestMethod', label: '请求方法', width: 100 },
  { prop: 'requestIp', label: 'IP地址', width: 140 },
  { prop: 'success', label: '状态', width: 80 },
  { prop: 'executionTimeMs', label: '执行时长(ms)', width: 120 },
  { prop: 'operationTime', label: '操作时间', width: 180 },
];

const buildFilters = (): FilterCondition[] => {
  const filters: FilterCondition[] = [];
  if (searchForm.userName) {
    filters.push({ field: 'userName', op: 'like', value: searchForm.userName });
  }
  if (searchForm.serviceName) {
    filters.push({ field: 'serviceName', op: 'like', value: searchForm.serviceName });
  }
  if (searchForm.success !== null && searchForm.success !== undefined) {
    filters.push({ field: 'success', op: 'eq', value: searchForm.success });
  }
  if (searchForm.dateRange && searchForm.dateRange.length === 2) {
    filters.push({ field: 'operationTime', op: 'gte', value: searchForm.dateRange[0] });
    filters.push({ field: 'operationTime', op: 'lte', value: searchForm.dateRange[1] });
  }
  return filters;
};

const handleReset = (onReset: () => void) => {
  searchForm.userName = '';
  searchForm.serviceName = '';
  searchForm.success = null;
  searchForm.dateRange = [];
  onReset();
};

const handleViewDetail = (row: any) => {
  currentLog.value = row;
  detailDialogVisible.value = true;
};
</script>

<style scoped>
.operation-log-container {
  padding: 20px;
}
</style>
