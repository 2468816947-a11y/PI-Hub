import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/request'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '状态大屏', icon: 'Monitor' }
      },
      {
        path: 'devices',
        name: 'devices',
        component: () => import('@/views/Devices.vue'),
        meta: { title: '设备台账', icon: 'Cpu' }
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('@/views/Tasks.vue'),
        meta: { title: '巡检任务', icon: 'List' }
      },
      {
        path: 'alarms',
        name: 'alarms',
        component: () => import('@/views/Alarms.vue'),
        meta: { title: '告警中心', icon: 'Bell' }
      },
      {
        path: 'events',
        name: 'events',
        component: () => import('@/views/Events.vue'),
        meta: { title: '事件检索', icon: 'Search' }
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/Reports.vue'),
        meta: { title: '巡检报告', icon: 'Document' }
      },
      {
        path: 'files',
        name: 'files',
        component: () => import('@/views/Files.vue'),
        meta: { title: '影像文件', icon: 'Picture' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫: 无 token 一律跳登录页
router.beforeEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 电力场站巡检平台`
    : '电力场站设备巡检数据集成平台'
  if (!to.meta.public && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && getToken()) {
    return { path: '/dashboard' }
  }
  return true
})

export default router
