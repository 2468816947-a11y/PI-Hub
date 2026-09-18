<template>
  <div class="page-container">
    <!-- ======= 筛选 ======= -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters">
        <el-form-item label="等级">
          <el-select v-model="filters.level" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="o in levelOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ======= 告警列表 ======= -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe :row-class-name="rowClass">
        <el-table-column prop="alarmId" label="告警编号" width="110" fixed>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row.alarmId)">{{ row.alarmId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="dictTag(ALARM_LEVEL, row.level)" size="small" effect="dark">
              {{ dictLabel(ALARM_LEVEL, row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="dictTag(ALARM_TYPE, row.alarmType)" size="small">
              {{ dictLabel(ALARM_TYPE, row.alarmType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" label="设备" width="130" />
        <el-table-column prop="taskId" label="关联任务" width="110">
          <template #default="{ row }">{{ row.taskId || '—' }}</template>
        </el-table-column>
        <el-table-column label="实测/阈值" width="130">
          <template #default="{ row }">
            <span v-if="row.value !== null && row.value !== undefined" class="mono">
              {{ row.value }} / {{ row.threshold ?? '—' }}
            </span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="告警描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="dictTag(ALARM_STATUS, row.status)" size="small">
              {{ dictLabel(ALARM_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产生时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.alarmId)">详情</el-button>
            <el-button
              v-if="row.status === 'NEW'"
              link type="success" size="small"
              @click="openProcess(row)"
            >处置</el-button>
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

    <!-- ======= 处置对话框 ======= -->
    <el-dialog v-model="processVisible" title="告警处置" width="480px">
      <el-alert
        v-if="current"
        :title="`${current.alarmId} · ${dictLabel(ALARM_TYPE, current.alarmType)} · ${dictLabel(ALARM_LEVEL, current.level)}`"
        :description="current.description"
        :type="current.level === 'CRITICAL' ? 'error' : 'warning'"
        show-icon
        :closable="false"
        class="process-alert"
      />
      <el-form ref="processFormRef" :model="processForm" :rules="processRules" style="margin-top: 14px">
        <el-form-item label="处置备注" prop="processNote" label-width="90px">
          <el-input
            v-model="processForm.processNote"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请填写处置措施, 最长 500 字(必填)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="success" :loading="processing" @click="onProcess">确认处置</el-button>
      </template>
    </el-dialog>

    <!-- ======= 详情抽屉 ======= -->
    <el-drawer v-model="detailVisible" title="告警详情" size="420px">
      <div v-if="detail" class="detail-box">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="告警编号">{{ detail.alarmId }}</el-descriptions-item>
          <el-descriptions-item label="等级">
            <el-tag :type="dictTag(ALARM_LEVEL, detail.level)" size="small" effect="dark">
              {{ dictLabel(ALARM_LEVEL, detail.level) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">
            {{ dictLabel(ALARM_TYPE, detail.alarmType) }}
          </el-descriptions-item>
          <el-descriptions-item label="设备编号">{{ detail.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="关联任务">{{ detail.taskId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="实测值">{{ detail.value ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="阈值">{{ detail.threshold ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="位置">
            {{ detail.position ? `${detail.position.lng}, ${detail.position.lat}` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="dictTag(ALARM_STATUS, detail.status)" size="small">
              {{ dictLabel(ALARM_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产生时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="处置时间">{{ formatTime(detail.processTime) }}</el-descriptions-item>
          <el-descriptions-item label="处置备注">{{ detail.processNote || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button
          v-if="detail.status === 'NEW'"
          type="success" style="margin-top: 16px; width: 100%"
          @click="detailVisible = false; openProcess(detail)"
        >前往处置</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onActivated, onDeactivated, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { alarmApi } from '@/api'
import {
  ALARM_LEVEL, ALARM_TYPE, ALARM_STATUS, dictLabel, dictTag, dictOptions
} from '@/constants/dict'
import { formatTime } from '@/utils/format'

const levelOptions = dictOptions(ALARM_LEVEL)
const typeOptions = dictOptions(ALARM_TYPE)
const statusOptions = dictOptions(ALARM_STATUS)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ level: '', type: '', status: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await alarmApi.list({
      page: page.value,
      size: size.value,
      level: filters.level || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined
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
  filters.level = ''
  filters.type = ''
  filters.status = ''
  page.value = 1
  loadList()
}

// 严重未处置告警行高亮
function rowClass({ row }) {
  if (row.status === 'NEW' && row.level === 'CRITICAL') return 'row-critical'
  return ''
}

/* ---------- 处置 ---------- */
const processVisible = ref(false)
const processing = ref(false)
const current = ref(null)
const processFormRef = ref()
const processForm = reactive({ processNote: '' })
const processRules = {
  processNote: [
    { required: true, message: '请填写处置备注', trigger: 'blur' },
    { max: 500, message: '处置备注不能超过 500 字', trigger: 'blur' }
  ]
}

function openProcess(row) {
  current.value = row
  processForm.processNote = row.processNote || ''
  processVisible.value = true
}

async function onProcess() {
  await processFormRef.value.validate().catch(() => null)
  if (!processForm.processNote) return
  processing.value = true
  try {
    await alarmApi.process(current.value.alarmId, processForm.processNote.trim())
    ElMessage.success('告警已处置')
    processVisible.value = false
    loadList()
  } finally {
    processing.value = false
  }
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const detail = ref(null)
async function openDetail(alarmId) {
  detail.value = null
  detailVisible.value = true
  detail.value = await alarmApi.detail(alarmId)
}

/* ---------- 轮询 ---------- */
let timer = null
onActivated(() => {
  loadList()
  timer = setInterval(loadList, 10000)
})
onDeactivated(() => clearInterval(timer))
</script>

<style scoped>
.process-alert {
  margin-top: 4px;
}
.detail-box {
  padding: 0 4px;
}
:deep(.row-critical) {
  background-color: #fff6f6 !important;
}
</style>
