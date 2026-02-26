<script setup lang="ts">
defineProps<{
  summary: string | null
  title: string | null
}>()
</script>

<template>
  <div v-if="summary" class="summary-card">
    <div class="summary-header">
      <span class="summary-label">摘要</span>
      <span v-if="title" class="summary-title">{{ title }}</span>
    </div>
    <p class="summary-text">{{ summary }}</p>
  </div>
</template>

<style scoped>
.summary-card {
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 20px;
}
.summary-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.summary-label {
  background: #faad14;
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.summary-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.summary-text {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}
</style>
