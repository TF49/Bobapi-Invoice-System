<template>
  <div class="slide-captcha">
    <el-button
      class="trigger-btn"
      :type="verified ? 'success' : 'default'"
      :disabled="disabled || loading"
      :loading="loading && !challenge"
      @click="loadChallenge()"
    >
      {{ verified ? '已验证' : '安全验证' }}
    </el-button>

    <p v-if="loading && !challenge" class="status muted" role="status">加载中...</p>

    <div v-if="challenge && !verified" class="puzzle-panel">
      <div class="panel-head">
        <p :id="hintId" class="hint">向右拖动滑块，使拼图块与形状相同的缺口对齐</p>
        <el-button
          class="refresh-btn"
          text
          :icon="Refresh"
          aria-label="换一张"
          title="换一张"
          :disabled="disabled || loading"
          @click="loadChallenge()"
        />
      </div>
      <div class="puzzle-frame">
        <img class="master" :src="challenge.masterImage" alt="验证拼图" draggable="false" />
        <img
          class="tile"
          :class="{ animated: !dragging }"
          :src="challenge.tileImage"
          alt=""
          draggable="false"
          :style="tileStyle"
        />
      </div>
      <div
        ref="trackRef"
        class="slider-track"
        :class="{ dragging, failed: status === 'error', busy: loading }"
      >
        <div class="slider-fill" :class="{ animated: !dragging }" :style="fillStyle" />
        <span v-if="offsetX === 0 && !dragging" class="slider-label" aria-hidden="true">
          按住滑块向右拖动
        </span>
        <div
          class="slider-handle"
          :class="{ animated: !dragging }"
          :style="handleStyle"
          role="slider"
          tabindex="0"
          aria-label="拼图位置"
          :aria-describedby="hintId"
          :aria-valuemin="0"
          :aria-valuemax="maxOffset"
          :aria-valuenow="offsetX"
          :aria-disabled="interactionLocked"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerCancel"
          @keydown="onKeyDown"
        >
          <el-icon :size="18"><DArrowRight /></el-icon>
        </div>
      </div>
      <p class="keyboard-hint">也可聚焦滑块后用方向键移动（Shift 加速），按回车提交</p>
    </div>

    <p v-if="verified" class="status ok" role="status">已验证</p>
    <p v-if="errorMessage" class="status err" role="alert">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { DArrowRight, Refresh } from '@element-plus/icons-vue'
import { authApi, type CaptchaChallenge } from '@/api/auth'

const props = defineProps<{
  disabled?: boolean
  /** 外部强制重置（例如登录失败后需重新验证） */
  resetKey?: number | string
}>()

const emit = defineEmits<{
  verified: [token: string]
  invalidate: []
}>()

/** 与服务端 SlideCaptchaService.MAX_TRACK_POINTS 保持在其之下 */
const MAX_TRACK_POINTS = 380
const HANDLE_SIZE = 44
/** 按下后几乎未移动时视为误触，不提交 */
const MIN_SUBMIT_OFFSET = 3
/** 与服务端 SlideCaptchaService.MAX_TRACK_ABS_Y 一致，纵向偏移超出时截断，避免手抖被误判为脚本 */
const MAX_TRACK_ABS_Y = 200
/** 失败后保留失败状态的时长，让用户看清结果再换题 */
const FAIL_FEEDBACK_MS = 500

const hintId = `slide-captcha-hint-${Math.random().toString(36).slice(2, 10)}`

const loading = ref(false)
const verified = ref(false)
const challenge = ref<CaptchaChallenge | null>(null)
const offsetX = ref(0)
const errorMessage = ref('')
const captchaToken = ref('')
const dragging = ref(false)
const status = ref<'idle' | 'error'>('idle')
const trackRef = ref<HTMLElement | null>(null)

// 一次作答的轨迹会话：指针与键盘共用，提交或重置后清空
let sessionStart = 0
let track: number[][] = []
let pointerId: number | null = null
let pointerStartX = 0
let pointerStartY = 0
let pointerStartOffset = 0
let lastDy = 0
// 每次重置、换题或卸载时递增，用于丢弃已过期的异步结果
let generation = 0

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

const maxOffset = computed(() => {
  if (!challenge.value) return 0
  return Math.max(0, challenge.value.masterWidth - challenge.value.tileWidth)
})

const interactionLocked = computed(() => !!props.disabled || loading.value)

const ratio = computed(() => (maxOffset.value > 0 ? offsetX.value / maxOffset.value : 0))

