<template>
  <div class="entity-metadata-page">
    <div class="metadata-sidebar">
      <div class="sidebar-header">
        <el-input
            v-model="filterText"
            :placeholder="t('dataforgeUi.search.inputPlaceholder', { label: t('menu.metadata', 'Entity') })"
            clearable
        >
          <template #prefix>
            <el-icon>
              <Search/>
            </el-icon>
          </template>
        </el-input>
      </div>
      <el-scrollbar>
        <el-menu
            :default-active="selectedEntity?.pathSegment"
            class="entity-menu"
            @select="handleSelect"
        >
          <el-menu-item
              v-for="entity in filteredEntities"
              :key="entity.pathSegment"
              :index="entity.pathSegment"
          >
            <div class="menu-item-content">
              <span class="entity-name">{{ entity.displayName }}</span>
              <span class="entity-path">{{ entity.pathSegment }}</span>
            </div>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </div>

    <div class="metadata-content">
      <div v-if="selectedEntity" class="entity-detail">
        <div class="detail-header">
          <h2>{{ selectedEntity.displayName }}</h2>
          <el-tag type="info">{{ selectedEntity.pathSegment }}</el-tag>
        </div>

        <el-tabs v-model="activeTab">
          <!-- Operations Tab -->
          <el-tab-pane :label="t('metadata.operations', 'Operations')" name="operations">
            <el-table :data="operationsList" border stripe style="width: 100%">
              <el-table-column :label="t('metadata.method', 'Method')" prop="method" width="100">
                <template #default="{ row }">
                  <el-tag :type="getMethodType(row.method)">{{ row.method }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('metadata.path', 'Path')" prop="path" show-overflow-tooltip/>
              <el-table-column :label="t('metadata.summary', 'Summary')" prop="summary" show-overflow-tooltip/>
              <el-table-column :label="t('metadata.permissions', 'Permissions')" prop="permissions">
                <template #default="{ row }">
                  <div v-if="row.permissions && row.permissions.length">
                    <el-tag v-for="perm in row.permissions" :key="perm" size="small"
                            style="margin-right: 4px; margin-bottom: 4px;">
                      {{ perm }}
                    </el-tag>
                  </div>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Queryable Fields Tab -->
          <el-tab-pane :label="t('metadata.fields', 'Queryable Fields')" name="fields">
            <el-table :data="queryableFieldsList" border stripe style="width: 100%">
              <el-table-column :label="t('metadata.fieldName', 'Field Name')" prop="name" width="180"/>
              <el-table-column :label="t('metadata.type', 'Type')" prop="type" width="120"/>
              <el-table-column :label="t('metadata.operators', 'Operators')">
                <template #default="{ row }">
                  <el-tag v-for="op in row.operators" :key="op" size="small" style="margin-right: 4px" type="info">
                    {{ op }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Schemas Tab -->
          <el-tab-pane :label="t('metadata.schemas', 'Schemas')" name="schemas">
            <el-tabs v-model="activeSchemaTab" type="card">
              <el-tab-pane label="Create" name="create">
                <div v-if="selectedEntity?.schemas?.create">
                  <el-table :data="getSchemaData(selectedEntity.schemas.create)" border stripe>
                    <el-table-column :label="t('metadata.propertyName', 'Property')" prop="name" width="180"/>
                    <el-table-column :label="t('metadata.type', 'Type')" prop="type" width="120"/>
                    <el-table-column :label="t('metadata.required', 'Required')" width="100">
                      <template #default="{ row }">
                        <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                          {{ row.required ? t('common.yes', 'Yes') : t('common.no', 'No') }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column :label="t('metadata.description', 'Description')" prop="description"/>
                  </el-table>
                </div>
                <el-empty v-else :description="t('common.noData', 'No Data')"/>
              </el-tab-pane>
              <el-tab-pane label="Update" name="update">
                <div v-if="selectedEntity?.schemas?.update">
                  <el-table :data="getSchemaData(selectedEntity.schemas.update)" border stripe>
                    <el-table-column :label="t('metadata.propertyName', 'Property')" prop="name" width="180"/>
                    <el-table-column :label="t('metadata.type', 'Type')" prop="type" width="120"/>
                    <el-table-column :label="t('metadata.required', 'Required')" width="100">
                      <template #default="{ row }">
                        <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                          {{ row.required ? t('common.yes', 'Yes') : t('common.no', 'No') }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column :label="t('metadata.description', 'Description')" prop="description"/>
                  </el-table>
                </div>
                <el-empty v-else :description="t('common.noData', 'No Data')"/>
              </el-tab-pane>
              <el-tab-pane label="Response" name="response">
                <div v-if="selectedEntity?.schemas?.pageResponse">
                  <el-table :data="getSchemaData(selectedEntity.schemas.pageResponse)" border stripe>
                    <el-table-column :label="t('metadata.propertyName', 'Property')" prop="name" width="180"/>
                    <el-table-column :label="t('metadata.type', 'Type')" prop="type" width="120"/>
                    <el-table-column :label="t('metadata.required', 'Required')" width="100">
                      <template #default="{ row }">
                        <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                          {{ row.required ? t('common.yes', 'Yes') : t('common.no', 'No') }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column :label="t('metadata.description', 'Description')" prop="description"/>
                  </el-table>
                </div>
                <el-empty v-else :description="t('common.noData', 'No Data')"/>
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>
        </el-tabs>
      </div>
      <el-empty v-else :description="t('metadata.selectEntity', 'Select an entity to view details')"/>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue';
import {Search} from '@element-plus/icons-vue';
import {useDataforge} from '@lrenyi/dataforge-headless/vue';
import type {EntityMeta} from '@lrenyi/dataforge-headless';

const props = defineProps<{
  locale?: Record<string, any>;
}>();

const {meta} = useDataforge();
const entities = ref<EntityMeta[]>([]);
const selectedEntity = ref<EntityMeta | null>(null);
const filterText = ref('');
const activeTab = ref('operations');
const activeSchemaTab = ref('response');

// Helper to translate text using provided locale or fallback
const t = (key: string, fallback: string | Record<string, any>) => {
  if (typeof fallback === 'object') {
    // Handle template replacement if fallback is an object (simplified logic here, actually fallback is string usually)
    // If the second arg is params, the first arg is key
    return key;
  }

  // Simplified i18n lookup
  if (!props.locale) return fallback;

  const keys = key.split('.');
  let value: any = props.locale;

  for (const k of keys) {
    if (value && typeof value === 'object' && k in value) {
      value = value[k];
    } else {
      return fallback;
    }
  }

  return value || fallback;
};

// Filter entities based on search text
const filteredEntities = computed(() => {
  if (!filterText.value) return entities.value;
  const lower = filterText.value.toLowerCase();
  return entities.value.filter(
      (e) =>
          (e.displayName && e.displayName.toLowerCase().includes(lower)) ||
          (e.pathSegment && e.pathSegment.toLowerCase().includes(lower))
  );
});

// Convert operations object to list for table
const operationsList = computed(() => {
  if (!selectedEntity.value) return [];
  return Object.values(selectedEntity.value?.operations || {});
});

// Convert queryable fields object to list for table
const queryableFieldsList = computed(() => {
  if (!selectedEntity.value?.queryableFields) return [];
  return Object.entries(selectedEntity.value.queryableFields).map(([name, conf]) => ({
    name,
    ...conf,
  }));
});

const handleSelect = (pathSegment: string) => {
  selectedEntity.value = entities.value.find((e) => e.pathSegment === pathSegment) || null;
};

const getMethodType = (method: string) => {
  switch (method.toUpperCase()) {
    case 'GET':
      return '';
    case 'POST':
      return 'success';
    case 'PUT':
      return 'warning';
    case 'DELETE':
      return 'danger';
    default:
      return 'info';
  }
};

const getSchemaData = (schema: Record<string, any>) => {
  return Object.entries(schema).map(([name, conf]) => ({
    name,
    ...conf
  }));
};

onMounted(async () => {
  try {
    const list = await meta.getEntities();
    // Sort by pathSegment
    entities.value = list.sort((a, b) => a.pathSegment.localeCompare(b.pathSegment));

    if (entities.value.length > 0) {
      selectedEntity.value = entities.value[0];
    }
  } catch (err) {
    console.error('Failed to fetch entities', err);
  }
});
</script>

<style scoped>
.entity-metadata-page {
  height: 100%;
  background-color: var(--el-bg-color);
  display: flex;
  overflow: hidden;
}

.metadata-sidebar {
  width: 300px;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  background-color: var(--el-bg-color-overlay);
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.entity-menu {
  border-right: none;
  background-color: transparent;
}

.menu-item-content {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
  padding: 8px 0;
}

.entity-name {
  font-weight: 500;
  font-size: 14px;
}

.entity-path {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metadata-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background-color: var(--el-bg-color);
}

.detail-header {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-header h2 {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}

/* Override element-plus menu item height to accommodate two lines */
:deep(.el-menu-item) {
  height: auto !important;
  line-height: normal !important;
}
</style>
