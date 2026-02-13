<template>
  <div class="dashboard">
    <div class="welcome">
      <h1>工作台</h1>
      <p class="desc">欢迎使用 Fastgen 全栈示例，从注解到 CRUD 与页面一键生成。</p>
    </div>

    <el-row :gutter="20" class="cards">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card" @click="$router.push('/user')">
          <div class="entry-icon user">
            <el-icon :size="32"><User /></el-icon>
          </div>
          <div class="entry-title">用户管理</div>
          <div class="entry-desc">查看、新增、编辑用户，由 @Domain User 自动生成</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card" @click="$router.push('/login')">
          <div class="entry-icon login">
            <el-icon :size="32"><Key /></el-icon>
          </div>
          <div class="entry-title">登录</div>
          <div class="entry-desc">登录页由 @Page LoginPage 生成，提交后跳转主页</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card entry-card-api">
          <div class="entry-icon api">
            <el-icon :size="32"><Link /></el-icon>
          </div>
          <div class="entry-title">API</div>
          <div class="entry-desc">REST 接口 /api/user、/api/auth/login 等已自动生成</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="intro-card" shadow="never">
      <template #header>
        <span>框架说明</span>
      </template>
      <el-alert title="Fastgen 通过 @Domain 和 @Page 注解自动生成：Spring Boot REST API、Vue 3 页面、TypeScript 类型与 API。" type="info" :closable="false" show-icon />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { User, Key, Link } from '@element-plus/icons-vue'
</script>

<style scoped>
.dashboard {
  max-width: 1000px;
  margin: 0 auto;
}

.welcome {
  margin-bottom: 24px;
}

.welcome h1 {
  font-size: 22px;
  color: #303133;
  margin: 0 0 8px 0;
}

.welcome .desc {
  color: #606266;
  font-size: 14px;
  margin: 0;
}

.cards {
  margin-bottom: 24px;
}

.entry-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.entry-card:hover {
  transform: translateY(-2px);
}

.entry-card-api {
  cursor: default;
}

.entry-card-api:hover {
  transform: none;
}

.entry-icon {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: #fff;
}

.entry-icon.user {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.entry-icon.login {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.entry-icon.api {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.entry-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.entry-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.intro-card {
  margin-top: 8px;
}
</style>
