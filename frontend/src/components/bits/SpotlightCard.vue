<template>
  <article
    ref="card"
    class="spotlight-card"
    @pointermove="handlePointerMove"
    @pointerleave="resetSpotlight"
  >
    <slot />
  </article>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const card = ref<HTMLElement | null>(null)

const handlePointerMove = (event: PointerEvent) => {
  if (!card.value || event.pointerType === 'touch') return
  const bounds = card.value.getBoundingClientRect()
  card.value.style.setProperty('--spotlight-x', `${event.clientX - bounds.left}px`)
  card.value.style.setProperty('--spotlight-y', `${event.clientY - bounds.top}px`)
  card.value.style.setProperty('--spotlight-opacity', '1')
}

const resetSpotlight = () => {
  card.value?.style.setProperty('--spotlight-opacity', '0')
}
</script>

<style scoped>
.spotlight-card {
  --spotlight-x: 50%;
  --spotlight-y: 50%;
  --spotlight-opacity: 0;
  position: relative;
  isolation: isolate;
  overflow: hidden;
}

.spotlight-card::before {
  position: absolute;
  z-index: -1;
  inset: 0;
  background: radial-gradient(
    260px circle at var(--spotlight-x) var(--spotlight-y),
    rgba(18, 113, 91, 0.1),
    transparent 68%
  );
  opacity: var(--spotlight-opacity);
  pointer-events: none;
  content: '';
  transition: opacity 180ms ease;
}

@media (prefers-reduced-motion: reduce) {
  .spotlight-card::before {
    display: none;
  }
}
</style>

