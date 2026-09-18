<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-grid"></div>
      <div class="bg-glow glow-1"></div>
      <div class="bg-glow glow-2"></div>
    </div>

    <div class="login-card">
      <div class="brand">
        <el-icon class="brand-icon"><Lightning /></el-icon>
        <h1>电力场站设备巡检数据集成平台</h1>
        <p>无人机 · 机器狗 空地协同巡检</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
      </el-form>

      <div class="login-tip">
        <el-icon><InfoFilled /></el-icon>
        开发期内置账号: <b>admin / admin123</b>
      </div>
      <div class="login-foot">Vue3 + Element Plus + ECharts ｜ Spring Boot 3.2 + Kafka + MongoDB + HDFS + ES</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  await formRef.value.validate().catch(() => null)
  if (!form.username || !form.password) return
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect ? String(route.query.redirect) : '/dashboard')
  } catch {
    // 错误提示已在响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0a1730 0%, #102a52 55%, #0d3b6e 100%);
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
}
.bg-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 42px 42px;
}
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}
.glow-1 {
  width: 420px;
  height: 420px;
  background: #1d6cff;
  top: -120px;
  left: -80px;
}
.glow-2 {
  width: 380px;
  height: 380px;
  background: #0bbf8e;
  bottom: -140px;
  right: -60px;
  opacity: 0.3;
}

.login-card {
  position: relative;
  z-index: 2;
  width: 400px;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 14px;
  padding: 38px 36px 26px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.4);
}
.brand {
  text-align: center;
  margin-bottom: 28px;
}
.brand-icon {
  font-size: 42px;
  color: #f5b800;
  margin-bottom: 8px;
}
.brand h1 {
  font-size: 19px;
  color: #13294b;
  line-height: 1.4;
}
.brand p {
  margin-top: 6px;
  font-size: 13px;
  color: #8a93a4;
  letter-spacing: 2px;
}
.login-btn {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 6px;
  font-weight: 600;
}
.login-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #8a93a4;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.login-tip b {
  color: #1769ff;
}
.login-foot {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #e6ebf3;
  font-size: 11px;
  color: #aab3c2;
  text-align: center;
}
</style>
