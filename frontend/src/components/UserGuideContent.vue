<template>
  <div ref="contentRef" class="user-guide-content" :class="[`mode-${mode}`]">
    <!-- 顶部极简快速跳转锚点 -->
    <div class="guide-nav-bar">
      <div class="nav-pills">
        <button
          type="button"
          class="pill-btn"
          :class="{ active: activeSection === 'quota' }"
          @click="scrollToSection('quota')"
        >
          <el-icon><Wallet /></el-icon>
          <span>1. 额度申请</span>
        </button>
        <button
          type="button"
          class="pill-btn"
          :class="{ active: activeSection === 'invoice' }"
          @click="scrollToSection('invoice')"
        >
          <el-icon><Tickets /></el-icon>
          <span>2. 发票申请</span>
        </button>
        <button
          type="button"
          class="pill-btn"
          :class="{ active: activeSection === 'document' }"
          @click="scrollToSection('document')"
        >
          <el-icon><DocumentChecked /></el-icon>
          <span>3. 文件盖章</span>
        </button>
        <button
          type="button"
          class="pill-btn"
          :class="{ active: activeSection === 'faq' }"
          @click="scrollToSection('faq')"
        >
          <el-icon><QuestionFilled /></el-icon>
          <span>4. 常见问题</span>
        </button>
      </div>
      <span class="scroll-hint">
        <el-icon><ArrowDown /></el-icon>
        上下滑动浏览全部
      </span>
    </div>

    <!-- 整个内容流（单页自然顺畅上下滑动） -->
    <div class="guide-scroll-body">
      <!-- 模块 1：额度申请 -->
      <section id="guide-section-quota" class="compact-section">
        <div class="section-title-row">
          <span class="section-index">01</span>
          <div>
            <h3>额度申请指引</h3>
            <p>1 元额度 = 1 元开票面额，按 3% 手续费充值换算</p>
          </div>
          <el-button
            size="small"
            type="primary"
            plain
            class="section-action-btn"
            @click="handleAction('quota')"
          >
            前往充值
          </el-button>
        </div>

        <!-- 极简计算公式横条 -->
        <div class="mini-rule-bar">
          <div class="rule-calc">
            <span class="rule-label">手续费率 <strong>3%</strong></span>
            <span class="calc-arrow">➔</span>
            <span class="rule-formula">到账额度 = 支付手续费 ÷ 3%</span>
          </div>
          <div class="rule-demo">
            <span>示例：付 <strong>¥30</strong> 手续费 ➔ 得 <strong>¥1,000</strong> 额度</span>
          </div>
        </div>

        <!-- 充值 4 步流程 -->
        <div class="compact-step-list">
          <div class="step-line-item">
            <div class="step-circle">1</div>
            <div class="step-detail">
              <strong>进入充值</strong>
              <span>在「我的额度」页面点击「快速充值」按钮</span>
            </div>
          </div>

          <div class="step-line-item">
            <div class="step-circle">2</div>
            <div class="step-detail">
              <strong>输入手续费</strong>
              <span>输入支付金额，系统实时显示预计到账额度</span>
            </div>
          </div>

          <div class="step-line-item">
            <div class="step-circle">3</div>
            <div class="step-detail">
              <strong>转账与回执</strong>
              <span>完成线下对公转账，保存转账成功截图</span>
            </div>
          </div>

          <div class="step-line-item">
            <div class="step-circle">4</div>
            <div class="step-detail">
              <strong>上传提交</strong>
              <span>上传回执截图提交，管理员对账后额度自动到账</span>
            </div>
          </div>
        </div>

        <!-- 凭证精简提示 -->
        <div class="micro-note-box">
          <el-icon><InfoFilled /></el-icon>
          <span>凭证要点：截图须清晰包含<strong>交易金额、流水号、时间</strong>，转账金额须与申请金额精确一致。</span>
        </div>
      </section>

      <!-- 分隔线 -->
      <div class="section-divider" />

      <!-- 模块 2：发票申请 -->
      <section id="guide-section-invoice" class="compact-section">
        <div class="section-title-row">
          <span class="section-index">02</span>
          <div>
            <h3>发票申请指引</h3>
            <p>开票金额须 ≤ 可用额度，支持单张、AI 填单与批量导入</p>
          </div>
          <el-button
            size="small"
            type="primary"
            class="section-action-btn"
            @click="handleAction('invoice')"
          >
            前往开票
          </el-button>
        </div>

        <!-- 3 种方式极简对比卡片 -->
        <div class="methods-mini-grid">
          <div class="mini-method-card">
            <div class="method-badge">方式一</div>
            <h4>常规手动填单</h4>
            <p>普票填公司名与税号；专票需补充银行账户及地址电话。</p>
          </div>

          <div class="mini-method-card highlight">
            <div class="method-badge star">方式二 · 推荐</div>
            <h4>AI 智能识别自动填单</h4>
            <p>直接粘贴客户发送的杂乱文本，AI 自动提取关键字段并回填。</p>
          </div>

          <div class="mini-method-card">
            <div class="method-badge">方式三</div>
            <h4>Excel 批量导入</h4>
            <p>下载官方 Excel 模板，集中整理多条发票信息一键批量上传。</p>
          </div>
        </div>

        <!-- 极简状态流转 -->
        <div class="mini-flow-row">
          <div class="flow-node">
            <el-icon><Clock /></el-icon>
            <span>1. 待开票</span>
          </div>
          <span class="flow-connect">➔</span>
          <div class="flow-node success">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>2. 已开票 (下载凭证)</span>
          </div>
          <span class="flow-connect">➔</span>
          <div class="flow-node warning">
            <el-icon><RefreshLeft /></el-icon>
            <span>3. 错票申请红冲 (额度退回)</span>
          </div>
        </div>
      </section>

      <!-- 分隔线 -->
      <div class="section-divider" />

      <!-- 模块 3：文件盖章 -->
      <section id="guide-section-document" class="compact-section">
        <div class="section-title-row">
          <span class="section-index">03</span>
          <div>
            <h3>证明文件盖章指引</h3>
            <p>需加盖公章的各类证明文件，极简直传，发票方盖章交付</p>
          </div>
          <el-button
            size="small"
            type="primary"
            class="section-action-btn"
            @click="handleAction('document')"
          >
            前往盖章
          </el-button>
        </div>

        <!-- 极简计费与规则横条 -->
        <div class="mini-rule-bar">
          <div class="rule-calc">
            <span class="rule-label">服务费 <strong>¥10.00 / 次</strong></span>
            <span class="calc-arrow">➔</span>
            <span class="rule-formula">独立盖章钱包扣费 · 1:1 转账充值</span>
          </div>
          <div class="rule-demo">
            <span>规则：<strong>无需表单直接上传原件</strong> · 撤销或驳回全额自动退款</span>
          </div>
        </div>

        <!-- 盖章 3 步极简流程 -->
        <div class="compact-step-list">
          <div class="step-line-item">
            <div class="step-circle">1</div>
            <div class="step-detail">
              <strong>极简称心上传</strong>
              <span>待盖章文件格式完全不限（PDF/Word/Excel/图片均可），无需任何表单直接拖拽提交</span>
            </div>
          </div>

          <div class="step-line-item">
            <div class="step-circle">2</div>
            <div class="step-detail">
              <strong>审核与加盖公章</strong>
              <span>发票方（开票员/管理员）在线审核材料并加盖企业公章，制作交付扫描件</span>
            </div>
          </div>

          <div class="step-line-item">
            <div class="step-circle">3</div>
            <div class="step-detail">
              <strong>在线查验与下载</strong>
              <span>盖章完成后系统变更为已交付，支持一键在线预览查验与高清交付文件下载</span>
            </div>
          </div>
        </div>

        <!-- 凭证精简提示 -->
        <div class="micro-note-box">
          <el-icon><InfoFilled /></el-icon>
          <span>额度保障：每次上传前先从独立盖章钱包扣除 10 元；若您主动撤销或被发票方驳回，款项<strong>全额原路秒级退还</strong>。</span>
        </div>
      </section>

      <!-- 分隔线 -->
      <div class="section-divider" />

      <!-- 模块 4：常见问题 FAQ -->
      <section id="guide-section-faq" class="compact-section">
        <div class="section-title-row">
          <span class="section-index">04</span>
          <div>
            <h3>常见问题速查</h3>
            <p>高频疑问快速排查与解答</p>
          </div>
        </div>

        <div class="faq-compact-list">
          <div class="faq-mini-item">
            <div class="faq-q">
              <span class="q-tag">Q</span>
              <strong>证明文件盖章如何收费？发票额度和盖章额度通用吗？</strong>
            </div>
            <div class="faq-a">
              每次盖章固定收费 <strong>10.00 元</strong>。盖章费用与发票额度相互独立，采用专用盖章钱包。充值时转账金额 <strong>1:1 入账</strong>，不收取额外手续费。
            </div>
          </div>

          <div class="faq-mini-item">
            <div class="faq-q">
              <span class="q-tag">Q</span>
              <strong>上传待盖章文件有限制格式或大小吗？</strong>
            </div>
            <div class="faq-a">
              待盖章文件格式<strong>完全不限制</strong>，支持 PDF、Word、Excel、图片等常用格式，单文件上限支持 <strong>50MB</strong>。
            </div>
          </div>

          <div class="faq-mini-item">
            <div class="faq-q">
              <span class="q-tag">Q</span>
              <strong>申请盖章后如果驳回或取消，费用会退吗？</strong>
            </div>
            <div class="faq-a">
              会的。如果用户主动撤销待盖章工单，或者发票方因材料不合规驳回工单，扣除的 10 元服务费将<strong>全额自动退回</strong>至您的盖章钱包。
            </div>
          </div>
          <div class="faq-mini-item">
            <div class="faq-q">
              <span class="q-tag">Q</span>
              <strong>额度充值后多久到账？</strong>
            </div>
            <div class="faq-a">
              工作时间（9:00 - 18:00）提交通常在 <strong>15 ~ 30 分钟</strong>内审核入账；非工作时间次日优先处理。
            </div>
          </div>

          <div class="faq-mini-item">
            <div class="faq-q">
              <span class="q-tag">Q</span>
              <strong>提示“可用额度不足”怎么办？</strong>
            </div>
            <div class="faq-a">
              开票金额不能超过账户可用额度。请先前往「我的额度」页面充值手续费（按 3% 折算额度），审核通过后再开票。
            </div>
          </div>

          <div class="faq-mini-item">
            <div class="faq-q">
              <span class="q-tag">Q</span>
              <strong>开票信息填错了如何处理？</strong>
            </div>
            <div class="faq-a">
              在已开票列表中找到对应记录，点击<strong>「申请红冲」</strong>。开票员在税务系统作废后，扣减的额度将<strong>全额自动退还</strong>。
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import {
  Wallet,
  Tickets,
  DocumentChecked,
  QuestionFilled,
  ArrowDown,
  InfoFilled,
  CircleCheckFilled,
  Clock,
  RefreshLeft
} from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    initialTab?: 'quota' | 'invoice' | 'document' | 'faq'
    mode?: 'dialog' | 'page'
  }>(),
  {
    initialTab: 'quota',
    mode: 'page'
  }
)

