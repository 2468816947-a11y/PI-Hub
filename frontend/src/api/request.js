/**
 * axios 统一封装(接口文档 附录 C):
 * - 请求拦截器: 自动携带 Authorization: Bearer <token>
 * - 响应拦截器: 以信封 code 为准(不看 HTTP 状态); code!==0 统一提示并 reject
 * - 401: 清除 token 跳转登录页; 429: 提示操作频繁, 禁止自动重试
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'patrol_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截: 附加 JWT
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 业务层自定义错误
export class BizError extends Error {
  constructor(code, msg) {
    super(msg)
    this.code = code
  }
}

let redirecting = false

function handleUnauthorized() {
  clearToken()
  if (redirecting) return
  redirecting = true
  ElMessage.warning('登录已过期, 请重新登录')
  // 用 location 跳转, 避免与 router 循环依赖
  const redirect = encodeURIComponent(window.location.pathname + window.location.search)
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = `/login?redirect=${redirect}`
  }
  setTimeout(() => {
    redirecting = false
  }, 1500)
}

// 响应拦截: 统一解包 { code, msg, data }
request.interceptors.response.use(
  (response) => {
    // blob 等二进制响应直接返回(文件下载)
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data
    // 非信封结构(理论上不会出现), 原样返回
    if (body === null || typeof body !== 'object' || body.code === undefined) {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    // 业务失败
    if (body.code === 401) {
      handleUnauthorized()
    } else if (body.code === 429) {
      ElMessage.error('操作过于频繁, 请稍后再试')
    } else {
      ElMessage.error(body.msg || '请求失败')
    }
    return Promise.reject(new BizError(body.code, body.msg))
  },
  (error) => {
    // HTTP 层错误(Nginx 502 / 超时 / 断网等)
    const status = error.response?.status
    const respBody = error.response?.data
    // 后端异常时仍可能返回信封 JSON
    if (respBody && typeof respBody === 'object' && respBody.code !== undefined) {
      if (respBody.code === 401) {
        handleUnauthorized()
      } else if (respBody.code === 429) {
        ElMessage.error('操作过于频繁, 请稍后再试')
      } else {
        ElMessage.error(respBody.msg || '请求失败')
      }
      return Promise.reject(new BizError(respBody.code, respBody.msg))
    }
    if (status === 401) {
      handleUnauthorized()
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时, 请稍后重试')
    } else if (status >= 500) {
      ElMessage.error('网关或后端服务暂不可用, 请确认容器已启动')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
