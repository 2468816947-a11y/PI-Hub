<template>
  <div class="file-image" :style="{ width: size + 'px', height: size + 'px' }">
    <img v-if="url" :src="url" :alt="alt" />
    <el-icon v-else-if="failed"><PictureFilled /></el-icon>
    <el-icon v-else class="loading-icon"><Loading /></el-icon>
  </div>
</template>

<script setup>
/**
 * 受保护影像展示: 下载接口需要 JWT, 无法直接 <img src>,
 * 因此用 axios 取 blob 再换 object URL, 卸载时释放。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fileApi } from '@/api'

const props = defineProps({
  fileId: { type: String, required: true },
  alt: { type: String, default: '' },
  size: { type: Number, default: 48 }
})

const url = ref('')
const failed = ref(false)

async function load() {
  if (url.value) {
    URL.revokeObjectURL(url.value)
    url.value = ''
  }
  failed.value = false
  try {
    const blob = await fileApi.download(props.fileId)
    url.value = URL.createObjectURL(blob)
  } catch {
    failed.value = true
  }
}

onMounted(load)
watch(() => props.fileId, load)
onBeforeUnmount(() => {
  if (url.value) URL.revokeObjectURL(url.value)
})
</script>

<style scoped>
.file-image {
  border-radius: 6px;
  overflow: hidden;
  background: #f4f6fa;
  border: 1px solid #e8edf5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.file-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.loading-icon {
  animation: rotating 1.4s linear infinite;
  color: #aab3c2;
}
.file-image > .el-icon {
  color: #c0c8d6;
  font-size: 18px;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
