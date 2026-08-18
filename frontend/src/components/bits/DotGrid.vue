<template>
  <div class="dot-grid" aria-hidden="true" />
</template>

<style scoped>
.dot-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255, 255, 255, 0.24) 1px, transparent 1.2px);
  background-position: center;
  background-size: 24px 24px;
  pointer-events: none;
}
</style>

