<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConversationsStore } from '@/stores/conversations'
import ChatBubble from '@/components/ChatBubble.vue'
import SummaryCard from '@/components/SummaryCard.vue'

const route = useRoute()
const router = useRouter()
const store = useConversationsStore()

const editingTitle = ref(false)
const titleDraft = ref('')
const summaryLoading = ref(false)

const convId = computed(() => route.params.id as string)

onMounted(() => {
  store.fetchDetail(convId.value)
})

const firstParticipant = computed(() => {
  return store.currentDetail?.participants?.[0] ?? ''
})

function startEditTitle() {
  titleDraft.value = store.currentDetail?.title || ''
  editingTitle.value = true
}

async function saveTitle() {
  if (!titleDraft.value.trim()) return
  await store.updateTitle(convId.value, titleDraft.value.trim())
  editingTitle.value = false
}

function cancelEdit() {
  editingTitle.value = false
}

async function regenerateSummary() {
  summaryLoading.value = true
  try {
    await store.regenerateSummary(convId.value)
  } finally {
    summaryLoading.value = false
  }
}

async function deleteConv() {
  if (!confirm('确定要删除此会话吗？')) return
  await store.deleteConversation(convId.value)
  router.push('/')
}
</script>

<template>
  <div class="detail-view">
    <div class="detail-toolbar">
      <button class="btn-back" @click="router.push('/')">返回列表</button>
      <div class="toolbar-actions">
        <button class="btn-summary" :disabled="summaryLoading" @click="regenerateSummary">
          {{ summaryLoading ? '生成中...' : '生成摘要' }}
        </button>
        <button class="btn-delete" @click="deleteConv">删除会话</button>
      </div>
    </div>

    <div v-if="store.loading" class="loading">加载中...</div>

    <template v-else-if="store.currentDetail">
      <div class="title-section">
        <template v-if="!editingTitle">
          <h2 class="conv-title">{{ store.currentDetail.title || '未命名会话' }}</h2>
          <button class="btn-edit-title" @click="startEditTitle">编辑标题</button>
        </template>
        <template v-else>
          <input v-model="titleDraft" class="title-input" @keyup.enter="saveTitle" />
          <button class="btn-save" @click="saveTitle">保存</button>
          <button class="btn-cancel" @click="cancelEdit">取消</button>
        </template>
      </div>

      <div class="conv-meta-bar">
        <span>参与者: {{ store.currentDetail.participants.join(', ') }}</span>
        <span>{{ store.currentDetail.message_count }} 条消息</span>
      </div>

      <SummaryCard
        :summary="store.currentDetail.summary"
        :title="store.currentDetail.title"
      />

      <div class="messages-container">
        <ChatBubble
          v-for="msg in store.currentDetail.messages"
          :key="msg.id"
          :sender="msg.sender"
          :content="msg.content"
          :timestamp="msg.timestamp"
          :is-right="msg.sender === firstParticipant"
          :msg-type="msg.msg_type"
          :image-url="msg.image_url"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-view { padding-bottom: 40px; }
.detail-toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.toolbar-actions { display: flex; gap: 8px; }
.btn-back { background: #f0f0f0; color: #333; }
.btn-summary { background: #07c160; color: #fff; }
.btn-delete { background: #ff4d4f; color: #fff; }
.btn-edit-title { background: #f0f0f0; color: #333; font-size: 12px; padding: 4px 10px; }
.btn-save { background: #07c160; color: #fff; font-size: 12px; padding: 4px 10px; }
.btn-cancel { background: #f0f0f0; color: #333; font-size: 12px; padding: 4px 10px; }
.loading { text-align: center; padding: 40px 0; color: #999; }
.title-section {
  display: flex; align-items: center; gap: 10px; margin-bottom: 8px;
}
.conv-title { font-size: 22px; font-weight: 600; }
.title-input { flex: 1; height: 36px; font-size: 16px; }
.conv-meta-bar {
  font-size: 13px; color: #888; display: flex; gap: 16px; margin-bottom: 16px;
}
.messages-container {
  background: #ebebeb;
  border-radius: 12px;
  padding: 20px 16px;
  min-height: 200px;
}
</style>