const emit = defineEmits<{
  (e: 'navigate', destination: 'quota' | 'invoice' | 'document'): void
}>()

const contentRef = ref<HTMLElement | null>(null)
const activeSection = ref<'quota' | 'invoice' | 'document' | 'faq'>(props.initialTab || 'quota')

const scrollToSection = (section: 'quota' | 'invoice' | 'document' | 'faq') => {
  activeSection.value = section
  const targetId = `guide-section-${section}`
  const targetEl = contentRef.value?.querySelector(`#${targetId}`)
  if (targetEl) {
    targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

watch(
  () => props.initialTab,
  (val) => {
    if (val) {
      nextTick(() => scrollToSection(val))
    }
  }
)

onMounted(() => {
  if (props.initialTab && props.initialTab !== 'quota') {
    nextTick(() => scrollToSection(props.initialTab))
  }
})

const handleAction = (destination: 'quota' | 'invoice' | 'document') => {
  emit('navigate', destination)
}
</script>

<style scoped>
.user-guide-content {
  color: var(--color-text);
  font-family: inherit;
  display: flex;
  flex-direction: column;
}

/* 顶部极简导航栏 (粘性悬浮) */
.guide-nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--color-surface-muted, #f8fafc);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  margin-bottom: 20px;
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(8px);
}

.nav-pills {
  display: flex;
  gap: 8px;
}

.pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  background: transparent;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.pill-btn:hover {
  background: var(--color-surface, #ffffff);
  color: var(--color-primary);
  border-color: var(--color-border);
}

.pill-btn.active {
  background: var(--color-surface, #ffffff);
  color: var(--color-primary);
  font-weight: 600;
  border-color: var(--color-primary);
  box-shadow: 0 2px 6px rgba(18, 113, 91, 0.1);
}

.scroll-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* 滚动主体区域 */
.guide-scroll-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 各章节卡片 */
.compact-section {
  padding: 8px 4px;
  scroll-margin-top: 60px;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.section-index {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-primary);
  background: var(--color-primary-soft, #e9f4f0);
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.section-title-row h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.3;
}

.section-title-row p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--color-text-muted);
}

.section-action-btn {
  margin-left: auto;
  font-weight: 500;
}

/* 极简计算公式条 */
.mini-rule-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
}

.rule-calc {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-label strong {
  color: var(--color-primary);
}

.calc-arrow {
  color: #10b981;
}

.rule-formula {
  font-weight: 600;
  color: #065f46;
}

.rule-demo {
  color: #047857;
  font-size: 12.5px;
}

/* 充值 4 步极简流程 */
.compact-step-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.step-line-item {
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-detail strong {
  display: block;
  font-size: 13.5px;
  color: var(--color-text);
  margin-bottom: 3px;
}

.step-detail span {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.4;
  display: block;
}

/* 微型提示条 */
.micro-note-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-surface-muted, #f8fafc);
  border: 1px dashed var(--color-border);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.micro-note-box .el-icon {
  color: var(--color-primary);
  font-size: 15px;
  flex-shrink: 0;
}

/* 方式 3 格极简卡片 */
.methods-mini-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.mini-method-card {
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 14px 12px;
}

.mini-method-card.highlight {
  border-color: #34d399;
  background: linear-gradient(180deg, #f0fdf4 0%, #ffffff 100%);
}

.method-badge {
  display: inline-block;
  font-size: 10.5px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  background: #e2e8f0;
  color: #475569;
  margin-bottom: 6px;
}

.method-badge.star {
  background: #10b981;
  color: #ffffff;
}

.mini-method-card h4 {
  margin: 0 0 6px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.mini-method-card p {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

/* 极简状态流转指示 */
.mini-flow-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--color-surface-muted, #f8fafc);
  border: 1px solid var(--color-border);
  font-size: 12.5px;
}

.flow-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.flow-node.success {
  color: #059669;
}

.flow-node.warning {
  color: #d97706;
}

.flow-connect {
  color: #94a3b8;
  font-size: 12px;
}

/* 常见问题精简列表 */
.faq-compact-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.faq-mini-item {
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px 14px;
}

.faq-q {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.q-tag {
  font-size: 11px;
  font-weight: 800;
  color: #ffffff;
  background: var(--color-primary);
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.faq-q strong {
  font-size: 13.5px;
  color: var(--color-text);
}

.faq-a {
  font-size: 12.5px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  padding-left: 26px;
}

/* 分隔线 */
.section-divider {
  height: 1px;
  background: var(--color-border);
  margin: 14px 0;
  opacity: 0.6;
}

@media (max-width: 768px) {
  .compact-step-list,
  .methods-mini-grid {
    grid-template-columns: 1fr;
  }
  .mini-flow-row {
    flex-direction: column;
    align-items: flex-start;
  }
  .flow-connect {
    display: none;
  }
  .scroll-hint {
    display: none;
  }
}
</style>
