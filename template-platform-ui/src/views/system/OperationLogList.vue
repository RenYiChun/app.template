<template>
  <div class="operation-log-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>操作日志</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.userName" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="服务名称">
          <el-input v-model="searchForm.serviceName" placeholder="请输入服务名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.success" placeholder="请选择" clearable>
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
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userName" label="用户名" width="120" />
        <el-table-column prop="description" label="操作描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="serviceName" label="服务名称" width="120" />
        <el-table-column prop="requestMethod" label="请求方法" width="100" />
        <el-table-column prop="requestIp" label="IP地址" width="140" />
        <el-table-column prop="success" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executionTimeMs" label="执行时长(ms)" width="120" />
        <el-table-column prop="operationTime" label="操作时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.operationTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
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
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getPlatform } from '@lrenyi/template-platform-frontend/vue';

const { client } = getPlatform();

const loading = ref(false);
const tableData = ref<any[]>([]);
const detailDialogVisible = ref(false);
const currentLog = ref<any>(null);

const searchForm = reactive({
  userName: '',
  serviceName: '',
  success: null as boolean | null,
  dateRange: [] as string[],
});

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
});

const loadData = async () => {
  loading.value = true;
  try {
    const filters: any[] = [];
    
    if (searchForm.userName) {
      filters.push({ field: 'userName', op: 'like', value: searchForm.userName });
    }
    if (searchForm.serviceName) {
      filters.push({ field: 'serviceName', op: 'like', value: searchForm.serviceName });
    }
    if (searchForm.success !== null) {
      filters.push({ field: 'success', op: 'eq', value: searchForm.success });
    }
    if (searchForm.dateRange && searchForm.dateRange.length === 2) {
      filters.push({ field: 'operationTime', op: 'gte', value: searchForm.dateRange[0] });
      filters.push({ field: 'operationTime', op: 'lte', value: searchForm.dateRange[1] });
    }

    const result = await client.search('sys_operation_log', {
      filters,
      sort: [{ field: 'operationTime', dir: 'desc' }],
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

const handleSearch = () => {
  pagination.page = 1;
  loadData();
};

const handleReset = () => {
  Object.assign(searchForm, {
    userName: '',
    serviceName: '',
    success: null,
    dateRange: [],
  });
  handleSearch();
};

const handleViewDetail = (row: any) => {
  currentLog.value = row;
  detailDialogVisible.value = true;
};

const formatDate = (date: any) => {
  if (!date) return '';
  const d = new Date(date);
  return d.toLocaleString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.operation-log-container {
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

pre {
  margin: 0;
  padding: 10px;
  background-color: #f5f5f5;
  border-radius: 4px;
  font-size: 12px;
}
</style>
