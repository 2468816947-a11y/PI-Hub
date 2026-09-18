<template>
  <div class="page-container dashboard">
    <!-- ======= 指标卡片 ======= -->
    <el-row :gutter="14">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="dash-card stat-card">
          <div class="stat-icon" :style="{ background: card.bg }">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value mono">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-sub" v-html="card.sub"></div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ======= 地图 + 告警类型 ======= -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="16">
        <div class="dash-card map-card">
          <div class="card-head">
            <span class="section-title">设备分布地图</span>
            <div class="flex-center gap-12">
              <span class="text-muted mini">数据每 5s 刷新 · {{ updateTime }}</span>
              <el-button text type="primary" @click="goDevices">设备台账<el-icon><ArrowRight /></el-icon></el-button>
            </div>
          </div>
          <div class="map-wrap">
            <DeviceMap :devices="devices" @select="goDevices" />
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="dash-card chart-card">
          <div class="card-head">
            <span class="section-title">告警类型分布</span>
            <el-radio-group v-model="range" size="small" @change="loadStats">
              <el-radio-button value="24h">近24h</el-radio-button>
              <el-radio-button value="7d">近7天</el-radio-button>
            </el-radio-group>
          </div>
          <div class="chart-wrap">
            <EChart v-if="hasAlarmDist" :option="alarmPieOption" height="268px" />
            <el-empty v-else description="暂无告警数据" :image-size="80" />
          </div>
        </div>
        <div class="dash-card chart-card mt14">
          <div class="card-head"><span class="section-title">区域事件分布</span></div>
          <div class="chart-wrap">
            <EChart v-if="hasAreaDist" :option="areaBarOption" height="236px" />
            <el-empty v-else description="暂无数据" :image-size="70" />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ======= 趋势 + 设备排行 ======= -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="14">
        <div class="dash-card chart-card">
          <div class="card-head"><span class="section-title">巡检事件时间趋势(按小时)</span></div>
          <div class="chart-wrap">
            <EChart v-if="hasTrend" :option="trendOption" height="280px" />
            <el-empty v-else description="暂无事件数据" :image-size="70" />
          </div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="dash-card chart-card">
          <div class="card-head"><span class="section-title">设备上报量排行 TOP6</span></div>
          <div class="chart-wrap">
            <EChart v-if="hasRank" :option="rankOption" height="280px" />
            <el-empty v-else description="暂无数据" :image-size="70" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onActivated, onDeactivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import DeviceMap from '@/components/DeviceMap.vue'
import EChart from '@/components/EChart.vue'
import { alarmApi, deviceApi, searchApi, taskApi } from '@/api'
import { ALARM_TYPE } from '@/constants/dict'
import { formatTime, recentDaysRange } from '@/utils/format'

const router = useRouter()

const devices = ref([])
const stats = ref(null)
const newAlarmTotal = ref(0)
const criticalAlarmTotal = ref(0)
const runningTaskTotal = ref(0)
const updateTime = ref('')
const range = ref('24h')

let deviceTimer = null
let statTimer = null

const onlineDevices = computed(() => devices.value.filter((d) => d.status === 'ONLINE'))
const offlineDevices = computed(() => devices.value.filter((d) => d.status === 'OFFLINE'))

const cards = computed(() => [
  {
    label: '设备在线',
    value: `${onlineDevices.value.length}/${devices.value.length}`,
    sub: `离线 <b class="text-danger">${offlineDevices.value.length}</b> 台`,
    icon: 'Cpu',
    bg: 'linear-gradient(135deg,#1d6cff,#0e47b8)'
  },
  {
    label: '未处置告警',
    value: newAlarmTotal.value,
    sub: `严重 <b class="text-danger">${criticalAlarmTotal.value}</b> 条`,
    icon: 'Bell',
    bg: 'linear-gradient(135deg,#ff6a3d,#d62d2d)'
  },
  {
    label: '进行中任务',
    value: runningTaskTotal.value,
    sub: '已下发 / 执行中',
    icon: 'List',
    bg: 'linear-gradient(135deg,#ffb020,#f08000)'
  },
  {
    label: '区间巡检事件',
    value: stats.value?.totals?.events ?? 0,
    sub: `告警累计 <b>${stats.value?.totals?.alarms ?? 0}</b> 条`,
    icon: 'DataLine',
    bg: 'linear-gradient(135deg,#13c296,#0a8f6c)'
  }
])

