<template>
  <div class="page-container reports">
    <!-- ======= 生成条件 ======= -->
    <el-card class="filter-card report-form-card" shadow="never">
      <el-form :inline="true">
        <el-form-item label="统计时段">
          <el-button-group>
            <el-button :type="quick === 1 ? 'primary' : ''" @click="setQuick(1)">今日</el-button>
            <el-button :type="quick === 7 ? 'primary' : ''" @click="setQuick(7)">近7天</el-button>
            <el-button :type="quick === 30 ? 'primary' : ''" @click="setQuick(30)">近30天</el-button>
          </el-button-group>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss+08:00"
            @change="quick = 0"
          />
        </el-form-item>
        <el-form-item label="报告标题">
          <el-input v-model="title" placeholder="选填, 缺省自动生成" style="width: 240px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Document" :loading="loading" @click="onGenerate">
            生成报告
          </el-button>
        </el-form-item>
      </el-form>
      <div class="text-muted tips">
        时间跨度不能超过 30 天(接口文档 §2.6.1)。报告由后端同步聚合 MongoDB + Elasticsearch 数据生成。
      </div>
    </el-card>

    <!-- ======= 报告内容 ======= -->
    <div v-if="report" class="report-paper">
      <div class="paper-head">
        <div>
          <h2>{{ report.title }}</h2>
          <div class="paper-meta">
            <span>报告编号: {{ report.reportId }}</span>
            <span>统计时段: {{ formatTime(report.from) }} ~ {{ formatTime(report.to) }}</span>
            <span>生成时间: {{ formatTime(report.generatedAt) }}</span>
          </div>
        </div>
        <el-button class="report-actions" :icon="Printer" @click="print">打印 / 存PDF</el-button>
      </div>

      <!-- 指标汇总 -->
      <div class="stat-grid">
        <div class="stat-item" v-for="s in statItems" :key="s.label">
          <div class="num mono" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="lab">{{ s.label }}</div>
        </div>
      </div>

      <el-row :gutter="16">
        <!-- 告警类型分布 -->
        <el-col :span="12">
          <div class="paper-section">
            <div class="section-title">告警类型分布</div>
            <el-table :data="alarmDistRows" size="small" border>
              <el-table-column label="告警类型">
                <template #default="{ row }">
                  {{ dictLabel(ALARM_TYPE, row.key) }}
                </template>
              </el-table-column>
              <el-table-column prop="count" label="数量" width="100" align="center" />
              <el-table-column label="占比" width="160">
                <template #default="{ row }">
                  <el-progress :percentage="alarmPercent(row.count)" :stroke-width="10" />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
        <!-- 设备上报排行 -->
        <el-col :span="12">
          <div class="paper-section">
            <div class="section-title">设备上报量排行</div>
            <el-table :data="deviceRankRows" size="small" border max-height="240">
              <el-table-column type="index" label="#" width="50" align="center" />
              <el-table-column prop="deviceId" label="设备编号" />
              <el-table-column prop="count" label="事件数" width="100" align="center" />
            </el-table>
          </div>
        </el-col>
      </el-row>

      <!-- 重点摘要 -->
      <div class="paper-section">
        <div class="section-title">重点关注</div>
        <ul v-if="report.highlights?.length" class="highlight-list">
          <li v-for="(h, i) in report.highlights" :key="i">
            <el-icon class="h-icon"><WarnTriangleFilled /></el-icon>{{ h }}
          </li>
        </ul>
        <el-empty v-else description="本时段无重点关注事项" :image-size="60" />
      </div>
    </div>

    <el-empty v-else description="请选择统计时段并生成巡检报告" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Printer } from '@element-plus/icons-vue'
import { reportApi } from '@/api'
import { ALARM_TYPE, dictLabel } from '@/constants/dict'
import { formatTime, recentDaysRange, toIsoString } from '@/utils/format'

const loading = ref(false)
const report = ref(null)
const title = ref('')
const quick = ref(7)

function endOfToday() {
  const d = new Date()
  d.setHours(23, 59, 59, 999)
  return d
}

function setQuick(days) {
  quick.value = days
  const [from, to] = recentDaysRange(days)
  timeRange.value = [from, days === 1 ? toIsoString(endOfToday()) : to]
}

const timeRange = ref((() => {
  const [from, to] = recentDaysRange(7)
  return [from, to]
})())

async function onGenerate() {
  if (!timeRange.value || timeRange.value.length !== 2) return
  const from = timeRange.value[0]
  const to = timeRange.value[1]
  const spanDays = (new Date(to) - new Date(from)) / 86400000
  if (spanDays > 30) {
    ElMessage.warning('单次报告时间跨度不能超过 30 天, 请缩小范围')
    return
  }
  loading.value = true
  try {
    report.value = await reportApi.generate({
      from,
      to,
      title: title.value || undefined
    })
  } finally {
    loading.value = false
  }
}

function print() {
  window.print()
}

const s = computed(() => report.value?.statistics || {})

const statItems = computed(() => [
  { label: '设备总数', value: s.value.deviceTotal ?? 0, color: '#1769ff' },
  { label: '在线设备', value: s.value.onlineDeviceTotal ?? 0, color: '#18b26b' },
  { label: '离线设备', value: s.value.offlineCount ?? 0, color: '#909399' },
  { label: '任务总数', value: s.value.taskTotal ?? 0, color: '#f08000' },
  { label: '告警总数', value: s.value.alarmTotal ?? 0, color: '#f03d3d' },
  { label: '事件总数', value: s.value.eventTotal ?? 0, color: '#0a8f6c' },
  { label: '影像文件', value: s.value.fileTotal ?? 0, color: '#7c5cff' }
])

const alarmDistRows = computed(() => s.value.alarmTypeDist || [])
const deviceRankRows = computed(() => s.value.deviceRank || [])
const alarmTotalCount = computed(() =>
  alarmDistRows.value.reduce((sum, i) => sum + (i.count || 0), 0)
)
function alarmPercent(count) {
  const total = alarmTotalCount.value
  return total ? Math.round((count / total) * 100) : 0
}
</script>

<style scoped>
.tips {
  font-size: 12px;
  margin-top: -6px;
}

.report-paper {
  background: #fff;
  border-radius: 10px;
  padding: 28px 32px;
  box-shadow: 0 2px 12px rgba(16, 27, 51, 0.06);
}
.paper-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  border-bottom: 2px solid #1769ff;
  padding-bottom: 16px;
  margin-bottom: 20px;
}
.paper-head h2 {
  font-size: 22px;
  color: #13294b;
  margin-bottom: 10px;
}
.paper-meta {
  display: flex;
  gap: 22px;
  font-size: 12px;
  color: #8a93a4;
  flex-wrap: wrap;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
  margin-bottom: 22px;
}
.stat-item {
  background: #f7f9fd;
  border: 1px solid #e8edf5;
  border-radius: 8px;
  padding: 14px 8px;
  text-align: center;
}
.stat-item .num {
  font-size: 26px;
  font-weight: 700;
}
.stat-item .lab {
  font-size: 12px;
  color: #8a93a4;
  margin-top: 4px;
}

.paper-section {
  margin-top: 8px;
  margin-bottom: 16px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #1769ff;
}
.highlight-list {
  list-style: none;
}
.highlight-list li {
  position: relative;
  padding: 7px 0 7px 24px;
  font-size: 13px;
  color: #44526a;
  border-bottom: 1px dashed #eef1f6;
}
.h-icon {
  position: absolute;
  left: 0;
  top: 9px;
  color: #ff9f1c;
}

@media print {
  .report-form-card,
  .report-actions {
    display: none !important;
  }
}
</style>
