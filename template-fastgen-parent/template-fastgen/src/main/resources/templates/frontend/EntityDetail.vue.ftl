<!-- Generated from @Domain ${entity.simpleName}. -->
<template>
  <div class="${entity.simpleName?lower_case}-detail">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>${entity.displayName}详情</span>
          <div>
            <el-button type="primary" @click="handleEdit">编辑</el-button>
            <el-button @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
<#list entity.fields as field>
        <el-descriptions-item label="${field.label!field.name}">
<#if field.type == "Boolean">
          {{ detailData.${field.name} ? '是' : '否' }}
<#else>
          {{ detailData.${field.name} }}
</#if>
        </el-descriptions-item>
</#list>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { get } from '@/api/${entity.simpleName?lower_case}';
import type { ${entity.simpleName} } from '@/types/${entity.simpleName?lower_case}';

const router = useRouter();
const route = useRoute();

const detailData = ref<Partial<${entity.simpleName}>>({});

const loadData = async () => {
  const id = route.params.id as string;
  if (!id) {
    ElMessage.error('缺少ID参数');
    return;
  }
  
  try {
    const res = await get(Number(id));
    detailData.value = res.data;
  } catch (error) {
    ElMessage.error('加载数据失败');
  }
};

const handleEdit = () => {
  router.push(`/${entity.simpleName?lower_case}/edit/${'$'}{route.params.id}`);
};

const handleBack = () => {
  router.back();
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
</style>
