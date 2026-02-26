<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useConversationsStore } from '@/stores/conversations'
import SearchBar from '@/components/SearchBar.vue'

const store = useConversationsStore()
const router = useRouter()
const keyword = ref('')

onMounted(() => {
  store.fetchList(1)
})

function onSearch(q: string) {
  keyword.value = q
  store.fetchList(1, q || undefined)
}

function goToPage(p: number) {
  store.fetchList(p, keyword.value || undefined)
}

function openDetail(id: string) {
  router.push(`/conversations/${id}`)
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

const totalPages = ref(0)
watch(
  () => [store.total, store.pageSize],
  () => { totalPages.value = Math.ceil(store.total / store.pageSize) },
  { immediate: true },
)
</script>

<template>
  <div class="conv-list-view">
    <h2 class="page-title">会话列表</h2>
    <SearchBar v-model="keyword" placeholder="搜索会话..." @search="onSearch" />

    <div v-if="store.loading" class="loading">加载中...</div>

    <div v-else-if="store.items.length === 0" class="empty">暂无会话记录</div>

    <ul v-else class="conv-list">
      <li
        v-for="conv in store.items"
        :key="conv.id"
        class="conv-item"
        @click="openDetail(conv.id)"
      >
        <div class="conv-header">
          <span class="conv-title">{{ conv.title || '未命名会话' }}</span>
          <span class="conv-date">{{ formatDate(conv.created_at) }}</span>
        </div>
        <div class="conv-meta">
          <span>参与者: {{ conv.participants.join(', ') }}</span>
          <span>{{ conv.message_count }} 条消息</span>
        </div>
        <p v-if="conv.summary" class="conv-summary">{{ conv.summary }}</p>
      </li>
    </ul>

    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="store.page <= 1" @click="goToPage(store.page - 1)">上一页</button>
      <span class="page-info">第 {{ store.page }} / {{ totalPages }} 页 (共 {{ store.total }} 条)</span>
      <button :disabled="store.page >= totalPages" @click="goToPage(store.page + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.page-title { font-size: 22px; font-weight: 600; margin-bottom: 16px; }
.loading, .empty { text-align: center; padding: 40px 0; color: #999; font-size: 15px; }
.conv-list { list-style: none; margin-top: 16px; }
.conv-item {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  border: 1px solid #eee;
}
.conv-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
.conv-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.conv-title { font-size: 16px; font-weight: 600; }
.conv-date { font-size: 12px; color: #999; }
.conv-meta { font-size: 13px; color: #888; display: flex; gap: 16px; margin-bottom: 6px; }
.conv-summary { font-size: 13px; color: #666; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pagination {
  display: flex; justify-content: center; align-items: center; gap: 16px; margin-top: 20px;
}
.pagination button {
  background: #07c160; color: #fff; padding: 6px 18px;
}
.pagination button:disabled { background: #ccc; cursor: not-allowed; }
.page-info { font-size: 14px; color: #666; }
</style>
