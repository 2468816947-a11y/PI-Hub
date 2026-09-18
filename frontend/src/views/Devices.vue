<template>
  <div class="page-container">
    <!-- ======= 筛选 ======= -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="filters.area" placeholder="区域模糊筛选" clearable style="width: 180px"
                    @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新增设备</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ======= 表格 ======= -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="deviceId" label="设备编号" width="130" fixed>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row.deviceId)">{{ row.deviceId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="dictTag(DEVICE_TYPE, row.deviceType)" size="small">
              {{ dictLabel(DEVICE_TYPE, row.deviceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="设备名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="area" label="所属区域" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="dictTag(DEVICE_STATUS, row.status)" size="small" effect="light">
              {{ dictLabel(DEVICE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="电量" width="130">
          <template #default="{ row }">
            <el-progress
              :percentage="row.battery ?? 0"
              :status="batteryStatus(row.battery)"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column label="故障" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.faultCode" type="danger" size="small">{{ row.faultCode }}</el-tag>
            <span v-else class="text-muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column label="最近心跳" width="140">
          <template #default="{ row }">
            <span :title="formatTime(row.lastHeartbeat)">{{ timeAgo(row.lastHeartbeat) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.deviceId)">详情</el-button>
            <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadList"
          @size-change="loadList"
        />
      </div>
    </el-card>

    <!-- ======= 新增/编辑对话框 ======= -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="设备编号" prop="deviceId">
          <el-input v-model="form.deviceId" placeholder="选填, 不传则后端自动生成"
                    :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="设备类型" prop="deviceType">
          <el-radio-group v-model="form.deviceType" :disabled="isEdit">
            <el-radio value="UAV">无人机</el-radio>
            <el-radio value="ROBOT_DOG">机器狗</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="form.name" placeholder="如: 无人机1号" />
        </el-form-item>
        <el-form-item label="型号" prop="model">
          <el-input v-model="form.model" placeholder="如: 巡检无人机-M300" />
        </el-form-item>
        <el-form-item label="所属区域" prop="area">
          <el-input v-model="form.area" list="area-list" placeholder="如: 1号变电站·东区" />
          <datalist id="area-list">
            <option v-for="a in knownAreas" :key="a" :value="a" />
          </datalist>
        </el-form-item>
        <el-form-item v-if="isEdit" label="故障码" prop="faultCode">
          <el-input v-model="form.faultCode" placeholder="无故障留空" />
        </el-form-item>
        <el-form-item label="坐标">
          <div class="pos-row">
            <el-input-number v-model="form.lng" :precision="6" :step="0.001" :controls="false"
                             placeholder="经度 lng" class="pos-input" />
            <el-input-number v-model="form.lat" :precision="6" :step="0.001" :controls="false"
                             placeholder="纬度 lat" class="pos-input" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- ======= 详情抽屉 ======= -->
    <el-drawer v-model="detailVisible" title="设备详情" size="420px">
      <div v-if="detail" class="detail-box">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="设备编号">{{ detail.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="设备类型">
            <el-tag :type="dictTag(DEVICE_TYPE, detail.deviceType)" size="small">
              {{ dictLabel(DEVICE_TYPE, detail.deviceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ detail.model || '—' }}</el-descriptions-item>
          <el-descriptions-item label="所属区域">{{ detail.area || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="dictTag(DEVICE_STATUS, detail.status)" size="small">
              {{ dictLabel(DEVICE_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="电量">{{ detail.battery ?? '—' }}%</el-descriptions-item>
          <el-descriptions-item label="故障码">
            <span v-if="detail.faultCode" class="text-danger">{{ detail.faultCode }}</span>
            <span v-else>无</span>
          </el-descriptions-item>
          <el-descriptions-item label="坐标">
            {{ detail.position ? `${detail.position.lng}, ${detail.position.lat}` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="最近心跳">{{ formatTime(detail.lastHeartbeat) }}
            ({{ timeAgo(detail.lastHeartbeat) }})
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatTime(detail.registerTime) }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.position" class="detail-map">
          <DeviceMap :devices="[detail]" :selected-id="detail.deviceId" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onActivated, onDeactivated, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import DeviceMap from '@/components/DeviceMap.vue'
import { deviceApi } from '@/api'
import {
  DEVICE_TYPE, DEVICE_STATUS, dictLabel, dictTag, dictOptions
} from '@/constants/dict'
import { formatTime, timeAgo } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const statusOptions = dictOptions(DEVICE_STATUS)
const typeOptions = dictOptions(DEVICE_TYPE)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ status: '', type: '', area: '' })

const knownAreas = ref(['1号变电站·东区', '2号变电站·西区'])

async function loadList() {
  loading.value = true
  try {
    const data = await deviceApi.list({
      page: page.value,
      size: size.value,
      status: filters.status || undefined,
      type: filters.type || undefined,
      area: filters.area || undefined
    })
    list.value = data.list || []
    total.value = data.total
    // 收集已知区域用于 datalist
    for (const d of list.value) {
      if (d.area && !knownAreas.value.includes(d.area)) knownAreas.value.push(d.area)
    }
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
  filters.area = ''
  page.value = 1
  loadList()
}

function batteryStatus(battery) {
  if (battery === null || battery === undefined) return ''
  if (battery < 20) return 'exception'
  if (battery < 50) return 'warning'
  return 'success'
}

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref()
const emptyForm = () => ({
  deviceId: '', deviceType: 'UAV', name: '', model: '', area: '',
  faultCode: '', lng: null, lat: null
})
const form = reactive(emptyForm())

const rules = {
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    deviceId: row.deviceId,
    deviceType: row.deviceType,
    name: row.name || '',
    model: row.model || '',
    area: row.area || '',
    faultCode: row.faultCode || '',
    lng: row.position?.lng ?? null,
    lat: row.position?.lat ?? null
  })
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, emptyForm())
}

async function onSubmit() {
  await formRef.value.validate().catch(() => null)
  if (!form.name || !form.deviceType) return
  const position =
    form.lng !== null && form.lat !== null && form.lng !== undefined && form.lat !== undefined
      ? { lng: form.lng, lat: form.lat }
      : undefined
  saving.value = true
  try {
    if (isEdit.value) {
      await deviceApi.update(form.deviceId, {
        name: form.name,
        model: form.model || undefined,
        area: form.area || undefined,
        faultCode: form.faultCode || undefined,
        position
      })
      ElMessage.success('设备已更新')
    } else {
      await deviceApi.create({
        deviceId: form.deviceId || undefined,
        deviceType: form.deviceType,
        name: form.name,
        model: form.model || undefined,
        area: form.area || undefined,
        position
      })
      ElMessage.success('设备已新增(初始离线, 首条心跳后上线)')
    }
    dialogVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确认删除设备 ${row.deviceId}(${row.name}) 吗? 存在未完成任务时将被后端拒绝(409)。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  ).catch(() => Promise.reject())
  await deviceApi.remove(row.deviceId)
  ElMessage.success('已删除')
  if (list.value.length === 1 && page.value > 1) page.value--
  loadList()
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const detail = ref(null)

async function openDetail(deviceId) {
  detail.value = null
  detailVisible.value = true
  detail.value = await deviceApi.detail(deviceId)
}

/* ---------- 轮询(keep-alive 激活时) ---------- */
let timer = null
onActivated(() => {
  loadList()
  if (route.query.deviceId) {
    const id = String(route.query.deviceId)
    openDetail(id)
    router.replace({ query: {} }) // 清掉参数, 避免重新进入时反复弹抽屉
  }
  timer = setInterval(loadList, 8000)
})
onDeactivated(() => clearInterval(timer))
</script>

<style scoped>
.filter-form {
  display: flex;
  flex-wrap: wrap;
}
.pos-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.pos-input {
  flex: 1;
}
.pos-input :deep(.el-input__wrapper) {
  width: 100%;
}
.detail-box {
  padding: 0 4px;
}
.detail-map {
  margin-top: 16px;
  height: 260px;
  border: 1px solid #e8edf5;
  border-radius: 8px;
}
</style>
