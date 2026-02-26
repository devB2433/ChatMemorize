import axios from 'axios'
import type {
  ConversationBrief,
  ConversationDetail,
  PaginatedConversations,
  SearchResponse,
  AskResponse,
} from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach Bearer token from localStorage
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wechatmem_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Redirect to /login on 401/403
http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      const path = window.location.pathname
      if (path !== '/login') {
        localStorage.removeItem('wechatmem_token')
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  },
)

export const conversationApi = {
  list(page = 1, pageSize = 20, q?: string) {
    return http.get<PaginatedConversations>('/conversations', {
      params: { page, page_size: pageSize, q: q || undefined },
    })
  },
  get(id: string) {
    return http.get<ConversationDetail>(`/conversations/${id}`)
  },
  create(text: string, title?: string) {
    return http.post<ConversationBrief>('/conversations', { text, title })
  },
  update(id: string, title: string) {
    return http.patch<ConversationBrief>(`/conversations/${id}`, { title })
  },
  delete(id: string) {
    return http.delete(`/conversations/${id}`)
  },
  generateSummary(id: string) {
    return http.post<ConversationBrief>(`/conversations/${id}/summary`)
  },
}

export const searchApi = {
  search(query: string, topK?: number, conversationId?: string) {
    return http.post<SearchResponse>('/search', {
      query,
      top_k: topK,
      conversation_id: conversationId || undefined,
    })
  },
  ask(question: string, conversationId?: string, topK?: number) {
    return http.post<AskResponse>('/search/ask', {
      question,
      conversation_id: conversationId || undefined,
      top_k: topK,
    })
  },
}

export default http