const tileStyle = computed(() => {
  if (!challenge.value) return {}
  const c = challenge.value
  return {
    width: `${(c.tileWidth / c.masterWidth) * 100}%`,
    left: `${(offsetX.value / c.masterWidth) * 100}%`,
    top: `${(c.thumbDisplayY / c.masterHeight) * 100}%`
  }
})

const handleStyle = computed(() => ({
  left: `calc((100% - ${HANDLE_SIZE}px) * ${ratio.value})`
}))

const fillStyle = computed(() => ({
  width: `calc((100% - ${HANDLE_SIZE}px) * ${ratio.value} + ${HANDLE_SIZE / 2}px)`
}))

watch(
  () => props.resetKey,
  () => {
    reset()
  }
)

const clearSession = () => {
  sessionStart = 0
  track = []
  pointerId = null
  lastDy = 0
  dragging.value = false
}

const reset = () => {
  generation++
  verified.value = false
  challenge.value = null
  offsetX.value = 0
  errorMessage.value = ''
  captchaToken.value = ''
  status.value = 'idle'
  loading.value = false
  clearSession()
  emit('invalidate')
}

onBeforeUnmount(() => {
  generation++
})

/**
 * 拉取新题。
 * keepError：保留上一次的失败提示；keepCurrent：加载期间继续显示旧题（失败态），
 * 新题到达后再替换，避免面板先消失再出现造成的高度跳动。
 */
const loadChallenge = async (options: { keepError?: boolean; keepCurrent?: boolean } = {}) => {
  if (props.disabled || loading.value) return
  if (verified.value) {
    reset()
  }
  emit('invalidate')
  const current = ++generation
  verified.value = false
  if (!options.keepCurrent) {
    challenge.value = null
    offsetX.value = 0
    status.value = 'idle'
  }
  if (!options.keepError) {
    errorMessage.value = ''
  }
  captchaToken.value = ''
  clearSession()
  loading.value = true
  try {
    const data = await authApi.generateCaptcha()
    if (current !== generation) return
    challenge.value = data
    offsetX.value = data.thumbDisplayX ?? 0
    status.value = 'idle'
  } catch (err: any) {
    if (current !== generation) return
    challenge.value = null
    offsetX.value = 0
    status.value = 'idle'
    errorMessage.value = err?.message || '加载验证码失败'
  } finally {
    if (current === generation) {
      loading.value = false
    }
  }
}

const now = () => Math.round(performance.now() - sessionStart)

const ensureSession = () => {
  if (track.length > 0) return
  sessionStart = performance.now()
  track = [[offsetX.value, 0, 0]]
}

const recordPoint = (x: number, dy: number) => {
  track.push([x, Math.round(dy), now()])
  if (track.length >= MAX_TRACK_POINTS) {
    // 隔点抽稀，保留首尾，避免长时间拖动超出服务端点数上限
    const last = track[track.length - 1]
    track = track.filter((_, index) => index % 2 === 0)
    if (track[track.length - 1] !== last) track.push(last)
  }
}

const clampOffset = (value: number) => Math.min(maxOffset.value, Math.max(0, Math.round(value)))

const onPointerDown = (event: PointerEvent) => {
  if (interactionLocked.value || !challenge.value || !trackRef.value) return
  if (event.pointerType === 'mouse' && event.button !== 0) return
  event.preventDefault()
  ensureSession()
  pointerId = event.pointerId
  pointerStartX = event.clientX
  pointerStartY = event.clientY
  pointerStartOffset = offsetX.value
  dragging.value = true
  errorMessage.value = ''
  status.value = 'idle'
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
}

const onPointerMove = (event: PointerEvent) => {
  if (!dragging.value || event.pointerId !== pointerId || !trackRef.value) return
  const usable = trackRef.value.getBoundingClientRect().width - HANDLE_SIZE
  if (usable <= 0) return
  const dx = event.clientX - pointerStartX
  offsetX.value = clampOffset(pointerStartOffset + (dx / usable) * maxOffset.value)
  lastDy = Math.max(-MAX_TRACK_ABS_Y, Math.min(MAX_TRACK_ABS_Y, event.clientY - pointerStartY))
  recordPoint(offsetX.value, lastDy)
}

const onPointerUp = (event: PointerEvent) => {
  if (!dragging.value || event.pointerId !== pointerId) return
  dragging.value = false
  pointerId = null
  if (offsetX.value < MIN_SUBMIT_OFFSET) {
    offsetX.value = 0
    clearSession()
    return
  }
  recordPoint(offsetX.value, lastDy)
  void verify()
}

