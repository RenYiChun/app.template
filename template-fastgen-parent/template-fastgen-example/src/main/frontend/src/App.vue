<template>
  <el-config-provider :locale="zhCn">
    <div id="app">
      <!-- 登录页等独立页：不显示侧栏与顶栏 -->
      <template v-if="route.path === '/login'">
        <router-view />
      </template>
      <el-container v-else class="layout-container">
        <el-aside width="200px" class="layout-aside">
          <div class="logo">🚀 Fastgen</div>
          <el-menu
            :default-active="activeMenu"
            router
            class="sidebar-menu"
            background-color="#304156"
            text-color="#bfcbd9"
            active-text-color="#409EFF"
          >
            <el-menu-item index="/">
              <el-icon><HomeFilled /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/user">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/login">
              <el-icon><Key /></el-icon>
              <span>登录</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        <el-container direction="vertical" class="layout-main">
          <el-header class="layout-header">
            <span class="header-title">全栈示例</span>
            <div class="header-right">
              <el-link type="primary" :href="'#/login'" :underline="false">去登录</el-link>
            </div>
          </el-header>
          <el-main class="layout-content">
            <router-view />
          </el-main>
        </el-container>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { HomeFilled, User, Key } from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  height: 100vh;
}

.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #304156;
  overflow-x: hidden;
}

.logo {
  height: 50px;
  line-height: 50px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #1f2d3d;
}

.sidebar-menu {
  border-right: none;
}

.sidebar-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
}

.layout-main {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.layout-header {
  height: 50px;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-title {
  font-size: 16px;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
}

.layout-content {
  flex: 1;
  overflow: auto;
  background: #f0f2f5;
  padding: 20px;
}
</style>
