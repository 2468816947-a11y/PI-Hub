<template>
  <div class="page-container">
    <!-- ======= 上传 ======= -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true">
        <el-form-item label="关联设备">
          <el-select v-model="uploadDevice" filterable clearable placeholder="不关联"
                     style="width: 200px" @visible-change="loadDevices">
            <el-option v-for="d in deviceOptions" :key="d.deviceId"
                       :label="`${d.deviceId} ${d.name}`" :value="d.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="uploadTask" filterable clearable placeholder="不关联"
                     style="width: 200px" @visible-change="loadTasks">
            <el-option v-for="t in taskOptions" :key="t.taskId"
                       :label="`${t.taskId} ${t.name}`" :value="t.taskId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-upload
            ref="uploadRef"
            :show-file-list="false"
            :auto-upload="false"
            accept="image/jpeg,image/png,image/webp"
            :on-change="onFileChange"
          >
            <el-button type="success" :icon="Upload">选择并上传巡检图片</el-button>
            <template #tip>
              <div class="upload-tip">仅 jpg/jpeg/png/webp, 单文件 ≤ 20MB; 文件写入 HDFS 并登记元数据</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ======= 筛选 ======= -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters">
        <el-form-item label="设备">
          <el-select v-model="filters.deviceId" filterable clearable placeholder="全部设备"
                     style="width: 180px">
            <el-option v-for="d in deviceOptions" :key="d.deviceId"
                       :label="`${d.deviceId} ${d.name}`" :value="d.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务">
          <el-select v-model="filters.taskId" filterable clearable placeholder="全部任务"
                     style="width: 180px">
            <el-option v-for="t in taskOptions" :key="t.taskId"
                       :label="`${t.taskId} ${t.name}`" :value="t.taskId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ======= 文件列表 ======= -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="缩略图" width="80">
          <template #default="{ row }">
            <FileImage :file-id="row.fileId" :alt="row.fileName" :size="46" />
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column prop="deviceId" label="设备" width="140">
          <template #default="{ row }">{{ row.deviceId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务" width="110">
          <template #default="{ row }">{{ row.taskId || '—' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="hdfsPath" label="HDFS 路径" min-width="260" show-overflow-tooltip />
        <el-table-column label="上传时间" width="165">
          <template #default="{ row }">{{ formatTime(row.uploadTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onPreview(row)">预览</el-button>
            <el-button link type="success" size="small" @click="onDownload(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadList"
          @size-change="loadList"
        />
      </div>
    </el-card>

    <!-- ======= 大图预览 ======= -->
    <el-dialog v-model="previewVisible" :title="previewRow?.fileName" width="720px" align-center>
      <div v-if="previewRow" class="preview-box">
        <FileImage :key="previewRow.fileId" :file-id="previewRow.fileId"
                   :alt="previewRow.fileName" :size="640" />
        <div class="preview-meta">
          <div>HDFS: {{ previewRow.hdfsPath }}</div>
          <div>{{ formatSize(previewRow.fileSize) }} · {{ formatTime(previewRow.uploadTime) }}</div>
          <el-button type="success" :icon="Download" @click="onDownload(previewRow)">下载原图</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onActivated, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Upload, Download } from '@element-plus/icons-vue'
import FileImage from '@/components/FileImage.vue'
import { deviceApi, fileApi, taskApi } from '@/api'
import { formatTime, formatSize } from '@/utils/format'

const MAX_SIZE = 20 * 1024 * 1024

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const filters = reactive({ deviceId: '', taskId: '' })

const deviceOptions = ref([])
const taskOptions = ref([])
const uploadDevice = ref('')
const uploadTask = ref('')

async function loadDevices() {
  if (deviceOptions.value.length) return
  const data = await deviceApi.list({ size: 100 })
  deviceOptions.value = data.list || []
}
async function loadTasks() {
  if (taskOptions.value.length) return
  const data = await taskApi.list({ size: 100 })
  taskOptions.value = data.list || []
}

async function loadList() {
  loading.value = true
  try {
    const data = await fileApi.list({
      page: page.value,
      size: size.value,
      deviceId: filters.deviceId || undefined,
      taskId: filters.taskId || undefined
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
  filters.deviceId = ''
  filters.taskId = ''
  page.value = 1
  loadList()
}

/* ---------- 上传 ---------- */
async function onFileChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.error('仅支持 jpg/jpeg/png/webp 图片')
    return
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('图片不能超过 20MB')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认上传 ${file.name} (${formatSize(file.size)}) 吗?`,
      '上传确认',
      { confirmButtonText: '上传', cancelButtonText: '取消', type: 'info' }
    ).catch(() => Promise.reject())
  } catch {
    return
  }
  const meta = await fileApi.upload(file, uploadDevice.value || undefined, uploadTask.value || undefined)
  ElMessage.success(`上传成功: ${meta.fileId}`)
  uploadDevice.value = ''
  uploadTask.value = ''
  onSearch()
}

/* ---------- 预览 / 下载 ---------- */
const previewVisible = ref(false)
const previewRow = ref(null)
function onPreview(row) {
  previewRow.value = row
  previewVisible.value = true
}

async function onDownload(row) {
  const blob = await fileApi.download(row.fileId)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = row.fileName || `${row.fileId}.jpg`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

onActivated(() => {
  loadDevices()
  loadTasks()
  loadList()
})
</script>

<style scoped>
.upload-tip {
  font-size: 12px;
  color: #aab3c2;
  margin-top: 4px;
}
.preview-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.preview-box :deep(.file-image) {
  width: 100% !important;
  height: auto !important;
  max-height: 480px;
  background: #f4f6fa;
}
.preview-box img {
  object-fit: contain;
  max-height: 480px;
}
.preview-meta {
  width: 100%;
  font-size: 12px;
  color: #8a93a4;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
</style>
