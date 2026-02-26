<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  placeholder?: string
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
}>()

const query = ref(props.modelValue ?? '')

function onSubmit() {
  const val = query.value.trim()
  emit('update:modelValue', val)
  emit('search', val)
}
</script>

<template>
  <form class="search-bar" @submit.prevent="onSubmit">
    <input
      v-model="query"
      :placeholder="placeholder || '输入关键词搜索...'"
      class="search-input"
    />
    <button type="submit" class="search-btn">搜索</button>
  </form>
</template>

<style scoped>
.search-bar {
  display: flex;
  gap: 8px;
}
.search-input {
  flex: 1;
  height: 40px;
}
.search-btn {
  background: #07c160;
  color: #fff;
  height: 40px;
  padding: 0 24px;
  font-size: 15px;
}
</style>
