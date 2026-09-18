/**
 * 展示层格式化工具。
 * 时间约定(接口文档 §1.5): 后端返回 ISO 8601 带时区字符串, 前端负责格式化。
 */

const pad = (n) => String(n).padStart(2, '0')

/** ISO 时间 -> YYYY-MM-DD HH:mm:ss (按浏览器本地时区) */
export function formatTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** ISO 时间 -> YYYY-MM-DD */
export function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 相对时间: "x 秒前 / x 分钟前 / x 小时前" */
export function timeAgo(value) {
  if (!value) return '从未'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const diff = Date.now() - d.getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return `${sec} 秒前`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  return formatDate(value)
}

/** 字节数 -> 可读大小 */
export function formatSize(bytes) {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/**
 * Date 对象 -> 后端可接受的 ISO 8601 (+08:00)。
 * el-date-picker 直接 toISOString() 是 UTC Z 后缀, 后端 OffsetDateTime 也能解析,
 * 这里统一补上 +08:00 偏移, 与文档示例保持一致。
 */
export function toIsoString(date) {
  if (!date) return null
  const d = date instanceof Date ? date : new Date(date)
  const tzOffsetMin = -d.getTimezoneOffset() // 东八区为 -480 -> 480
  const sign = tzOffsetMin >= 0 ? '+' : '-'
  const abs = Math.abs(tzOffsetMin)
  const tz = `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}:${pad(d.getSeconds())}${tz}`
}

/** 获取今天 00:00 / 23:59:59 的 ISO 字符串 */
export function todayRange() {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  return [toIsoString(start), toIsoString(end)]
}

/** 最近 n 天范围 [from, to] ISO */
export function recentDaysRange(n) {
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  const start = new Date()
  start.setDate(start.getDate() - (n - 1))
  start.setHours(0, 0, 0, 0)
  return [toIsoString(start), toIsoString(end)]
}
