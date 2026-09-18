/**
 * 接口集合 —— 与《04-接口文档.md》v1.2 的 19 个接口一一对应。
 * 均返回 Promise<data>(拦截器已解包信封)。
 */
import request from './request'

/* ---------------- 2.1 认证与健康 ---------------- */
export const authApi = {
  login(username, password) {
    return request.post('/auth/login', { username, password })
  },
  health() {
    return request.get('/health')
  }
}

/* ---------------- 2.2 设备管理 ---------------- */
export const deviceApi = {
  list(params) {
    return request.get('/devices', { params })
  },
  detail(deviceId) {
    return request.get(`/devices/${encodeURIComponent(deviceId)}`)
  },
  create(body) {
    return request.post('/devices', body)
  },
  update(deviceId, body) {
    return request.put(`/devices/${encodeURIComponent(deviceId)}`, body)
  },
  remove(deviceId) {
    return request.delete(`/devices/${encodeURIComponent(deviceId)}`)
  }
}

/* ---------------- 2.3 任务管理 ---------------- */
export const taskApi = {
  create(body) {
    return request.post('/tasks', body)
  },
  list(params) {
    return request.get('/tasks', { params })
  },
  detail(taskId) {
    return request.get(`/tasks/${encodeURIComponent(taskId)}`)
  }
}

/* ---------------- 2.4 告警管理 ---------------- */
export const alarmApi = {
  list(params) {
    return request.get('/alarms', { params })
  },
  detail(alarmId) {
    return request.get(`/alarms/${encodeURIComponent(alarmId)}`)
  },
  process(alarmId, processNote) {
    return request.put(`/alarms/${encodeURIComponent(alarmId)}/process`, {
      processNote
    })
  }
}

/* ---------------- 2.5 检索分析 ---------------- */
export const searchApi = {
  events(body) {
    return request.post('/search/events', body)
  },
  stats(params) {
    return request.get('/search/stats', { params })
  }
}

/* ---------------- 2.6 报告服务 ---------------- */
export const reportApi = {
  generate(body) {
    return request.post('/reports', body)
  }
}

/* ---------------- 2.7 文件上传下载 ---------------- */
export const fileApi = {
  upload(file, deviceId, taskId, onUploadProgress) {
    const form = new FormData()
    form.append('file', file)
    if (deviceId) form.append('deviceId', deviceId)
    if (taskId) form.append('taskId', taskId)
    return request.post('/files', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    })
  },
  list(params) {
    return request.get('/files', { params })
  },
  /** 下载: 携带 JWT 获取二进制 blob */
  async download(fileId) {
    const resp = await request.get(
      `/files/${encodeURIComponent(fileId)}/download`,
      { responseType: 'blob' }
    )
    return resp.data
  }
}
