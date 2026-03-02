<template>
  <div class="metadata-detail">
    <div class="header">
      <h2>{{ entity.displayName }} <small>({{ entity.entityName }})</small></h2>
      <el-tag type="info">{{ entity.serviceName || 'default' }}</el-tag>
      <div class="actions">
        <el-button type="primary" @click="exportJson">导出 JSON</el-button>
      </div>
    </div>

    <el-descriptions :column="2" border class="mb-4" title="基础信息">
      <el-descriptions-item label="路径片段">{{ entity.pathSegment }}</el-descriptions-item>
      <el-descriptions-item label="表名">{{ entity.tableName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="描述">{{ entity.description || '-' }}</el-descriptions-item>
      <el-descriptions-item label="主键类型">{{ entity.primaryKeyType || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="字段列表" name="fields">
        <el-table :data="entity.fields" border height="600" stripe>
          <el-table-column label="字段名" prop="name" show-overflow-tooltip width="150"/>
          <el-table-column label="显示名" prop="label" show-overflow-tooltip width="150"/>
          <el-table-column label="类型" prop="type" show-overflow-tooltip width="250"/>
          <el-table-column label="UI组件" prop="component" width="120"/>
          <el-table-column label="属性" width="300">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-if="row.primaryKey" size="small" type="danger">PK</el-tag>
                <el-tag v-if="row.required" size="small" type="warning">Required</el-tag>
                <el-tag v-if="row.searchable" size="small">Search</el-tag>
                <el-tag v-if="row.columnVisible" size="small" type="success">List</el-tag>
                <el-tag v-if="row.dictCode" size="small" type="info">Dict: {{ row.dictCode }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column label="描述" prop="description" show-overflow-tooltip/>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="功能开关" name="features">
        <el-form label-position="left" label-width="200px">
          <el-row>
            <el-col :span="8">
              <el-form-item label="CRUD Enabled">
                <el-tag :type="entity.crudEnabled ? 'success' : 'danger'">{{ entity.crudEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="List Enabled">
                <el-tag :type="entity.listEnabled ? 'success' : 'danger'">{{ entity.listEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Get Enabled">
                <el-tag :type="entity.getEnabled ? 'success' : 'danger'">{{ entity.getEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Create Enabled">
                <el-tag :type="entity.createEnabled ? 'success' : 'danger'">{{ entity.createEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Update Enabled">
                <el-tag :type="entity.updateEnabled ? 'success' : 'danger'">{{ entity.updateEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Delete Enabled">
                <el-tag :type="entity.deleteEnabled ? 'success' : 'danger'">{{ entity.deleteEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Export Enabled">
                <el-tag :type="entity.exportEnabled ? 'success' : 'danger'">{{ entity.exportEnabled }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Soft Delete">
                <el-tag :type="entity.softDelete ? 'success' : 'info'">{{ entity.softDelete }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Audit">
                <el-tag :type="entity.enableCreateAudit ? 'success' : 'info'">{{ entity.enableCreateAudit }}</el-tag>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="原始 JSON" name="json">
        <div class="json-container">
          <el-input
              v-model="jsonStr"
              :rows="25"
              readonly
              resize="none"
              type="textarea"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from 'vue';
import type {EntityMeta} from '@lrenyi/dataforge-headless/core';

const props = defineProps<{
  entity: EntityMeta
}>();

const activeTab = ref('fields');

const jsonStr = computed(() => JSON.stringify(props.entity, null, 2));

const exportJson = () => {
  const data = JSON.stringify(props.entity, null, 2);
  const blob = new Blob([data], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${props.entity.entityName}.json`;
  a.click();
  URL.revokeObjectURL(url);
};
</script>

<style scoped>
.metadata-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.actions {
  margin-left: auto;
}

.mb-4 {
  margin-bottom: 16px;
}

.json-container {
  height: 600px;
}
</style>
