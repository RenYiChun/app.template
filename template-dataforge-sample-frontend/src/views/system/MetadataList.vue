<template>
  <div class="metadata-viewer">
    <div class="sidebar">
      <el-input v-model="filterText" placeholder="搜索实体..." clearable prefix-icon="Search" class="mb-2"/>
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="defaultProps"
        default-expand-all
        :filter-node-method="filterNode"
        @node-click="handleNodeClick"
        highlight-current
        node-key="id"
      />
    </div>
    <div class="content">
      <MetadataDetail v-if="currentEntity" :entity="currentEntity" />
      <div v-else class="empty-state">
        <el-empty description="请选择一个实体查看详情" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue';
import { useDataforge } from '@lrenyi/dataforge-headless/vue';
import type { EntityMeta } from '@lrenyi/dataforge-headless/core';
import { Search } from '@element-plus/icons-vue';
import MetadataDetail from './MetadataDetail.vue';

const { meta } = useDataforge();
const entities = ref<EntityMeta[]>([]);
const filterText = ref('');
const currentEntity = ref<EntityMeta | null>(null);
const treeRef = ref();

const defaultProps = {
  children: 'children',
  label: 'label',
};

// 构造树形数据：按服务分组
const treeData = computed(() => {
  const groups: Record<string, EntityMeta[]> = {};
  entities.value.forEach(e => {
    const serviceName = e.serviceName || 'default';
    if (!groups[serviceName]) groups[serviceName] = [];
    groups[serviceName].push(e);
  });

  return Object.entries(groups).map(([serviceName, list]) => ({
    id: `service-${serviceName}`,
    label: serviceName,
    children: list.map(e => ({
      id: `entity-${e.pathSegment}`,
      label: e.displayName || e.name,
      value: e
    }))
  }));
});

const filterNode = (value: string, data: any) => {
  if (!value) return true;
  return data.label.includes(value);
};

watch(filterText, (val) => {
  treeRef.value!.filter(val);
});

const handleNodeClick = (data: any) => {
  if (data.value) {
    currentEntity.value = data.value;
  }
};

onMounted(async () => {
  try {
    entities.value = await meta.getEntities();
  } catch (e) {
    console.error('Failed to load entities:', e);
  }
});
</script>

<style scoped>
.metadata-viewer {
  display: flex;
  height: 100%;
  border: 1px solid #dcdfe6;
  background-color: #fff;
}
.sidebar {
  width: 300px;
  border-right: 1px solid #dcdfe6;
  padding: 10px;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}
.mb-2 {
    margin-bottom: 8px;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 20px;
}
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
</style>
