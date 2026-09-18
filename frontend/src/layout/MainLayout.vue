<template>
  <el-container class="layout">
    <!-- ============ 侧边导航 ============ -->
    <el-aside :width="collapsed ? '64px' : '224px'" class="sidebar">
      <div class="logo-bar">
        <el-icon class="logo-icon"><Lightning /></el-icon>
        <span v-show="!collapsed" class="logo-text">空地协同巡检平台</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="side-menu"
        background-color="transparent"
        text-color="#b6c2d9"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
      <div v-show="!collapsed" class="sidebar-foot">电力场站·仿真集成环境</div>
    </el-aside>

    <el-container>
      <!-- ============ 顶栏 ============ -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <span class="page-title">{{ route.meta.title }}</span>
        </div>

        <div class="header-right">
          <!-- 健康检查: 展示当前命中的后端实例(Nginx 轮询) -->
          <el-tooltip placement="bottom" :show-after="200">
            <template #content>
              <span>每 5s 探活 /api/health, 连续观察实例名交替即说明 Nginx 双实例轮询生效</span>
            </template>
            <div class="health-chip" :class="{ down: !healthOk }">
              <span class="health-dot"></span>
              <template v-if="health">
                {{ health.instance }}
                <el-icon class="refresh-icon"><Refresh /></el-icon>
              </template>
              <span v-else-if="healthLoading">连接中…</span>
              <span v-else>后端不可达</span>
            </div>
          </el-tooltip>

          <el-dropdown @command="onCommand">
            <span class="user-box">
              <el-avatar :size="30" class="user-avatar">
                {{ auth.username.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="user-name">{{ auth.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  角色: {{ auth.user?.role || 'ADMIN' }}
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- ============ 内容区 ============ -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const collapsed = ref(false)
const menus = [
  { path: '/dashboard', title: '状态大屏', icon: 'Monitor' },
  { path: '/devices', title: '设备台账', icon: 'Cpu' },
  { path: '/tasks', title: '巡检任务', icon: 'List' },
  { path: '/alarms', title: '告警中心', icon: 'Bell' },
  { path: '/events', title: '事件检索', icon: 'Search' },
  { path: '/reports', title: '巡检报告', icon: 'Document' },
  { path: '/files', title: '影像文件', icon: 'Picture' }
]

// ---- 健康检查轮询(接口文档 §2.1.2, instance 字段验证 Nginx 轮询) ----
const health = ref(null)
const healthOk = ref(false)
const healthLoading = ref(false)
let timer = null

async function checkHealth() {
  healthLoading.value = true
  try {
    const data = await authApi.health()
    health.value = data
    healthOk.value = data.status === 'UP'
  } catch {
    health.value = null
    healthOk.value = false
  } finally {
    healthLoading.value = false
  }
}

onMounted(() => {
  checkHealth()
  timer = setInterval(checkHealth, 5000)
})
onBeforeUnmount(() => timer && clearInterval(timer))

async function onCommand(cmd) {
  if (cmd !== 'logout') return
  await ElMessageBox.confirm('确定退出登录吗?', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).catch(() => {})
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}

/* 侧边栏 */
.sidebar {
  background: var(--app-sidebar-bg);
  transition: width 0.2s;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.logo-bar {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  color: #fff;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-icon {
  font-size: 24px;
  color: #ffd34d;
  flex-shrink: 0;
}
.logo-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}
.side-menu {
  flex: 1;
  border-right: none;
  padding: 10px 8px;
}
.side-menu :deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 4px;
  height: 46px;
}
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #1d6cff, #1769ff);
  box-shadow: 0 4px 12px rgba(23, 105, 255, 0.35);
}
.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}
.sidebar-foot {
  padding: 14px 18px;
  font-size: 11px;
  color: #6b7896;
  white-space: nowrap;
}

/* 顶栏 */
.header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e8edf5;
  box-shadow: 0 1px 6px rgba(16, 27, 51, 0.04);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.collapse-btn {
  font-size: 20px;
  color: #5a6a85;
  cursor: pointer;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.health-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #2f7d4f;
  background: #ecf9f1;
  border: 1px solid #cdeed9;
  border-radius: 20px;
  padding: 4px 12px;
  cursor: default;
}
.health-chip.down {
  color: #c45656;
  background: #fef0f0;
  border-color: #fbc4c4;
}
.health-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2ecc71;
  box-shadow: 0 0 0 3px rgba(46, 204, 113, 0.18);
}
.health-chip.down .health-dot {
  background: #f56c6c;
  box-shadow: 0 0 0 3px rgba(245, 108, 108, 0.18);
}
.refresh-icon {
  font-size: 12px;
}
.user-box {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.user-avatar {
  background: linear-gradient(135deg, #1d6cff, #0e47b8);
  font-weight: 600;
}
.user-name {
  font-size: 14px;
  color: #2c3a52;
}

/* 内容区 */
.main {
  background: #f0f3f8;
  padding: 0;
  overflow-y: auto;
}
</style>