/* ---------- 数据加载 ---------- */
async function loadDevices() {
  try {
    const data = await deviceApi.list({ size: 100 })
    devices.value = data.list || []
    updateTime.value = formatTime(new Date())
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}

function rangeParams() {
  // 近24h 不传时间, 由后端取默认(接口文档 §2.5.2)
  if (range.value === '24h') return {}
  const [from, to] = recentDaysRange(7)
  return { from, to }
}

async function loadStats() {
  const p = rangeParams()
  const [statsRes, newAlarm, criticalAlarm, runningTask] = await Promise.allSettled([
    searchApi.stats(p),
    alarmApi.list({ status: 'NEW', size: 1 }),
    alarmApi.list({ status: 'NEW', level: 'CRITICAL', size: 1 }),
    taskApi.list({ status: 'RUNNING', size: 1 })
  ])
  if (statsRes.status === 'fulfilled') stats.value = statsRes.value
  if (newAlarm.status === 'fulfilled') newAlarmTotal.value = newAlarm.value.total ?? 0
  if (criticalAlarm.status === 'fulfilled') criticalAlarmTotal.value = criticalAlarm.value.total ?? 0
  if (runningTask.status === 'fulfilled') runningTaskTotal.value = runningTask.value.total ?? 0
}

function refreshAll() {
  if (document.hidden) return
  loadDevices()
  loadStats()
}

function onVisible() {
  if (!document.hidden) refreshAll()
}

// keep-alive 激活时启动轮询, 离开页面暂停(接口文档附录 C: 设备 5s 轮询)
onActivated(() => {
  refreshAll()
  deviceTimer = setInterval(loadDevices, 5000)
  statTimer = setInterval(loadStats, 10000)
  document.addEventListener('visibilitychange', onVisible)
})
onDeactivated(() => {
  clearInterval(deviceTimer)
  clearInterval(statTimer)
  document.removeEventListener('visibilitychange', onVisible)
})

function goDevices(device) {
  if (device && device.deviceId) {
    router.push({ path: '/devices', query: { deviceId: device.deviceId } })
  } else {
    router.push('/devices')
  }
}

/* ---------- ECharts 配置 ---------- */
const ALARM_COLORS = {
  TEMP_OVER: '#f03d3d',
  BATTERY_LOW: '#ff9f1c',
  OFFLINE: '#909399',
  FAULT: '#d6336c'
}

const alarmDist = computed(() => stats.value?.alarmTypeDist || [])
const hasAlarmDist = computed(() => alarmDist.value.some((i) => i.count > 0))

const alarmPieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{c}', fontSize: 11 },
      data: alarmDist.value.map((i) => ({
        name: ALARM_TYPE[i.key]?.label || i.key,
        value: i.count,
        itemStyle: { color: ALARM_COLORS[i.key] || '#7a869c' }
      }))
    }
  ]
}))

const trend = computed(() => stats.value?.timeTrend || [])
const hasTrend = computed(() => trend.value.some((i) => i.count > 0))

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 44, right: 20, top: 24, bottom: 46 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map((i) => i.bucket.slice(11, 16)),
    axisLabel: { color: '#8a93a4', fontSize: 10, interval: 'auto' },
    axisLine: { lineStyle: { color: '#dbe2ee' } }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#8a93a4' },
    splitLine: { lineStyle: { color: '#eef1f6' } }
  },
  series: [
    {
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: trend.value.map((i) => i.count),
      lineStyle: { width: 3, color: '#1769ff' },
      itemStyle: { color: '#1769ff' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(23,105,255,0.28)' },
            { offset: 1, color: 'rgba(23,105,255,0.02)' }
          ]
        }
      }
    }
  ]
}))

const rank = computed(() => (stats.value?.deviceRank || []).slice(0, 6))
const hasRank = computed(() => rank.value.length > 0)

const rankOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 110, right: 30, top: 16, bottom: 24 },
  xAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#8a93a4' },
    splitLine: { lineStyle: { color: '#eef1f6' } }
  },
  yAxis: {
    type: 'category',
    inverse: true,
    data: rank.value.map((i) => i.deviceId),
    axisLabel: { color: '#44526a', fontSize: 11 },
    axisLine: { show: false },
    axisTick: { show: false }
  },
  series: [
    {
      type: 'bar',
      barWidth: 14,
      data: rank.value.map((i) => i.count),
      itemStyle: {
        borderRadius: [0, 7, 7, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [
            { offset: 0, color: '#5490ff' },
            { offset: 1, color: '#1769ff' }
          ]
        }
      },
      label: { show: true, position: 'right', fontSize: 11, color: '#44526a' }
    }
  ]
}))

const areaDist = computed(() => stats.value?.areaDist || [])
const hasAreaDist = computed(() => areaDist.value.some((i) => i.count > 0))

const areaBarOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 12, right: 20, top: 20, bottom: 60 },
  xAxis: {
    type: 'category',
    data: areaDist.value.map((i) => i.key),
    axisLabel: {
      color: '#8a93a4', fontSize: 10, interval: 0,
      formatter: (v) => (v && v.length > 6 ? v.slice(0, 6) + '…' : v)
    },
    axisLine: { lineStyle: { color: '#dbe2ee' } },
    axisTick: { show: false }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#8a93a4' },
    splitLine: { lineStyle: { color: '#eef1f6' } }
  },
  series: [
    {
      type: 'bar',
      barWidth: 18,
      data: areaDist.value.map((i) => i.count),
      itemStyle: {
        borderRadius: [7, 7, 0, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#22c99a' },
            { offset: 1, color: '#0e9a74' }
          ]
        }
      }
    }
  ]
}))
</script>

<style scoped>
.mt14 { margin-top: 14px; }

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 54px;
  height: 54px;
  border-radius: 12px;
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #13294b;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #8a93a4;
  margin-top: 2px;
}
.stat-sub {
  font-size: 11px;
  color: #aab3c2;
  margin-top: 4px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.mini { font-size: 12px; }
.map-card { height: 100%; }
.map-wrap {
  height: 520px;
}
.chart-wrap {
  position: relative;
}
.chart-wrap .el-empty {
  padding: 20px 0;
}
</style>
