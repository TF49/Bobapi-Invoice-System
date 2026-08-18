<template>
  <div class="auth-shell">
    <aside class="auth-brand" aria-label="发票管理系统">
      <DotGrid />
      <div class="auth-brand-content">
        <div class="auth-brand-lockup">
          <span class="auth-brand-mark"><Tickets /></span>
          <div>
            <strong>BOBAPI</strong>
            <span>INVOICE SYSTEM</span>
          </div>
        </div>

        <div class="auth-brand-copy">
          <p>企业发票工作台</p>
          <h1>每一张发票，<br>都清晰可循。</h1>
        </div>

        <div class="invoice-preview" aria-hidden="true">
          <div class="invoice-preview-header">
            <span class="invoice-preview-icon"><DocumentChecked /></span>
            <span>INVOICE</span>
            <small>NO. 20260818</small>
          </div>
          <div class="invoice-preview-lines">
            <i /><i /><i />
          </div>
          <div class="invoice-preview-total">
            <span>合计</span>
            <strong>¥ 28,640.00</strong>
          </div>
          <div class="invoice-preview-status">
            <CircleCheckFilled />
            已完成
          </div>
        </div>

        <p class="auth-brand-footer">BOBAPI · 票据管理</p>
      </div>
    </aside>

    <main class="auth-main">
      <AnimatedContent class="auth-panel" :distance="10">
        <div class="auth-heading">
          <span>{{ eyebrow }}</span>
          <h2>{{ title }}</h2>
          <p>{{ subtitle }}</p>
        </div>
        <slot />
      </AnimatedContent>
    </main>
  </div>
</template>

<script setup lang="ts">
import { CircleCheckFilled, DocumentChecked, Tickets } from '@element-plus/icons-vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import DotGrid from '@/components/bits/DotGrid.vue'

defineProps<{
  eyebrow: string
  title: string
  subtitle: string
}>()
</script>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(380px, 44%) minmax(420px, 1fr);
  min-height: 100vh;
  background: var(--color-surface);
}

.auth-brand {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  color: #fff;
  background: #153f36;
}

.auth-brand::after {
  position: absolute;
  right: -18%;
  bottom: -28%;
  width: 72%;
  aspect-ratio: 1;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  content: '';
}

.auth-brand-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 48px clamp(40px, 6vw, 88px);
}

.auth-brand-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
}

.auth-brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  color: #153f36;
  background: #f2c66d;
  border-radius: 8px;
}

.auth-brand-mark :deep(svg) {
  width: 22px;
  height: 22px;
}

.auth-brand-lockup div {
  display: grid;
  gap: 1px;
}

.auth-brand-lockup strong {
  font-size: 14px;
  letter-spacing: 0;
}

.auth-brand-lockup span:last-child {
  color: rgba(255, 255, 255, 0.6);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.auth-brand-copy {
  margin-top: clamp(64px, 12vh, 118px);
}

.auth-brand-copy p {
  margin-bottom: 16px;
  color: #f2c66d;
  font-size: 14px;
  font-weight: 600;
}

.auth-brand-copy h1 {
  margin: 0;
  font-size: 52px;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0;
}

.invoice-preview {
  width: min(100%, 360px);
  margin-top: 52px;
  padding: 22px;
  color: var(--color-text);
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 8px;
  box-shadow: 0 28px 60px rgba(4, 25, 20, 0.24);
  transform: rotate(-2deg);
}

.invoice-preview-header {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--color-text);
  font-size: 11px;
  font-weight: 700;
}

.invoice-preview-header small {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 9px;
  font-weight: 500;
}

.invoice-preview-icon {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-radius: 6px;
}

.invoice-preview-icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.invoice-preview-lines {
  display: grid;
  gap: 9px;
  margin: 22px 0;
}

.invoice-preview-lines i {
  display: block;
  height: 5px;
  background: #e8ecea;
  border-radius: 2px;
}

.invoice-preview-lines i:nth-child(2) {
  width: 72%;
}

.invoice-preview-lines i:nth-child(3) {
  width: 86%;
}

.invoice-preview-total {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px dashed var(--color-border-strong);
}

.invoice-preview-total span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.invoice-preview-total strong {
  color: var(--color-primary);
  font-size: 18px;
}

.invoice-preview-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 16px;
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 600;
}

.invoice-preview-status :deep(svg) {
  width: 14px;
}

.auth-brand-footer {
  margin-top: auto;
  padding-top: 36px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
}

.auth-main {
  display: grid;
  min-height: 100vh;
  padding: 48px 7vw;
  place-items: center;
}

.auth-panel {
  width: min(100%, 420px);
}

.auth-heading {
  margin-bottom: 32px;
}

.auth-heading span {
  display: inline-block;
  margin-bottom: 10px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.auth-heading h2 {
  margin: 0 0 10px;
  color: var(--color-text);
  font-size: 30px;
  line-height: 1.25;
  letter-spacing: 0;
}

.auth-heading p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

@media (max-width: 1200px) and (min-width: 881px) {
  .auth-brand-copy h1 {
    font-size: 44px;
  }
}

@media (max-width: 880px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    min-height: auto;
  }

  .auth-brand-content {
    min-height: auto;
    padding: 28px 24px 36px;
  }

  .auth-brand-copy {
    margin-top: 40px;
  }

  .auth-brand-copy h1 {
    font-size: 32px;
  }

  .invoice-preview,
  .auth-brand-footer {
    display: none;
  }

  .auth-main {
    min-height: auto;
    padding: 48px 24px 56px;
  }
}

@media (max-width: 480px) {
  .auth-brand-copy h1 br {
    display: none;
  }

  .auth-heading h2 {
    font-size: 26px;
  }
}
</style>
