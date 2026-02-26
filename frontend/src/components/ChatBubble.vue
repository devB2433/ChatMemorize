<script setup lang="ts">
const props = defineProps<{
  sender: string
  content: string
  timestamp: string | null
  isRight: boolean
  msgType?: string
  imageUrl?: string | null
}>()

function openImage() {
  if (props.imageUrl) window.open(props.imageUrl, '_blank')
}
</script>

<template>
  <div class="bubble-row" :class="{ right: isRight }">
    <div class="bubble-sender">{{ sender }}</div>
    <div class="bubble" :class="{ 'bubble-right': isRight, 'bubble-left': !isRight }">
      <img
        v-if="msgType === 'image' && imageUrl"
        :src="imageUrl"
        class="bubble-image"
        loading="lazy"
        @click="openImage"
      />
      <div v-else-if="msgType === 'image'" class="bubble-placeholder">[图片]</div>
      <div v-else-if="msgType === 'media'" class="bubble-placeholder">{{ content }}</div>
      <div v-else class="bubble-content">{{ content }}</div>
      <div v-if="timestamp" class="bubble-time">{{ timestamp }}</div>
    </div>
  </div>
</template>

<style scoped>
.bubble-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 12px;
}
.bubble-row.right {
  align-items: flex-end;
}
.bubble-sender {
  font-size: 12px;
  color: #999;
  margin-bottom: 2px;
  padding: 0 8px;
}
.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  word-break: break-word;
}
.bubble-left {
  background: #fff;
  border-top-left-radius: 2px;
}
.bubble-right {
  background: #95ec69;
  border-top-right-radius: 2px;
}
.bubble-content { font-size: 15px; line-height: 1.5; }
.bubble-image {
  max-width: 200px;
  max-height: 300px;
  border-radius: 8px;
  cursor: pointer;
}
.bubble-placeholder {
  font-size: 14px;
  color: #888;
  font-style: italic;
}
.bubble-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  text-align: right;
}
</style>
