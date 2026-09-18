<template>
  <div class="device-map" @mousemove="onMouseMove" @mouseleave="hovered = null">
    <!-- 图例 -->
    <div class="map-legend">
      <span><i class="dot dot-online"></i>在线</span>
      <span><i class="dot dot-offline"></i>离线</span>
      <span><i class="dot dot-fault"></i>故障</span>
      <span class="legend-split"></span>
      <span><i class="shape shape-uav"></i>无人机</span>
      <span><i class="shape shape-dog"></i>机器狗</span>
    </div>

    <svg :viewBox="`0 0 ${W} ${H}`" class="map-svg" preserveAspectRatio="xMidYMid meet">
      <defs>
        <pattern :id="patternId" width="40" height="40" patternUnits="userSpaceOnUse">
          <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#dfe7f2" stroke-width="0.6" />
        </pattern>
        <radialGradient id="mapBg" cx="50%" cy="45%" r="75%">
          <stop offset="0%" stop-color="#f7faff" />
          <stop offset="100%" stop-color="#eef3fb" />
        </radialGradient>
      </defs>

      <rect x="0" y="0" :width="W" :height="H" fill="url(#mapBg)" />
      <rect x="0" y="0" :width="W" :height="H" :fill="`url(#${patternId})`" />

      <!-- 装饰: 场站围墙 -->
      <rect x="24" y="24" :width="W - 48" :height="H - 48" rx="10"
            fill="none" stroke="#c4d2e8" stroke-width="1.4" stroke-dasharray="6 5" />
      <text :x="34" :y="46" class="map-note">电力场站巡检区域(设备坐标平面图)</text>

      <!-- 区域底块(按 area 聚合) -->
      <g v-for="zone in zones" :key="zone.area">
        <rect :x="zone.x - 58" :y="zone.y - 44" width="116" height="80" rx="8"
              fill="#ffffff" fill-opacity="0.55" stroke="#cdd8ea" stroke-width="1" />
        <text :x="zone.x" :y="zone.y - 50" class="zone-label" text-anchor="middle">
          {{ zone.area }}
        </text>
      </g>

      <!-- 设备点位 -->
      <g v-for="d in locatedDevices" :key="d.deviceId"
         :transform="`translate(${project(d.position).x}, ${project(d.position).y})`"
         class="marker" :class="{ 'marker-offline': d.status === 'OFFLINE' }"
         @click="$emit('select', d)"
         @mouseenter="hovered = d">
        <!-- 选中环 -->
        <circle v-if="selectedId === d.deviceId" r="17" fill="none"
                stroke="#1769ff" stroke-width="2" stroke-dasharray="3 3" />
        <!-- 在线光晕 -->
        <circle v-if="d.status === 'ONLINE' && !d.faultCode" r="12"
                fill="#18b26b" opacity="0.16" />

        <!-- 无人机: 菱形; 机器狗: 圆形 -->
        <polygon v-if="d.deviceType === 'UAV'" points="0,-9 8,0 0,9 -8,0"
                 :fill="markerColor(d)" stroke="#fff" stroke-width="1.5" />
        <circle v-else r="8" :fill="markerColor(d)" stroke="#fff" stroke-width="1.5" />
        <text y="24" text-anchor="middle" class="marker-label">{{ d.name || d.deviceId }}</text>
      </g>


      <text v-if="!locatedDevices.length" :x="W / 2" :y="H / 2" text-anchor="middle"
            class="map-empty">暂无带坐标的设备数据</text>
    </svg>

    <!-- 悬浮信息卡 -->
    <div v-if="hovered" class="map-tip" :style="{ left: tipX + 'px', top: tipY + 'px' }">
      <div class="tip-title">
        {{ hovered.name }}
        <el-tag size="small" :type="hovered.status === 'ONLINE' ? 'success' : 'info'">
          {{ hovered.status === 'ONLINE' ? '在线' : '离线' }}
        </el-tag>
      </div>
      <div class="tip-row"><span>编号</span><b>{{ hovered.deviceId }}</b></div>
      <div class="tip-row"><span>类型</span><b>{{ hovered.deviceType === 'UAV' ? '无人机' : '机器狗' }}</b></div>
      <div class="tip-row"><span>电量</span><b>{{ hovered.battery ?? '—' }}%</b></div>
      <div v-if="hovered.faultCode" class="tip-row">
        <span>故障码</span><b style="color:#ff8f8f">{{ hovered.faultCode }}</b>
      </div>
    </div>

    <div v-if="unlocatedCount" class="map-unlocated">
      ⚠ {{ unlocatedCount }} 台设备无坐标, 未在图中显示
    </div>
  </div>
</template>

<script setup>
/**
 * 设备 GIS 平面图(FR6-5)。
 * 仿真环境无外网地图/Key, 采用自包含 SVG: 按设备经纬度归一化投影到站场坐标,
 * 形状区分类型(菱形=无人机, 圆=机器狗), 颜色区分状态(绿=在线/灰=离线/红=故障)。
 */
