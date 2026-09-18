/**
 * 登录态: JWT + 用户信息。token 持久化在 localStorage(后端无状态, 支持双实例轮询)。
 */
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { getToken, setToken, clearToken } from '@/api/request'

const USER_KEY = 'patrol_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.user?.realName || state.user?.username || ''
  },
  actions: {
    async login(username, password) {
      const data = await authApi.login(username, password)
      this.token = data.token
      this.user = data.user
      setToken(data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    logout() {
      this.token = ''
      this.user = null
      clearToken()
      localStorage.removeItem(USER_KEY)
    }
  }
})
