import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ConversationBrief, ConversationDetail } from '@/types'
import { conversationApi } from '@/api'

export const useConversationsStore = defineStore('conversations', () => {
  const items = ref<ConversationBrief[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const currentDetail = ref<ConversationDetail | null>(null)

  async function fetchList(p = 1, q?: string) {
    loading.value = true
    try {
      const { data } = await conversationApi.list(p, pageSize.value, q)
      items.value = data.items
      total.value = data.total
      page.value = data.page
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: string) {
    loading.value = true
    try {
      const { data } = await conversationApi.get(id)
      currentDetail.value = data
    } finally {
      loading.value = false
    }
  }

  async function deleteConversation(id: string) {
    await conversationApi.delete(id)
    items.value = items.value.filter((c) => c.id !== id)
    total.value = Math.max(0, total.value - 1)
  }

  async function updateTitle(id: string, title: string) {
    const { data } = await conversationApi.update(id, title)
    const idx = items.value.findIndex((c) => c.id === id)
    if (idx !== -1) items.value[idx] = data
    if (currentDetail.value?.id === id) {
      currentDetail.value = { ...currentDetail.value, ...data }
    }
  }

  async function regenerateSummary(id: string) {
    const { data } = await conversationApi.generateSummary(id)
    const idx = items.value.findIndex((c) => c.id === id)
    if (idx !== -1) items.value[idx] = data
    if (currentDetail.value?.id === id) {
      currentDetail.value = { ...currentDetail.value, ...data }
    }
  }

  return {
    items, total, page, pageSize, loading, currentDetail,
    fetchList, fetchDetail, deleteConversation, updateTitle, regenerateSummary,
  }
})
