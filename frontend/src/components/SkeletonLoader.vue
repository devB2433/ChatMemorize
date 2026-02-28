<script setup lang="ts">
defineProps<{
  type: 'list' | 'detail'
}>()
</script>

<template>
  <div class="skeleton-wrapper">
    <!-- List mode: multiple card skeletons -->
    <template v-if="type === 'list'">
      <div v-for="i in 4" :key="i" class="skeleton-card">
        <div class="skeleton-line title shimmer" />
        <div class="skeleton-line meta shimmer" />
        <div class="skeleton-line summary shimmer" />
      </div>
    </template>

    <!-- Detail mode: title + meta + message bubbles -->
    <template v-else>
      <div class="skeleton-line detail-title shimmer" />
      <div class="skeleton-line detail-meta shimmer" />
      <div class="skeleton-messages">
        <div v-for="i in 6" :key="i" :class="['skeleton-bubble', 'shimmer', i % 3 === 0 ? 'right' : 'left']" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.skeleton-wrapper { padding: 8px 0; }

.skeleton-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 12px;
  border: 1px solid #eee;
}

.skeleton-line {
  border-radius: 4px;
  background: #e8e8e8;
}
.skeleton-line.title { width: 50%; height: 20px; margin-bottom: 10px; }
.skeleton-line.meta { width: 70%; height: 14px; margin-bottom: 8px; }
.skeleton-line.summary { width: 90%; height: 14px; }

.skeleton-line.detail-title { width: 40%; height: 26px; margin-bottom: 12px; }
.skeleton-line.detail-meta { width: 55%; height: 14px; margin-bottom: 20px; }

.skeleton-messages {
  background: #ebebeb;
  border-radius: 12px;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.skeleton-bubble {
  height: 36px;
  border-radius: 12px;
  background: #ddd;
}
.skeleton-bubble.left { width: 60%; align-self: flex-start; }
.skeleton-bubble.right { width: 50%; align-self: flex-end; }

.shimmer {
  background: linear-gradient(90deg, #e8e8e8 25%, #f5f5f5 50%, #e8e8e8 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
