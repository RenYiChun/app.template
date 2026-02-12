<!-- Generated from @Domain ${entity.simpleName}. -->
<template>
  <div class="${entity.simpleName?lower_case}-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>${entity.displayName}管理</span>
          <el-button type="primary" @click="handleCreate">新增</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
<#list entity.fields as field>
<#if field.listable || field.id>
        <el-table-column prop="${field.name}" label="${field.label!field.name}" />
</#if>
</#list>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="handleView(scope.row)">查看</el-button>
            <el-button size="small" type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { list, remove } from '@/api/${entity.simpleName?lower_case}';
import type { ${entity.simpleName} } from '@/types/${entity.simpleName?lower_case}';

const router = useRouter();

const searchForm = ref({
  keyword: ''
});

const tableData = ref<${entity.simpleName}[]>([]);

const pagination = ref({
  page: 1,
  size: 10,
  total: 0
});

const loadData = async () => {
  try {
    const res = await list(pagination.value.page, pagination.value.size);
    tableData.value = res.data;
    // pagination.value.total = res.total; // 需要后端返回 total
  } catch (error) {
    ElMessage.error('加载数据失败');
  }
};

const handleSearch = () => {
  pagination.value.page = 1;
  loadData();
};

const handleReset = () => {
  searchForm.value.keyword = '';
  loadData();
};

const handleCreate = () => {
  router.push('/${entity.simpleName?lower_case}/create');
};

const handleView = (row: ${entity.simpleName}) => {
  router.push(`/${entity.simpleName?lower_case}/${'$'}{row.id}`);
};

const handleEdit = (row: ${entity.simpleName}) => {
  router.push(`/${entity.simpleName?lower_case}/edit/${'$'}{row.id}`);
};

const handleDelete = async (row: ${entity.simpleName}) => {
  try {
    await ElMessageBox.confirm('确认删除该${entity.displayName}吗?', '提示', {
      type: 'warning'
    });
    await remove(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const handlePageChange = () => {
  loadData();
};

const handleSizeChange = () => {
  pagination.value.page = 1;
  loadData();
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
