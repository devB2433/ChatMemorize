<script setup lang="ts">
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
</script>

<template>
  <Teleport to="body">
    <div class="toast-container" aria-live="polite">
      <TransitionGroup name="toast">
        <div
          v-for="t in toast.toasts"
          :key="t.id"
          :class="['toast-item', `toast-${t.type}`]"
          role="alert"
          @click="toast.remove(t.id)"
        >
          {{ t.message }}
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 360px;
}
.toast-item {
  padding: 12px 20px;
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.toast-success { background: #07c160; }
.toast-error { background: #ff4d4f; }
.toast-info { background: #1890ff; }

.toast-enter-active { transition: all 0.3s ease; }
.toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from { opacity: 0; transform: translateX(80px); }
.toast-leave-to { opacity: 0; transform: translateX(80px); }
</style>
