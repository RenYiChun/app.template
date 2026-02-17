<template>
  <div class="home">
    <el-card>
      <template #header>
        <div class="header">
          <span>欢迎，{{ user?.username ?? '未知' }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
        </div>
      </template>
      <p>登录成功！当前用户：{{ user?.username }} (ID: {{ user?.id }})</p>
      <p v-if="meLoading">正在刷新用户信息...</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '@lrenyi/template-platform-frontend/vue';

const router = useRouter();
const { user, logout, refreshMe } = useAuth();
const meLoading = ref(false);

onMounted(() => {
  meLoading.value = true;
  refreshMe().finally(() => {
    meLoading.value = false;
  });
});

async function handleLogout() {
  await logout();
  router.push('/login');
}
</script>

<style scoped>
.home {
  min-height: 100vh;
  padding: 40px;
  background: #f5f5f5;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