import { computed, ref } from 'vue'

const W = 900
const H = 460
const PAD = 70
let uid = 0
const patternId = `map-grid-${++uid}`

const props = defineProps({
  devices: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' }
})
defineEmits(['select'])

const hovered = ref(null)
const tipX = ref(0)
const tipY = ref(0)

const hasPos = (d) =>
  d.position &&
  typeof d.position.lng === 'number' &&
  typeof d.position.lat === 'number' &&
  !(d.position.lng === 0 && d.position.lat === 0)

const locatedDevices = computed(() => props.devices.filter(hasPos))
const unlocatedCount = computed(() => props.devices.length - locatedDevices.value.length)

const bounds = computed(() => {
  const list = locatedDevices.value
  if (!list.length) return { minLng: 116.39, maxLng: 116.41, minLat: 39.905, maxLat: 39.915 }
  let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity
  for (const d of list) {
    const { lng, lat } = d.position
    minLng = Math.min(minLng, lng)
    maxLng = Math.max(maxLng, lng)
    minLat = Math.min(minLat, lat)
    maxLat = Math.max(maxLat, lat)
  }
  // 单点时给默认跨度
  if (minLng === maxLng) { minLng -= 0.004; maxLng += 0.004 }
  if (minLat === maxLat) { minLat -= 0.003; maxLat += 0.003 }
  return { minLng, maxLng, minLat, maxLat }
})

function project(position) {
  const b = bounds.value
  const rx = (position.lng - b.minLng) / (b.maxLng - b.minLng)
  const ry = (position.lat - b.minLat) / (b.maxLat - b.minLat)
  return {
    x: PAD + rx * (W - PAD * 2),
    y: H - PAD - ry * (H - PAD * 2) // 纬度越大(北)越靠上
  }
}

// 区域底块位置: 该区域设备点位质心
const zones = computed(() => {
  const map = new Map()
  for (const d of locatedDevices.value) {
    const key = d.area || '未分区'
    if (!map.has(key)) map.set(key, { area: key, sumX: 0, sumY: 0, n: 0 })
    const z = map.get(key)
    const p = project(d.position)
    z.sumX += p.x
    z.sumY += p.y
    z.n++
  }
  return [...map.values()].map((z) => ({ area: z.area, x: z.sumX / z.n, y: z.sumY / z.n }))
})

function markerColor(d) {
  if (d.faultCode) return '#f03d3d'
  return d.status === 'ONLINE' ? '#18b26b' : '#94a3b8'
}

function onMouseMove(e) {
  if (!hovered.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  tipX.value = e.clientX - rect.left
  tipY.value = e.clientY - rect.top
}
</script>

<style scoped>
.device-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
}
.map-svg {
  width: 100%;
  height: 100%;
  display: block;
}
.marker {
  cursor: pointer;
}
.marker-label {
  font-size: 10px;
  fill: #44526a;
  paint-order: stroke;
  stroke: #f7faff;
  stroke-width: 3px;
  pointer-events: none;
}
.map-note,
.zone-label {
  font-size: 11px;
  fill: #7a869c;
}
.map-empty {
  font-size: 15px;
  fill: #a3adbf;
}
.map-legend {
  position: absolute;
  top: 10px;
  right: 14px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e2e8f2;
  border-radius: 6px;
  font-size: 12px;
  color: #44526a;
  box-shadow: 0 2px 8px rgba(16, 27, 51, 0.06);
}
.dot {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-right: 4px;
}
.dot-online { background: #18b26b; }
.dot-offline { background: #94a3b8; }
.dot-fault { background: #f03d3d; }
.legend-split {
  width: 1px;
  height: 14px;
  background: #dbe2ee;
}
.shape {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 4px;
}
.shape-uav {
  background: #44526a;
  transform: rotate(45deg);
  border-radius: 2px;
}
.shape-dog {
  background: #44526a;
  border-radius: 50%;
}
.map-tip {
  position: absolute;
  z-index: 5;
  pointer-events: none;
  min-width: 168px;
  padding: 8px 12px;
  background: rgba(20, 32, 56, 0.94);
  color: #eef3fb;
  border-radius: 8px;
  font-size: 12px;
  box-shadow: 0 6px 18px rgba(16, 27, 51, 0.3);
  transform: translate(12px, 12px);
}
.tip-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.tip-row {
  display: flex;
  justify-content: space-between;
  line-height: 1.8;
}
.tip-row span {
  color: #9fb0cc;
}
.map-unlocated {
  position: absolute;
  left: 14px;
  bottom: 10px;
  font-size: 12px;
  color: #b26a00;
  background: rgba(255, 247, 230, 0.95);
  border: 1px solid #f5dab1;
  padding: 3px 10px;
  border-radius: 6px;
}
</style>
