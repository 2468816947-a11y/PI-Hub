<template>
  <div class="page-container">
    <!-- ======= 筛选 ======= -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-button type="success" :icon="Promotion" @click="openCreate">创建并下发任务</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ======= 任务列表 ======= -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="taskId" label="任务编号" width="110" fixed>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row.taskId)">{{ row.taskId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="dictTag(TASK_TYPE, row.taskType)" size="small">
              {{ dictLabel(TASK_TYPE, row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="area" label="区域" min-width="140" show-overflow-tooltip />
        <el-table-column label="指派设备" min-width="170">
          <template #default="{ row }">
            <el-tag v-for="id in row.deviceIds" :key="id" size="small" class="dev-tag">{{ id }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="dictTag(TASK_STATUS, row.status)" size="small" effect="dark">
              {{ dictLabel(TASK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果(测点/告警)" width="130">
          <template #default="{ row }">
            <span v-if="row.result" class="mono">
              {{ row.result.pointCount ?? 0 }} /
              <span :class="{ 'text-danger': row.result.alarmCount > 0 }">{{ row.result.alarmCount ?? 0 }}</span>
            </span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.taskId)">详情</el-button>
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
          @current-change="loadList"
          @size-change="loadList"
        />
      </div>
    </el-card>

    <!-- ======= 创建任务 ======= -->
    <el-dialog v-model="dialogVisible" title="创建并下发巡检任务" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="如: 2号主变压器红外测温" />
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检区域">
          <el-input v-model="form.area" list="task-area-list" placeholder="如: 1号变电站·东区" />
          <datalist id="task-area-list">
            <option v-for="a in knownAreas" :key="a" :value="a" />
          </datalist>
        </el-form-item>
        <el-form-item label="指派设备" prop="deviceIds">
          <el-select
            v-model="form.deviceIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            filterable
            placeholder="仅可选择在线设备(全部设备须在线, 否则 409)"
            style="width: 100%"
            @visible-change="loadDevices"
          >
            <el-option
              v-for="d in deviceOptions"
              :key="d.deviceId"
              :label="`${d.deviceId} ${d.name}`"
              :value="d.deviceId"
              :disabled="d.status !== 'ONLINE'"
            >
              <span>{{ d.deviceId }} {{ d.name }}</span>
              <el-tag size="small" :type="d.status === 'ONLINE' ? 'success' : 'info'" class="opt-tag">
                {{ d.status === 'ONLINE' ? '在线' : '离线' }}
              </el-tag>
              <el-tag size="small" :type="dictTag(DEVICE_TYPE, d.deviceType)" class="opt-tag">
                {{ dictLabel(DEVICE_TYPE, d.deviceType) }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>

        <template v-if="form.taskType === 'THERMAL'">
          <el-form-item label="测点数">
            <el-input-number v-model="form.points" :min="1" :max="999" />
            <span class="form-hint">params.points, 进度估算依据(缺省 12)</span>
          </el-form-item>
          <el-form-item label="超温阈值℃">
            <el-input-number v-model="form.tempThreshold" :min="1" :max="300" :precision="1" />
            <span class="form-hint">params.tempThreshold, 超过即告警(缺省 80)</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :icon="Promotion" @click="onSubmit">
          创建并下发
        </el-button>
      </template>
    </el-dialog>

    <!-- ======= 任务详情 ======= -->
    <el-drawer v-model="detailVisible" title="任务详情与执行进度" size="460px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="任务编号">{{ detail.taskId }}</el-descriptions-item>
            <el-descriptions-item label="任务名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag :type="dictTag(TASK_TYPE, detail.taskType)" size="small">
                {{ dictLabel(TASK_TYPE, detail.taskType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="区域">{{ detail.area || '—' }}</el-descriptions-item>
            <el-descriptions-item label="指派设备">
              <el-tag v-for="id in detail.deviceIds" :key="id" size="small" class="dev-tag">{{ id }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="dictTag(TASK_STATUS, detail.status)" size="small" effect="dark">
                {{ dictLabel(TASK_STATUS, detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="下发时间">{{ formatTime(detail.dispatchTime) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ formatTime(detail.finishTime) }}</el-descriptions-item>
            <el-descriptions-item label="执行结果" v-if="detail.result">
              测点 {{ detail.result.pointCount ?? 0 }} 个,
              告警 {{ detail.result.alarmCount ?? 0 }} 条
              <div v-if="detail.result.detail" class="text-muted">{{ detail.result.detail }}</div>
            </el-descriptions-item>
          </el-descriptions>

          <div class="progress-title">设备执行进度</div>
          <el-empty v-if="!progress.length" description="暂无设备进度数据" :image-size="60" />
          <div v-for="p in progress" :key="p.deviceId" class="progress-item">
            <div class="flex-between">
              <span class="mono">{{ p.deviceId }}</span>
              <el-tag size="small" :type="dictTag(TASK_STATUS, p.status) || 'info'">
                {{ dictLabel(TASK_STATUS, p.status) || p.status }}
              </el-tag>
            </div>
            <el-progress :percentage="p.progress" :status="p.status === 'FAILED' ? 'exception' : 'success'" />
            <div class="text-muted mini">最近更新: {{ formatTime(p.lastUpdateTime) }}</div>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onActivated, onDeactivated, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Promotion } from '@element-plus/icons-vue'
import { deviceApi, taskApi } from '@/api'
import {
  TASK_TYPE, TASK_STATUS, DEVICE_TYPE, dictLabel, dictTag, dictOptions
} from '@/constants/dict'
import { formatTime } from '@/utils/format'

const statusOptions = dictOptions(TASK_STATUS)
const typeOptions = dictOptions(TASK_TYPE)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ status: '', type: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await taskApi.list({
      page: page.value,
      size: size.value,
      status: filters.status || undefined,
      type: filters.type || undefined
    })
    list.value = data.list || []
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function onSearch() {
  page.value = 1
  loadList()
}
function onReset() {
  filters.status = ''
  filters.type = ''
  page.value = 1
  loadList()
}

/* ---------- 创建任务 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const deviceOptions = ref([])
const knownAreas = ref(['1号变电站·东区', '2号变电站·西区'])
const emptyForm = () => ({
  name: '', taskType: 'THERMAL', area: '', deviceIds: [], points: 12, tempThreshold: 80
})
const form = reactive(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  deviceIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一台设备', trigger: 'change' }]
}

async function loadDevices() {
  const data = await deviceApi.list({ size: 100 })
  deviceOptions.value = data.list || []
  for (const d of deviceOptions.value) {
    if (d.area && !knownAreas.value.includes(d.area)) knownAreas.value.push(d.area)
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  loadDevices()
  dialogVisible.value = true
}
function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, emptyForm())
}

async function onSubmit() {
  await formRef.value.validate().catch(() => null)
  if (!form.name || !form.deviceIds.length) return
  const body = {
    name: form.name,
    taskType: form.taskType,
    area: form.area || undefined,
    deviceIds: form.deviceIds
  }
  // 仅 THERMAL 携带 params(接口文档 §2.3.1, 其他类型由后端取默认)
  if (form.taskType === 'THERMAL') {
    body.params = { points: form.points, tempThreshold: form.tempThreshold }
  }
  saving.value = true
  try {
    const task = await taskApi.create(body)
    ElMessage.success(`任务 ${task.taskId} 已下发, 状态: 已下发`)
    dialogVisible.value = false
    page.value = 1
    loadList()
  } finally {
    saving.value = false
  }
}

/* ---------- 任务详情 + 进度轮询 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const progress = ref([])
let progressTimer = null

async function loadDetail(taskId) {
  const data = await taskApi.detail(taskId)
  detail.value = data.task
  progress.value = data.deviceProgress || []
}

async function openDetail(taskId) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  progress.value = []
  try {
    await loadDetail(taskId)
  } finally {
    detailLoading.value = false
  }
}

function stopProgressTimer() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

watch(detailVisible, async (visible) => {
  stopProgressTimer()
  if (visible && detail.value) {
    progressTimer = setInterval(async () => {
      // 终态后停止轮询
      if (['FINISHED', 'FAILED'].includes(detail.value.status)) {
        stopProgressTimer()
        return
      }
      await loadDetail(detail.value.taskId)
      loadList()
    }, 4000)
  }
})

/* ---------- 列表轮询 ---------- */
let timer = null
onActivated(() => {
  loadList()
  timer = setInterval(loadList, 8000)
})
onDeactivated(() => {
  clearInterval(timer)
  stopProgressTimer()
})
</script>

<style scoped>
.dev-tag {
  margin: 2px 4px 2px 0;
}
.opt-tag {
  margin-left: 6px;
}
.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #aab3c2;
}
.progress-title {
  font-size: 15px;
  font-weight: 600;
  margin: 18px 0 12px;
}
.progress-item {
  padding: 10px 0;
  border-bottom: 1px dashed #e8edf5;
}
.mini {
  font-size: 12px;
  margin-top: 2px;
}
</style>
