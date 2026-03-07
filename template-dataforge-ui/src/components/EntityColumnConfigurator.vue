<script lang="ts" setup>
import {computed} from 'vue';
import {ElButton, ElCheckbox, ElCheckboxGroup, ElPopover} from 'element-plus';
import {Setting} from '@element-plus/icons-vue';
import type {ColumnConfig} from '@lrenyi/dataforge-headless/vue';

const props = withDefaults(
    defineProps<{
      allColumns: ColumnConfig[];
      displayColumns?: ColumnConfig[];
      visibleColumnProps: string[];
      setVisibleColumnProps?: (props: string[]) => void;
    }>(),
    {
      setVisibleColumnProps: () => {
      }
    }
);

const selectedProps = computed({
  get: () => props.visibleColumnProps,
  set: (val: string[]) => {
    props.setVisibleColumnProps(val);
  },
});
</script>

<template>
  <span class="entity-column-configurator">
    <ElPopover :width="200" placement="bottom" trigger="click">
      <template #reference>
        <ElButton :icon="Setting" circle title="列设置"/>
      </template>
      <div class="column-setting-popover">
        <div class="popover-title">列设置</div>
        <ElCheckboxGroup v-model="selectedProps" direction="vertical">
          <div v-for="col in allColumns" :key="col.prop" class="column-checkbox-item">
            <ElCheckbox :value="col.prop">
              {{ col.label || col.prop }}
            </ElCheckbox>
          </div>
        </ElCheckboxGroup>
      </div>
    </ElPopover>
  </span>
</template>

<style scoped>
.column-setting-popover .popover-title {
  font-weight: bold;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 8px;
}

.column-setting-popover .column-checkbox-item {
  margin-bottom: 4px;
}
</style>
