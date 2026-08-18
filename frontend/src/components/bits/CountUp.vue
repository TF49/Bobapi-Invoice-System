<template>
  <span class="count-up" aria-live="polite">{{ formattedValue }}</span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  duration?: number
  decimals?: number
  prefix?: string
  suffix?: string
}>(), {
  duration: 650,
  decimals: 0,
  prefix: '',
  suffix: ''
})

const displayValue = ref(0)
let animationFrame = 0

const formattedValue = computed(() => {
  const value = new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals
  }).format(displayValue.value)

  return `${props.prefix}${value}${props.suffix}`
})

const animateTo = (target: number) => {
  cancelAnimationFrame(animationFrame)

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    displayValue.value = target
    return
  }

  const start = displayValue.value
  const difference = target - start
  const startTime = performance.now()

  const update = (now: number) => {
    const progress = Math.min((now - startTime) / props.duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    displayValue.value = start + difference * eased

    if (progress < 1) {
      animationFrame = requestAnimationFrame(update)
    } else {
      displayValue.value = target
    }
  }

  animationFrame = requestAnimationFrame(update)
}

watch(() => props.value, animateTo)

onMounted(() => animateTo(props.value))
onBeforeUnmount(() => cancelAnimationFrame(animationFrame))
</script>

