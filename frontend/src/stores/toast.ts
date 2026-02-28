import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

let nextId = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])

  function add(type: Toast['type'], message: string, duration = 3000) {
    const id = nextId++
    toasts.value.push({ id, type, message })
    setTimeout(() => remove(id), duration)
  }

  function remove(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function success(message: string) { add('success', message) }
  function error(message: string) { add('error', message, 5000) }
  function info(message: string) { add('info', message) }

  return { toasts, add, remove, success, error, info }
})
