<template>
  <div class="operation-log-auto-container">
    <!-- 
      通过 Config 和 Slot，让自动生成组件达到手写级的精细度
    -->
    <EntityCrudPage 
      entity="sys_operation_log" 
      :enable-create="false"
      :row-actions="['view']"
      :columns="columns"
      shadow="never"
      @view="handleView"
    >
      <!-- 自定义状态列 -->
      <template #column-success="{ value }">
        <el-tag :type="value ? 'success' : 'danger'">
          {{ value ? '成功' : '失败' }}
        </el-tag>
      </template>

      <!-- 自定义时间列 -->
      <template #column-operationTime="{ value }">
        {{ formatDate(value) }}
      </template>
    </EntityCrudPage>

    <!-- 详情对话框 (直接复用手写逻辑) -->
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
import { ref } from 'vue';
import { EntityCrudPage } from '@lrenyi/template-platform-frontend/vue';
import type { ColumnConfig } from '@lrenyi/template-platform-frontend/vue';
import dayjs from 'dayjs';

// 自定义列配置：控制宽度、标题、顺序
const columns: ColumnConfig[] = [
  { prop: 'id', width: 80, label: 'ID' },
  { prop: 'userName', width: 120, label: '用户名' },
  { prop: 'description', width: 200, label: '操作描述' }, // TODO: tooltip supported? Need to check EntityTable
  { prop: 'serviceName', width: 120, label: '服务名称' },
  { prop: 'requestMethod', width: 100, label: '请求方法' },
  { prop: 'requestIp', width: 140, label: 'IP地址' },
  { prop: 'success', width: 80, label: '状态' },
  { prop: 'executionTimeMs', width: 120, label: '执行时长(ms)' },
  { prop: 'operationTime', width: 180, label: '操作时间' },
];

const detailDialogVisible = ref(false);
const currentLog = ref<any>(null);

const handleView = (row: any) => {
  currentLog.value = row;
  detailDialogVisible.value = true;
};

const formatDate = (val: string | number | Date) => {
  if (!val) return '';
  return dayjs(val).format('YYYY-MM-DD HH:mm:ss');
};
</script>

<style scoped>
.operation-log-auto-container {
  /* padding: 20px; HomeView has global padding */
}
</style>
