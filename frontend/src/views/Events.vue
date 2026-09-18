<template>
  <div class="page-container">
    <!-- ======= 检索条件 ======= -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="form" label-width="82px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="设备编号">
              <el-select v-model="form.deviceId" filterable clearable placeholder="全部设备"
                         style="width: 100%" @visible-change="loadDevices">
                <el-option v-for="d in deviceOptions" :key="d.deviceId"
                           :label="`${d.deviceId} ${d.name}`" :value="d.deviceId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="事件类型">
              <el-select v-model="form.eventTypes" multiple collapse-tags collapse-tags-tooltip
                         clearable placeholder="全部类型" style="width: 100%">
                <el-option v-for="o in eventTypeOptions" :key="o.value"
                           :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DDTHH:mm:ss+08:00"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="告警类型">
              <el-select v-model="form.alarmType" clearable placeholder="全部" style="width: 100%">
                <el-option v-for="o in alarmTypeOptions" :key="o.value"
                           :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="告警等级">
              <el-select v-model="form.alarmLevel" clearable placeholder="全部" style="width: 100%">
                <el-option v-for="o in levelOptions" :key="o.value"
                           :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区域">
              <el-input v-model="form.area" placeholder="区域精确匹配" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关键词">
              <el-input v-model="form.keyword" placeholder="描述全文检索(IK 分词)" clearable
                        @keyup.enter="onSearch" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="地理检索">
              <el-radio-group v-model="geoMode" @change="onGeoModeChange">
                <el-radio value="none">不限</el-radio>
                <el-radio value="geo">圆形半径</el-radio>
                <el-radio value="bbox">矩形范围</el-radio>
              </el-radio-group>
              <div class="geo-fields">
                <template v-if="geoMode === 'geo'">
                  <el-input-number v-model="geo.center.lng" :precision="6" :step="0.001"
                                   :controls="false" placeholder="中心经度" />
                  <el-input-number v-model="geo.center.lat" :precision="6" :step="0.001"
                                   :controls="false" placeholder="中心纬度" />
                  <el-input-number v-model="geo.radiusKm" :min="0.1" :precision="1"
                                   placeholder="半径km" />
                </template>
                <template v-else-if="geoMode === 'bbox'">
                  <el-input-number v-model="bbox.topLeft.lng" :precision="6" :step="0.001"
                                   :controls="false" placeholder="左上经度" />
                  <el-input-number v-model="bbox.topLeft.lat" :precision="6" :step="0.001"
                                   :controls="false" placeholder="左上纬度" />
                  <el-input-number v-model="bbox.bottomRight.lng" :precision="6" :step="0.001"
                                   :controls="false" placeholder="右下经度" />
                  <el-input-number v-model="bbox.bottomRight.lat" :precision="6" :step="0.001"
                                   :controls="false" placeholder="右下纬度" />
                </template>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">检索</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置条件</el-button>
          <span class="text-muted rate-tip">
            <el-icon><WarningFilled /></el-icon>
            检索接口限流 10 次/秒, 请勿高频点击(触发 429 后请稍后再试)
          </span>
        </div>
      </el-form>
    </el-card>

    <!-- ======= 结果 ======= -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="事件时间" width="165">
          <template #default="{ row }">{{ formatTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column label="设备" min-width="170">
          <template #default="{ row }">
            <div class="mono">{{ row.deviceId }}</div>
            <div class="text-muted mini">{{ row.deviceName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag :type="dictTag(EVENT_TYPE, row.eventType)" size="small">
              {{ dictLabel(EVENT_TYPE, row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="告警" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.alarmType" :type="dictTag(ALARM_TYPE, row.alarmType)" size="small">
              {{ dictLabel(ALARM_TYPE, row.alarmType) }}
            </el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.alarmLevel" :type="dictTag(ALARM_LEVEL, row.alarmLevel)"
                    size="small" effect="dark">
              {{ dictLabel(ALARM_LEVEL, row.alarmLevel) }}
            </el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="area" label="区域" min-width="140" show-overflow-tooltip />
        <el-table-column label="温度℃" width="90">
          <template #default="{ row }">{{ row.temperature ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="doSearch"
          @size-change="doSearch"
        />
      </div>
    </el-card>

    <!-- ======= 详情抽屉 ======= -->
    <el-drawer v-model="detailVisible" title="巡检事件详情" size="420px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="事件编号">{{ current.eventId }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">
          <el-tag :type="dictTag(EVENT_TYPE, current.eventType)" size="small">
            {{ dictLabel(EVENT_TYPE, current.eventType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备">{{ current.deviceId }} ({{ current.deviceName }})</el-descriptions-item>
        <el-descriptions-item label="设备类型">
          {{ dictLabel(DEVICE_TYPE, current.deviceType) }}
        </el-descriptions-item>
        <el-descriptions-item label="关联任务">{{ current.taskId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="区域">{{ current.area || '—' }}</el-descriptions-item>
        <el-descriptions-item label="告警类型">
          {{ current.alarmType ? dictLabel(ALARM_TYPE, current.alarmType) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="告警等级">
          {{ current.alarmLevel ? dictLabel(ALARM_LEVEL, current.alarmLevel) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="温度 / 阈值">
          {{ current.temperature ?? '—' }} / {{ current.threshold ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="位置">
          {{ current.position ? `${current.position.lng}, ${current.position.lat}` : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="事件时间">{{ formatTime(current.eventTime) }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ current.description || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onActivated, reactive, ref } from 'vue'
import { Search, RefreshLeft, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { deviceApi, searchApi } from '@/api'
import {
  EVENT_TYPE, ALARM_TYPE, ALARM_LEVEL, DEVICE_TYPE, dictLabel, dictTag, dictOptions
} from '@/constants/dict'
import { formatTime } from '@/utils/format'

const eventTypeOptions = dictOptions(EVENT_TYPE)
const alarmTypeOptions = dictOptions(ALARM_TYPE)
const levelOptions = dictOptions(ALARM_LEVEL)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const searched = ref(false)

const emptyForm = () => ({
  deviceId: '',
  eventTypes: [],
  alarmType: '',
  alarmLevel: '',
  area: '',
  keyword: ''
})
const form = reactive(emptyForm())
const timeRange = ref(null)
const geoMode = ref('none')
const geo = reactive({ center: { lng: null, lat: null }, radiusKm: 5 })
const bbox = reactive({
  topLeft: { lng: null, lat: null },
  bottomRight: { lng: null, lat: null }
})

const deviceOptions = ref([])
async function loadDevices() {
  if (deviceOptions.value.length) return
  const data = await deviceApi.list({ size: 100 })
  deviceOptions.value = data.list || []
}

function onGeoModeChange() {
  // geo 与 bbox 互斥(接口文档 §2.5.1), 切换即清空另一组
}

function buildBody() {
  const body = {
    page: page.value,
    size: size.value,
    deviceId: form.deviceId || undefined,
    eventTypes: form.eventTypes.length ? form.eventTypes : undefined,
    alarmType: form.alarmType || undefined,
    alarmLevel: form.alarmLevel || undefined,
    area: form.area || undefined,
    keyword: form.keyword || undefined,
    from: timeRange.value?.[0] || undefined,
    to: timeRange.value?.[1] || undefined
  }
  if (geoMode.value === 'geo') {
    if (geo.center.lng === null || geo.center.lat === null || !geo.radiusKm) {
      ElMessage.warning('请填写完整的圆心经纬度和半径')
      return null
    }
    body.geo = {
      center: { lng: geo.center.lng, lat: geo.center.lat },
      radiusKm: geo.radiusKm
    }
  } else if (geoMode.value === 'bbox') {
    const { topLeft: t, bottomRight: b } = bbox
    if ([t.lng, t.lat, b.lng, b.lat].some((v) => v === null)) {
      ElMessage.warning('请填写矩形左上/右下四个坐标')
      return null
    }
    body.bbox = {
      topLeft: { lng: t.lng, lat: t.lat },
      bottomRight: { lng: b.lng, lat: b.lat }
    }
  }
  return body
}

async function doSearch() {
  const body = buildBody()
  if (!body) return
  loading.value = true
  try {
    const data = await searchApi.events(body)
    list.value = data.list || []
    total.value = data.total
    searched.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  doSearch()
}

function onReset() {
  Object.assign(form, emptyForm())
  timeRange.value = null
  geoMode.value = 'none'
  geo.center.lng = null
  geo.center.lat = null
  geo.radiusKm = 5
  bbox.topLeft = { lng: null, lat: null }
  bbox.bottomRight = { lng: null, lat: null }
  page.value = 1
  list.value = []
  total.value = 0
  searched.value = false
}

/* 详情 */
const detailVisible = ref(false)
const current = ref(null)
function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

let firstActivate = true
onActivated(() => {
  loadDevices()
  // 首次进入自动检索一次(仅一次, 避免触发 10r/s 限流)
  if (firstActivate) {
    firstActivate = false
    onSearch()
  }
})
</script>

<style scoped>
.filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rate-tip {
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.geo-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.geo-fields :deep(.el-input-number) {
  width: 130px;
}
.mini {
  font-size: 12px;
}
</style>
