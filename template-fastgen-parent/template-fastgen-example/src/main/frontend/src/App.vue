<template>
  <el-config-provider :locale="zhCn">
    <div id="app">
      <el-container>
        <el-header>
          <div class="header-content">
            <h1>🚀 Fastgen 全栈示例</h1>
            <el-menu mode="horizontal" :default-active="activeMenu" router>
              <el-menu-item index="/">首页</el-menu-item>
              <el-menu-item index="/user">用户管理</el-menu-item>
              <el-menu-item index="/login">登录页</el-menu-item>
            </el-menu>
          </div>
        </el-header>
        <el-main>
          <router-view />
        </el-main>
        <el-footer>
          <div class="footer-content">
            Powered by Fastgen - 从注解到全栈应用
          </div>
        </el-footer>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
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
}

.el-header {
  background: #545c64;
  color: #fff;
  padding: 0;
}

.header-content {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
}

.header-content h1 {
  margin: 0;
  font-size: 20px;
  margin-right: 40px;
}

.el-header .el-menu {
  background: transparent;
  border: none;
}

.el-header .el-menu-item {
  color: #fff !important;
  border-bottom: 2px solid transparent;
}

.el-header .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.1) !important;
}

.el-header .el-menu-item.is-active {
  border-bottom-color: #409EFF !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

.el-main {
  background: #f5f5f5;
  min-height: calc(100vh - 120px);
  padding: 20px;
}

.el-footer {
  background: #fff;
  border-top: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.footer-content {
  color: #909399;
  font-size: 14px;
}
</style>
