<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const isLogin = ref(true)
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    if (isLogin.value) {
      await auth.login(username.value, password.value)
    } else {
      await auth.register(username.value, password.value)
    }
    router.push('/')
  } catch (e: any) {
    error.value = e.response?.data?.detail || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h2>{{ isLogin ? '登录' : '注册' }}</h2>
      <form @submit.prevent="handleSubmit">
        <input
          v-model="username"
          type="text"
          placeholder="用户名"
          autocomplete="username"
          required
          minlength="2"
          maxlength="32"
        />
        <input
          v-model="password"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
          required
          minlength="6"
        />
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '请稍候...' : isLogin ? '登录' : '注册' }}
        </button>
      </form>
      <p class="toggle" @click="isLogin = !isLogin; error = ''">
        {{ isLogin ? '没有账号？去注册' : '已有账号？去登录' }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 80px);
}
.login-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px 32px;
  width: 360px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.login-card h2 {
  text-align: center;
  margin-bottom: 24px;
  color: #07c160;
}
form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
form input {
  width: 100%;
}
form button {
  background: #07c160;
  color: #fff;
  font-size: 16px;
  padding: 10px;
  margin-top: 4px;
}
form button:disabled {
  opacity: 0.6;
}
.error {
  color: #e74c3c;
  font-size: 13px;
  text-align: center;
}
.toggle {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: #07c160;
  cursor: pointer;
}
.toggle:hover {
  text-decoration: underline;
}
</style>
