import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import http from '@/api'
import type { TokenResponse, UserOut } from '@/types'

const TOKEN_KEY = 'wechatmem_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserOut | null>(null)
  const isLoggedIn = computed(() => !!token.value)

  function setToken(t: string) {
    token.value = t
    localStorage.setItem(TOKEN_KEY, t)
  }

  function clearToken() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  async function register(username: string, password: string) {
    const { data } = await http.post<TokenResponse>('/auth/register', { username, password })
    setToken(data.token)
  }

  async function login(username: string, password: string) {
    const { data } = await http.post<TokenResponse>('/auth/login', { username, password })
    setToken(data.token)
  }

  async function fetchMe() {
    const { data } = await http.get<UserOut>('/auth/me')
    user.value = data
  }

  async function refresh() {
    const { data } = await http.post<TokenResponse>('/auth/refresh')
    setToken(data.token)
  }

  function logout() {
    clearToken()
  }

  return { token, user, isLoggedIn, register, login, fetchMe, refresh, logout }
})
