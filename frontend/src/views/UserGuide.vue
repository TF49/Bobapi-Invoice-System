<template>
  <div class="workspace-shell">
    <AppHeader title="使用说明" />

    <main class="workspace-content user-guide-page">
      <!-- 顶部横幅 -->
      <AnimatedContent tag="section" class="guide-hero-section" :distance="10">
        <SpotlightCard class="hero-spotlight-card">
          <div class="hero-inner">
            <div class="hero-left">
              <span class="hero-icon"><Reading /></span>
              <div class="hero-info">
                <h2>系统操作指引与帮助中心</h2>
                <p>极简掌握额度充值折算与发票开具全流程。</p>
              </div>
            </div>
            <div class="hero-shortcuts">
              <el-button type="primary" plain :icon="Wallet" @click="router.push('/user/quota')">
                前往额度充值
              </el-button>
              <el-button type="primary" :icon="Tickets" @click="router.push('/user')">
                前往发票申请
              </el-button>
            </div>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <!-- 主体内容卡片 -->
      <AnimatedContent tag="section" class="surface-panel guide-container-panel" :delay="80">
        <UserGuideContent
          :initial-tab="activeTab"
          mode="page"
          @navigate="handleNavigate"
        />
      </AnimatedContent>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Reading, Tickets, Wallet } from '@element-plus/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import UserGuideContent from '@/components/UserGuideContent.vue'

const route = useRoute()
const router = useRouter()

const activeTab = computed<'quota' | 'invoice' | 'faq'>(() => {
  const tab = route.query.tab
  if (tab === 'invoice' || tab === 'faq') {
    return tab
  }
  return 'quota'
})

const handleNavigate = (destination: 'quota' | 'invoice') => {
  if (destination === 'quota') {
    router.push('/user/quota')
  } else if (destination === 'invoice') {
    router.push('/user')
  }
}
</script>

<style scoped>
.user-guide-page {
  max-width: 1200px;
}

.guide-hero-section {
  margin-bottom: 20px;
}

.hero-spotlight-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
  gap: 20px;
  flex-wrap: wrap;
}

.hero-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hero-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}

.hero-info h2 {
  margin: 0 0 6px 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.hero-info p {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.hero-shortcuts {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.guide-container-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: var(--shadow-soft);
}

@media (max-width: 768px) {
  .hero-inner {
    flex-direction: column;
    align-items: flex-start;
    padding: 18px 20px;
  }

  .hero-shortcuts {
    width: 100%;
  }

  .hero-shortcuts .el-button {
    flex: 1;
  }

  .guide-container-panel {
    padding: 18px 16px;
  }
}
</style>