const onPointerCancel = (event: PointerEvent) => {
  if (event.pointerId !== pointerId) return
  offsetX.value = 0
  clearSession()
}

const onKeyDown = (event: KeyboardEvent) => {
  if (interactionLocked.value || !challenge.value) return
  const step = event.shiftKey ? 10 : 1
  if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
    event.preventDefault()
    ensureSession()
    offsetX.value = clampOffset(offsetX.value + step)
    recordPoint(offsetX.value, 0)
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
    event.preventDefault()
    ensureSession()
    offsetX.value = clampOffset(offsetX.value - step)
    recordPoint(offsetX.value, 0)
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (track.length > 1 && offsetX.value >= MIN_SUBMIT_OFFSET) {
      recordPoint(offsetX.value, 0)
      void verify()
    }
  }
}

const verify = async () => {
  if (!challenge.value || props.disabled || loading.value) return
  const current = generation
  loading.value = true
  errorMessage.value = ''
  const submittedTrack = track
  try {
    const result = await authApi.verifyCaptcha({
      captchaId: challenge.value.captchaId,
      x: offsetX.value,
      y: challenge.value.thumbDisplayY,
      track: submittedTrack
    })
    if (current !== generation) return
    captchaToken.value = result.captchaToken
    verified.value = true
    challenge.value = null
    clearSession()
    loading.value = false
    emit('verified', result.captchaToken)
    return
  } catch (err: any) {
    if (current !== generation) return
    errorMessage.value = err?.message || '验证失败，请重试'
    status.value = 'error'
    clearSession()
    emit('invalidate')
  }
  // 挑战已在服务端作废：保持失败态片刻（期间 loading 为 true，禁止再次操作），再原位换新题
  await wait(FAIL_FEEDBACK_MS)
  if (current !== generation) return
  loading.value = false
  await loadChallenge({ keepError: true, keepCurrent: true })
}

defineExpose({
  reset,
  loadChallenge,
  isVerified: () => verified.value,
  getToken: () => captchaToken.value
})
</script>

<style scoped>
.slide-captcha {
  margin: 0 0 16px;
}

.trigger-btn {
  width: 100%;
}

.puzzle-panel {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface-muted);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.refresh-btn {
  flex: none;
  width: 44px;
  height: 44px;
  margin: -8px -8px -8px 0;
  color: var(--color-text-secondary);
}

.puzzle-frame {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background: #0f2f28;
  user-select: none;
}

.master {
  display: block;
  width: 100%;
  vertical-align: top;
  pointer-events: none;
}

.tile {
  position: absolute;
  pointer-events: none;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.45));
}

.slider-track {
  position: relative;
  height: 44px;
  margin-top: 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  background: var(--color-surface);
  overflow: hidden;
  user-select: none;
  touch-action: none;
}

.slider-fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-primary-soft);
}

.slider-track.failed .slider-fill {
  background: var(--color-danger-soft);
}

.slider-label {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 44px;
  color: var(--color-text-secondary);
  font-size: 13px;
  pointer-events: none;
}

.slider-handle {
  position: absolute;
  top: -1px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-primary);
  box-shadow: 0 1px 3px rgba(24, 39, 34, 0.16);
  cursor: grab;
  touch-action: none;
  transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease;
}

.slider-handle:hover {
  border-color: var(--color-primary);
}

.slider-handle:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.slider-track.dragging .slider-handle {
  cursor: grabbing;
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.slider-track.failed {
  border-color: var(--color-danger);
}

.slider-track.failed .slider-handle {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.slider-track.busy .slider-handle,
.slider-handle[aria-disabled='true'] {
  cursor: not-allowed;
  opacity: 0.6;
}

/* 失败反馈期间虽然锁定操作，但保持失败态清晰可见 */
.slider-track.failed.busy .slider-handle {
  opacity: 1;
}

/* 松手回弹时的过渡；拖动过程中不加过渡以保证跟手 */
.slider-handle.animated,
.slider-fill.animated,
.tile.animated {
  transition-property: left, width, background-color, border-color, color;
  transition-duration: 200ms;
  transition-timing-function: ease-out;
}

.keyboard-hint {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.status {
  margin: 8px 0 0;
  font-size: 13px;
}

.status.muted {
  color: var(--color-text-secondary);
}

.status.ok {
  color: var(--color-primary);
}

.status.err {
  color: var(--color-danger);
}

@media (prefers-reduced-motion: reduce) {
  .slider-handle,
  .slider-handle.animated,
  .slider-fill.animated,
  .tile.animated {
    transition: none;
  }
}
</style>
