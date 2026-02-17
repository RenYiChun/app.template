<template>
  <div class="app-layout">
    <!-- Sidebar Navigation -->
    <aside :class="['sidebar', { 'collapsed': isCollapsed }]">
      <!-- Logo Area -->
      <div class="sidebar-header">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <span class="logo-text" v-show="!isCollapsed">Platform</span>
      </div>

      <!-- Menu Items -->
      <div class="sidebar-menu">
        <el-scrollbar>
          <el-menu
            :default-active="activeMenu"
            class="el-menu-vertical"
            :collapse="isCollapsed"
            background-color="transparent"
            text-color="#94a3b8"
            active-text-color="#ffffff"
            :collapse-transition="false"
            router
          >
            <el-menu-item index="1" @click="$router.push('/')">
              <el-icon><DataBoard /></el-icon>
              <template #title>控制台</template>
            </el-menu-item>
            
            <el-sub-menu index="2">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>系统管理</span>
              </template>
              <el-menu-item index="/system/users">用户管理</el-menu-item>
              <el-menu-item index="/system/roles">角色管理</el-menu-item>
              <el-menu-item index="/system/departments">部门管理</el-menu-item>
              <el-menu-item index="/system/dicts">字典管理</el-menu-item>
              <el-menu-item index="/system/operation-logs">操作日志</el-menu-item>
            </el-sub-menu>

            <el-menu-item index="3" @click="$router.push('/docs')">
              <el-icon><Document /></el-icon>
              <template #title>文档</template>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </div>
      
      <!-- Bottom Action (optional) -->
      <div class="sidebar-footer" v-show="!isCollapsed">
          <div class="version-tag">v1.0.0</div>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main class="main-content">
      <!-- Top Header -->
      <header class="app-header">
        <div class="header-left">
          <button class="toggle-btn" @click="toggleSidebar">
            <el-icon :size="20">
              <Expand v-if="isCollapsed" />
              <Fold v-else />
            </el-icon>
          </button>
          
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/' }">Home</el-breadcrumb-item>
            <el-breadcrumb-item>Dashboard</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- Notification Icon -->
          <div class="icon-btn">
            <el-badge is-dot class="item">
              <el-icon :size="20"><Bell /></el-icon>
            </el-badge>
          </div>
          
          <!-- User Profile Dropdown -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-profile">
              <el-avatar :size="32" class="user-avatar">{{ userInitial }}</el-avatar>
              <span class="username">{{ user?.username || 'Admin' }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="user-dropdown">
                <div class="dropdown-header">
                  <p class="dh-name">{{ user?.username || 'Administrator' }}</p>
                  <p class="dh-email">admin@platform.com</p>
                </div>
                <el-dropdown-item divided command="profile">
                    <el-icon><User /></el-icon> Profile
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                    <el-icon><Setting /></el-icon> Account Settings
                </el-dropdown-item>
                <el-dropdown-item divided command="logout" class="danger-item">
                    <el-icon><SwitchButton /></el-icon> Log Out
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- Dashboard Content View -->
      <div class="content-wrapper">
        <router-view></router-view>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '@lrenyi/template-platform-frontend/vue';
import { 
  DataBoard, User, Setting, Document, 
  Expand, Fold, Bell, ArrowDown, SwitchButton,
  UserFilled, Money, Files, WarningFilled
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

const { user, logout, refreshMe } = useAuth();
const router = useRouter();

const isCollapsed = ref(false);
const activeMenu = ref('1');

const userInitial = computed(() => {
    return (user.value?.username?.[0] || 'A').toUpperCase();
});

const toggleSidebar = () => {
    isCollapsed.value = !isCollapsed.value;
};

const handleCommand = async (command: string) => {
    if (command === 'logout') {
        await logout();
        router.push('/login');
        ElMessage.success('Logged out successfully');
    } else if (command === 'profile') {
        ElMessage.info('Profile clicked');
    }
};

onMounted(() => {
    // Ensure user data is fresh
    refreshMe();
});
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

.app-layout {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  background-color: #f1f5f9;
  font-family: 'Inter', sans-serif;
  overflow: hidden;
}

/* Sidebar Styles */
.sidebar {
  width: 260px;
  background: #0f172a; /* Slate 900 */
  color: #fff;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #1e293b;
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-header {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 12px;
  border-bottom: 1px solid #1e293b;
  overflow: hidden; /* Hide text when collapsed */
}

.sidebar.collapsed .sidebar-header {
    justify-content: center;
    padding: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  color: #6366f1;
  flex-shrink: 0;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  color: #f8fafc;
}

.sidebar-menu {
  flex: 1;
  padding-top: 16px;
  overflow: hidden;
}

.sidebar-footer {
    padding: 20px;
    border-top: 1px solid #1e293b;
    text-align: center;
}

.version-tag {
    font-size: 11px;
    color: #475569;
    background: #1e293b;
    padding: 2px 8px;
    border-radius: 10px;
    display: inline-block;
}

/* Menu Overrides */
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  margin: 4px 12px;
  border-radius: 8px;
  height: 48px;
  line-height: 48px;
}

:deep(.el-sub-menu__title) {
  margin: 4px 12px;
  border-radius: 8px;
  height: 48px;
  line-height: 48px;
}

.sidebar.collapsed :deep(.el-menu-item),
.sidebar.collapsed :deep(.el-sub-menu__title) {
    margin: 4px 8px; /* Tighter margins for collapsed state */
    padding: 0 !important;
    display: flex;
    justify-content: center;
}

:deep(.el-menu-item.is-active) {
  background-color: #6366f1 !important; /* Indigo 500 */
  color: #fff !important;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
}

:deep(.el-menu-item:hover) {
  background-color: #1e293b !important; /* Slate 800 */
}

/* Main Content Area */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f8fafc; /* Slate 50 */
}

/* Header */
.app-header {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.toggle-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #64748b;
  padding: 8px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.toggle-btn:hover {
  background: #f1f5f9;
  color: #334155;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.icon-btn {
  cursor: pointer;
  color: #64748b;
  transition: color 0.2s;
}
.icon-btn:hover {
  color: #334155;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.2s;
}

.user-profile:hover {
  background: #f1f5f9;
}

.user-avatar {
  background: #6366f1;
  font-weight: 600;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

/* Dropdown styling */
.dropdown-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 4px;
}
.dh-name {
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}
.dh-email {
  font-size: 12px;
  color: #94a3b8;
  margin: 0;
}
.danger-item {
    color: #ef4444 !important;
}

/* Content Area */
.content-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Dashboard Widgets */
.welcome-banner {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: 8px;
}
.welcome-banner h2 {
    font-size: 24px;
    font-weight: 700;
    color: #1e293b;
    margin: 0 0 8px 0;
}
.welcome-banner p {
    color: #64748b;
    margin: 0;
}

.stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 24px;
}

.stat-card {
    border: none;
    border-radius: 12px;
    transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

:deep(.el-card__body) {
    padding: 24px;
    display: flex;
    align-items: center;
    gap: 20px;
}

.stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
}

.icon-blue { background: #e0e7ff; color: #4f46e5; }
.icon-green { background: #dcfce7; color: #16a34a; }
.icon-purple { background: #f3e8ff; color: #9333ea; }
.icon-orange { background: #ffedd5; color: #ea580c; }

.stat-info {
    display: flex;
    flex-direction: column;
}

.stat-label {
    font-size: 13px;
    color: #64748b;
    font-weight: 500;
    margin-bottom: 4px;
}

.stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #1e293b;
    line-height: 1.2;
}

.stat-trend {
    font-size: 12px;
    margin-top: 4px;
    font-weight: 500;
    color: #94a3b8;
}
.trend-up { color: #16a34a; }
.trend-down { color: #ea580c; }

.content-card {
    border: none;
    border-radius: 12px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    color: #1e293b;
}

/* Transitions */
.sidebar, .main-content {
    transition: all 0.3s ease;
}
</style>
