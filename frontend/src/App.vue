<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div id="app-root">
    <nav class="top-nav">
      <router-link to="/" class="nav-brand">WeChatMem</router-link>
      <div class="nav-links">
        <template v-if="auth.isLoggedIn">
          <router-link to="/">会话列表</router-link>
          <router-link to="/search">搜索</router-link>
          <span class="nav-user" @click="handleLogout">退出</span>
        </template>
        <template v-else>
          <router-link to="/login">登录</router-link>
        </template>
      </div>
    </nav>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  background: #f5f5f5;
  color: #333;
  line-height: 1.6;
}
a { text-decoration: none; color: inherit; }
button {
  cursor: pointer;
  border: none;
  border-radius: 6px;
  padding: 6px 16px;
  font-size: 14px;
  transition: opacity 0.2s;
}
button:hover { opacity: 0.85; }
input, textarea {
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}
input:focus, textarea:focus { border-color: #07c160; }
</style>

<style scoped>
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: #07c160;
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
}
.nav-brand { font-size: 20px; font-weight: 700; }
.nav-links { display: flex; gap: 20px; }
.nav-links a {
  font-size: 15px;
  padding: 4px 0;
  border-bottom: 2px solid transparent;
}
.nav-links a.router-link-exact-active {
  border-bottom-color: #fff;
}
.nav-user {
  font-size: 15px;
  cursor: pointer;
  opacity: 0.85;
}
.nav-user:hover { opacity: 1; text-decoration: underline; }
.main-content {
  max-width: 960px;
  margin: 24px auto;
  padding: 0 16px;
}
</style>
