<template>
  <div class="slide-captcha">
    <el-button
      class="trigger-btn"
      :type="verified ? 'success' : 'default'"
      :disabled="disabled || loading"
      :loading="loading && !challenge"
      @click="loadChallenge"
    >
      {{ verified ? '已验证' : '安全验证' }}
    </el-button>

    <p v-if="loading && !challenge" class="status muted" role="status">加载中...</p>

    <div v-if="challenge && !verified" class="puzzle-panel">
      <p class="hint">移动滑块完成拼图</p>
      <div class="puzzle-frame">
        <img class="master" :src="challenge.masterImage" alt="验证拼图" />
        <img
          class="tile"
          :src="challenge.tileImage"
          alt=""
          :style="tileStyle"
        />
      </div>
      <input
        class="slider"
        type="range"
        aria-label="拼图位置"
        :min="0"
        :max="maxOffset"
        :step="1"
        :value="offsetX"
        :disabled="disabled || loading"
        @input="onSlide"
      />
      <el-button
        class="verify-btn"
        type="primary"
        :loading="loading"
        :disabled="disabled"
        @click="verify"
      >
        验证
      </el-button>
    </div>

    <p v-if="verified" class="status ok" role="status">已验证</p>
    <p v-if="errorMessage" class="status err" role="alert">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

const loading = ref(false)
const verified = ref(false)
const challenge = ref<CaptchaChallenge | null>(null)
const offsetX = ref(0)
const errorMessage = ref('')
const captchaToken = ref('')

const maxOffset = computed(() => {
  if (!challenge.value) return 0
  return Math.max(0, challenge.value.masterWidth - challenge.value.tileWidth)
})

const tileStyle = computed(() => {
  if (!challenge.value) return {}
  const c = challenge.value
  return {
    width: `${(c.tileWidth / c.masterWidth) * 100}%`,
    left: `${(offsetX.value / c.masterWidth) * 100}%`,
    top: `${(c.thumbDisplayY / c.masterHeight) * 100}%`
  }
})

watch(
  () => props.resetKey,
  () => {
    reset()
  }
)

const reset = () => {
  verified.value = false
  challenge.value = null
  offsetX.value = 0
  errorMessage.value = ''
  captchaToken.value = ''
  emit('invalidate')
}

const loadChallenge = async () => {
  if (props.disabled || loading.value) return
  if (verified.value) {
    reset()
  }
  emit('invalidate')
  verified.value = false
  challenge.value = null
  errorMessage.value = ''
  captchaToken.value = ''
  loading.value = true
  try {
    const data = await authApi.generateCaptcha()
    challenge.value = data
    offsetX.value = data.thumbDisplayX ?? 0
  } catch (err: any) {
    errorMessage.value = err?.message || '加载验证码失败'
  } finally {
    loading.value = false
  }
}

const onSlide = (event: Event) => {
  offsetX.value = Number((event.target as HTMLInputElement).value)
}

const verify = async () => {
  if (!challenge.value || props.disabled || loading.value) return
  loading.value = true
  errorMessage.value = ''
  let shouldReload = false
  try {
    const result = await authApi.verifyCaptcha({
      captchaId: challenge.value.captchaId,
      x: offsetX.value,
      y: challenge.value.thumbDisplayY
    })
    captchaToken.value = result.captchaToken
    verified.value = true
    challenge.value = null
    emit('verified', result.captchaToken)
  } catch (err: any) {
    errorMessage.value = err?.message || '验证失败，请重试'
    emit('invalidate')
    challenge.value = null
    shouldReload = true
  } finally {
    loading.value = false
  }
  // 挑战已在服务端作废，自动拉取新题
  if (shouldReload) {
    await loadChallenge()
  }
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

.hint {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.puzzle-frame {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background: #0f2f28;
}

.master {
  display: block;
  width: 100%;
  vertical-align: top;
}

.tile {
  position: absolute;
  pointer-events: none;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.35));
}

.slider {
  display: block;
  width: 100%;
  margin: 12px 0;
  accent-color: var(--color-primary);
}

.verify-btn {
  width: 100%;
}

.status {
  margin: 8px 0 0;
  font-size: 13px;
}

.status.muted {
  color: var(--color-text-muted);
}

.status.ok {
  color: var(--color-primary);
}

.status.err {
  color: var(--color-danger);
}
</style>
