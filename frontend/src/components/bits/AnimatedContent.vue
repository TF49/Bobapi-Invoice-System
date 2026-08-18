<template>
  <component
    :is="tag"
    ref="root"
    class="animated-content"
    :class="{ 'is-visible': isVisible }"
    :style="contentStyle"
  >
    <slot />
  </component>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  tag?: string
  delay?: number
  distance?: number
}>(), {
  tag: 'div',
  delay: 0,
  distance: 14
})

const root = ref<HTMLElement | null>(null)
const isVisible = ref(false)
let observer: IntersectionObserver | null = null

const contentStyle = computed(() => ({
  '--content-delay': `${props.delay}ms`,
  '--content-distance': `${props.distance}px`
}))

onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) {
    isVisible.value = true
    return
  }

  observer = new IntersectionObserver(([entry]) => {
    if (!entry.isIntersecting) return
    isVisible.value = true
    observer?.disconnect()
  }, { threshold: 0.08 })

  if (root.value) observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
.animated-content {
  opacity: 0;
  transform: translateY(var(--content-distance));
  transition:
    opacity 420ms ease var(--content-delay),
    transform 420ms ease var(--content-delay);
}

.animated-content.is-visible {
  opacity: 1;
  transform: translateY(0);
}

@media (prefers-reduced-motion: reduce) {
  .animated-content {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>

