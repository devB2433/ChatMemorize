<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchApi } from '@/api'
import type { SearchResult } from '@/types'
import SearchBar from '@/components/SearchBar.vue'

const router = useRouter()
const activeTab = ref<'search' | 'ask'>('search')

const searchResults = ref<SearchResult[]>([])
const searchLoading = ref(false)
const searchQuery = ref('')

const askQuestion = ref('')
const askAnswer = ref('')
const askSources = ref<SearchResult[]>([])
const askLoading = ref(false)

async function onSearch(q: string) {
  if (!q.trim()) return
  searchQuery.value = q
  searchLoading.value = true
  try {
    const { data } = await searchApi.search(q)
    searchResults.value = data.results
  } finally {
    searchLoading.value = false
  }
}

async function onAsk() {
  const q = askQuestion.value.trim()
  if (!q) return
  askLoading.value = true
  askAnswer.value = ''
  askSources.value = []
  try {
    const { data } = await searchApi.ask(q)
    askAnswer.value = data.answer
    askSources.value = data.sources
  } finally {
    askLoading.value = false
  }
}

function goToConversation(id: string) {
  router.push(`/conversations/${id}`)
}

function highlightContent(content: string) {
  if (!searchQuery.value) return content
  const escaped = searchQuery.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return content.replace(
    new RegExp(`(${escaped})`, 'gi'),
    '<mark>$1</mark>',
  )
}
</script>

<template>
  <div class="search-view">
    <h2 class="page-title">搜索</h2>

    <div class="tab-bar">
      <button
        :class="['tab-btn', { active: activeTab === 'search' }]"
        @click="activeTab = 'search'"
      >语义搜索</button>
      <button
        :class="['tab-btn', { active: activeTab === 'ask' }]"
        @click="activeTab = 'ask'"
      >AI 问答</button>
    </div>

    <div v-if="activeTab === 'search'" class="tab-panel">
      <SearchBar placeholder="输入语义搜索内容..." @search="onSearch" />
      <div v-if="searchLoading" class="loading">搜索中...</div>
      <div v-else-if="searchResults.length === 0 && searchQuery" class="empty">未找到相关结果</div>
      <ul v-else class="result-list">
        <li
          v-for="r in searchResults"
          :key="r.message_id"
          class="result-item"
          @click="goToConversation(r.conversation_id)"
        >
          <div class="result-header">
            <span class="result-sender">{{ r.sender }}</span>
            <span v-if="r.timestamp" class="result-time">{{ r.timestamp }}</span>
            <span class="result-score">相关度: {{ (r.score * 100).toFixed(1) }}%</span>
          </div>
          <p class="result-content" v-html="highlightContent(r.content)"></p>
        </li>
      </ul>
    </div>

    <div v-if="activeTab === 'ask'" class="tab-panel">
      <form class="ask-form" @submit.prevent="onAsk">
        <textarea
          v-model="askQuestion"
          rows="3"
          placeholder="输入你的问题，AI 将基于聊天记录回答..."
          class="ask-input"
        ></textarea>
        <button type="submit" class="ask-btn" :disabled="askLoading">
          {{ askLoading ? '思考中...' : '提问' }}
        </button>
      </form>

      <div v-if="askAnswer" class="ask-answer">
        <div class="answer-label">AI 回答</div>
        <p class="answer-text">{{ askAnswer }}</p>
      </div>

      <div v-if="askSources.length > 0" class="ask-sources">
        <div class="sources-label">参考来源</div>
        <ul class="result-list">
          <li
            v-for="s in askSources"
            :key="s.message_id"
            class="result-item"
            @click="goToConversation(s.conversation_id)"
          >
            <div class="result-header">
              <span class="result-sender">{{ s.sender }}</span>
              <span class="result-score">相关度: {{ (s.score * 100).toFixed(1) }}%</span>
            </div>
            <p class="result-content">{{ s.content }}</p>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title { font-size: 22px; font-weight: 600; margin-bottom: 16px; }
.tab-bar { display: flex; gap: 0; margin-bottom: 16px; }
.tab-btn {
  flex: 1; padding: 10px; font-size: 15px; background: #f0f0f0; color: #666;
  border: 1px solid #ddd; border-bottom: 2px solid #ddd;
}
.tab-btn:first-child { border-radius: 8px 0 0 8px; }
.tab-btn:last-child { border-radius: 0 8px 8px 0; }
.tab-btn.active { background: #fff; color: #07c160; border-bottom-color: #07c160; font-weight: 600; }
.tab-panel { margin-top: 8px; }
.loading, .empty { text-align: center; padding: 40px 0; color: #999; font-size: 15px; }
.result-list { list-style: none; margin-top: 16px; }
.result-item {
  background: #fff; border-radius: 8px; padding: 14px 18px;
  margin-bottom: 10px; cursor: pointer; border: 1px solid #eee;
  transition: box-shadow 0.2s;
}
.result-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
.result-header { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.result-sender { font-weight: 600; font-size: 14px; }
.result-time { font-size: 12px; color: #999; }
.result-score { font-size: 12px; color: #07c160; margin-left: auto; }
.result-content { font-size: 14px; color: #444; line-height: 1.5; }
.result-content :deep(mark) { background: #ffe58f; padding: 0 2px; border-radius: 2px; }
.ask-form { display: flex; flex-direction: column; gap: 10px; }
.ask-input { width: 100%; resize: vertical; font-size: 15px; line-height: 1.5; }
.ask-btn {
  align-self: flex-end; background: #07c160; color: #fff;
  padding: 8px 28px; font-size: 15px;
}
.ask-btn:disabled { background: #ccc; cursor: not-allowed; }
.ask-answer {
  background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 8px;
  padding: 16px 20px; margin-top: 20px;
}
.answer-label {
  font-size: 13px; font-weight: 600; color: #52c41a; margin-bottom: 8px;
}
.answer-text { font-size: 15px; line-height: 1.7; color: #333; white-space: pre-wrap; }
.ask-sources { margin-top: 20px; }
.sources-label { font-size: 14px; font-weight: 600; color: #666; margin-bottom: 4px; }
</style>
