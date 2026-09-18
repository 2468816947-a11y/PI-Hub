<template>
  <div ref="chartRef" class="echart-box" :style="{ height: height }"></div>
</template>

<script setup>
/**
 * ECharts 轻封装: 传入 option 即可渲染, 自动响应容器尺寸变化。
 */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '300px' }
})

const chartRef = ref(null)
const chart = shallowRef(null)
let resizeObserver = null

function render() {
  if (chart.value && props.option) {
    chart.value.setOption(props.option, true)
  }
}

onMounted(() => {
  chart.value = echarts.init(chartRef.value)
  render()
  resizeObserver = new ResizeObserver(() => chart.value?.resize())
  resizeObserver.observe(chartRef.value)
})

watch(
  () => props.option,
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart.value?.dispose()
})
</script>

<style scoped>
.echart-box {
  width: 100%;
}
</style>
