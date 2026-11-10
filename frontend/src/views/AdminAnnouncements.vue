<template>
  <div class="workspace-shell">
    <AppHeader title="公告管理" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid announcement-stats" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Promotion /></span>
            <div class="stat-copy">
              <span class="stat-label">公告总数</span>
              <strong class="stat-value"><CountUp :value="stats.total" /></strong>
            </div>
            <p class="stat-note">不含已删除公告</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">已发布</span>
              <strong class="stat-value"><CountUp :value="stats.published" /></strong>
            </div>
            <p class="stat-note">用户登录时弹窗展示</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><EditPen /></span>
            <div class="stat-copy">
              <span class="stat-label">草稿</span>
              <strong class="stat-value"><CountUp :value="stats.draft" /></strong>
            </div>
            <p class="stat-note">仅管理员可见</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Hide /></span>
            <div class="stat-copy">
              <span class="stat-label">已下线</span>
              <strong class="stat-value"><CountUp :value="stats.offline" /></strong>
            </div>
            <p class="stat-note">不再弹窗与展示</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><Promotion /></span>
            <div>
              <h2>公告列表</h2>
              <p>发布后所有用户登录时会收到弹窗，每条公告每人只弹一次</p>
            </div>
          </div>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建公告</el-button>
        </div>

        <div class="announcement-filter-bar">
          <el-input
            v-model="filters.keyword"
            class="keyword-input"
            clearable
            :prefix-icon="Search"
            aria-label="按标题搜索公告"
            placeholder="搜索公告标题"
            maxlength="50"
            @keyup.enter="applyFilters"
            @clear="applyFilters"
          />
          <el-select v-model="filters.category" class="filter-select" aria-label="筛选公告分类" @change="applyFilters">
            <el-option label="全部分类" value="ALL" />
            <el-option v-for="(meta, key) in ANNOUNCEMENT_CATEGORIES" :key="key" :label="meta.label" :value="key" />
          </el-select>
          <el-select v-model="filters.status" class="filter-select" aria-label="筛选公告状态" @change="applyFilters">
            <el-option label="全部状态" value="ALL" />
            <el-option v-for="(meta, key) in ANNOUNCEMENT_STATUSES" :key="key" :label="meta.label" :value="key" />
          </el-select>
          <el-button :icon="Search" @click="applyFilters">查询</el-button>
          <el-tooltip content="刷新列表" placement="top">
            <el-button
              class="refresh-button"
              :icon="RefreshRight"
              :loading="loading"
              aria-label="刷新公告列表"
              @click="loadAnnouncements"
            />
          </el-tooltip>
        </div>

        <div v-if="!loading && announcements.length === 0" class="table-empty-state">
          <span><Promotion /></span>
          <strong>暂无公告，点击右上角「新建公告」开始</strong>
        </div>
        <div v-else class="table-scroll announcement-table-scroll">
          <el-table :data="announcements" v-loading="loading" row-key="id">
            <el-table-column label="公告" min-width="300">
              <template #default="{ row }">
                <div class="announcement-title-cell" :class="`is-${row.category.toLowerCase()}`">
                  <span class="announcement-category-dot" aria-hidden="true" />
                  <div class="announcement-title-copy">
                    <div class="announcement-title-line">
                      <strong :title="row.title">{{ row.title }}</strong>
                      <span class="announcement-category-label">{{ categoryMeta(row.category).label }}</span>
                    </div>
                    <p class="announcement-excerpt">{{ markdownExcerpt(row.content, 80) || '（无正文）' }}</p>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="110">
              <template #default="{ row }">
                <span class="announcement-status" :class="`is-${row.status.toLowerCase()}`">
                  {{ statusMeta(row.status).label }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="已读人数" min-width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.status === 'DRAFT'" class="muted-text">—</span>
                <span v-else class="read-count">{{ row.readCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最近推送" min-width="170">
              <template #default="{ row }">
                <span class="time-text">{{ formatAnnouncementTime(row.publishedAt) }}</span>
                <small v-if="row.pushVersion > 1" class="push-version">第 {{ row.pushVersion }} 次推送</small>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="160">
              <template #default="{ row }">
                <span class="time-text">{{ formatAnnouncementTime(row.updatedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="292" fixed="right">
              <template #default="{ row }">
                <div class="announcement-action-cell">
                  <el-button size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
                  <el-button
                    v-if="row.status === 'PUBLISHED'"
                    size="small"
                    type="primary"
                    plain
                    :icon="Promotion"
                    :loading="actingId === row.id"
                    @click="handleRepush(row)"
                  >
                    重新推送
                  </el-button>
                  <el-button
                    v-else
                    size="small"
                    type="primary"
                    :icon="Position"
                    :loading="actingId === row.id"
                    @click="handlePublish(row)"
                  >
                    {{ row.status === 'OFFLINE' ? '重新发布' : '发布' }}
                  </el-button>
                  <el-dropdown trigger="click" @command="(command: MoreCommand) => handleMoreCommand(command, row)">
                    <el-button size="small" class="more-button" :icon="MoreFilled" :aria-label="`更多操作：${row.title}`" />
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="offline" :icon="Hide">
                          下线
                        </el-dropdown-item>
                        <el-dropdown-item command="delete" :icon="Delete" class="danger-dropdown-item">
                          删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="total > 0" class="pagination-bar">
          <span>共 {{ total }} 条公告</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="sizes, prev, pager, next"
            background
            @size-change="handlePageSizeChange"
            @current-change="loadAnnouncements"
          />
        </div>
      </AnimatedContent>
    </main>

    <el-dialog
      v-model="editorVisible"
      :title="editing ? '编辑公告' : '新建公告'"
      width="min(96vw, 1180px)"
      class="announcement-editor-dialog"
      :close-on-click-modal="false"
      align-center
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <div class="editor-meta-row">
          <el-form-item label="标题" prop="title" class="editor-title-item">
            <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：发票批量导入功能上线" />
          </el-form-item>
          <el-form-item label="分类" prop="category" class="editor-category-item">
            <el-select v-model="form.category" aria-label="公告分类">
              <el-option v-for="(meta, key) in ANNOUNCEMENT_CATEGORIES" :key="key" :label="meta.label" :value="key" />
            </el-select>
          </el-form-item>
        </div>

        <div class="editor-split">
          <el-form-item prop="content" class="editor-content-item">
            <template #label>
              <span class="editor-pane-label">正文 <small>支持 Markdown，Ctrl+B 加粗 / Ctrl+I 斜体</small></span>
            </template>
            <div class="editor-box">
              <div class="editor-toolbar" role="toolbar" aria-label="Markdown 格式">
                <template v-for="(group, gi) in toolbarGroups" :key="gi">
                  <span v-if="gi > 0" class="editor-toolbar-divider" aria-hidden="true" />
                  <el-tooltip
                    v-for="tool in group"
                    :key="tool.kind"
                    :content="tool.tip"
                    placement="top"
                    :show-after="300"
                  >
                    <button
                      type="button"
                      class="editor-tool"
                      :class="tool.className"
                      :aria-label="tool.tip"
                      @mousedown.prevent
                      @click="applyFormat(tool.kind)"
                    >
                      <component :is="tool.icon" v-if="tool.icon" />
                      <span v-else>{{ tool.text }}</span>
                    </button>
                  </el-tooltip>
                </template>
              </div>
              <el-input
                ref="contentInputRef"
                v-model="form.content"
                type="textarea"
                class="editor-textarea"
                :rows="18"
                maxlength="20000"
                show-word-limit
                resize="none"
                aria-label="公告正文"
                :placeholder="contentPlaceholder"
                @keydown="handleEditorKeydown"
              />
            </div>
          </el-form-item>

          <div class="editor-preview">
            <span class="editor-pane-label">用户看到的弹窗 <small>实时预览</small></span>
            <div class="editor-preview-stage">
              <AnnouncementCard
                class="editor-preview-card"
                :title="form.title.trim()"
                :category="form.category"
                :content="form.content"
                :published-at="editing?.publishedAt ?? null"
                empty-text="在左侧输入正文后，这里会显示用户看到的效果"
              >
                <template #footer>
                  <span class="ann-popup-counter">系统公告</span>
                  <span class="editor-preview-fake-btn" aria-hidden="true">我知道了</span>
                </template>
              </AnnouncementCard>
            </div>
          </div>
        </div>
      </el-form>

      <template #footer>
        <div class="editor-footer">
          <p v-if="editing?.status === 'PUBLISHED'" class="editor-hint">
            <InfoFilled aria-hidden="true" />
            修改已发布的公告不会再次弹窗；如需让所有用户重新看到，请保存后使用「重新推送」。
          </p>
          <div class="editor-footer-actions">
            <el-button @click="editorVisible = false">取消</el-button>
            <el-button :loading="saving" @click="submit(false)">
              {{ editing ? '保存修改' : '保存草稿' }}
            </el-button>
            <el-button v-if="editing?.status !== 'PUBLISHED'" type="primary" :loading="saving" @click="submit(true)">
              保存并发布
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type InputInstance } from 'element-plus'
import {
  CircleCheck,
  Delete,
  Edit,
  EditPen,
  Hide,
  InfoFilled,
  Link,
  MoreFilled,
  Plus,
  Position,
  Promotion,
  RefreshRight,
  Search
} from '@element-plus/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import AnnouncementCard from '@/components/AnnouncementCard.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import CountUp from '@/components/bits/CountUp.vue'
import {
  ANNOUNCEMENT_CATEGORIES,
  ANNOUNCEMENT_STATUSES,
  announcementApi,
  formatAnnouncementTime,
  type AdminAnnouncement,
  type AnnouncementCategory,
  type AnnouncementStats,
  type AnnouncementStatus
} from '@/api/announcement'
import { markdownExcerpt } from '@/utils/markdown'

type MoreCommand = 'offline' | 'delete'
type FormatKind = 'heading' | 'bold' | 'italic' | 'ul' | 'ol' | 'quote' | 'link' | 'code' | 'hr'

const announcements = ref<AdminAnnouncement[]>([])
const stats = ref<AnnouncementStats>({ total: 0, published: 0, draft: 0, offline: 0 })
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const actingId = ref<number | null>(null)

const filters = reactive<{
  keyword: string
  category: AnnouncementCategory | 'ALL'
  status: AnnouncementStatus | 'ALL'
}>({ keyword: '', category: 'ALL', status: 'ALL' })

const editorVisible = ref(false)
const editing = ref<AdminAnnouncement | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const contentInputRef = ref<InputInstance>()
const form = reactive<{ title: string; content: string; category: AnnouncementCategory }>({
  title: '',
  content: '',
  category: 'FEATURE'
})

const contentPlaceholder = `## 本次更新
- 新增发票批量导入
- 优化开票速度

> 如有问题请联系管理员`

const toolbarGroups: { kind: FormatKind; tip: string; text?: string; icon?: Component; className?: string }[][] = [
  [
    { kind: 'heading', tip: '小标题', text: 'H', className: 'is-heading' },
    { kind: 'bold', tip: '加粗（Ctrl+B）', text: 'B', className: 'is-bold' },
    { kind: 'italic', tip: '斜体（Ctrl+I）', text: 'I', className: 'is-italic' }
  ],
  [
    { kind: 'ul', tip: '无序列表', text: '•≡' },
    { kind: 'ol', tip: '有序列表', text: '1.' },
    { kind: 'quote', tip: '引用', text: '❝' }
  ],
  [
    { kind: 'link', tip: '插入链接', icon: Link },
    { kind: 'code', tip: '行内代码', text: '</>', className: 'is-code' },
    { kind: 'hr', tip: '分割线', text: '—' }
  ]
]

const rules: FormRules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入公告正文', trigger: 'blur' },
    { max: 20000, message: '正文不能超过 20000 个字符', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

function categoryMeta(value: AnnouncementCategory) {
  return ANNOUNCEMENT_CATEGORIES[value] ?? ANNOUNCEMENT_CATEGORIES.GENERAL
}

function statusMeta(value: AnnouncementStatus) {
  return ANNOUNCEMENT_STATUSES[value] ?? ANNOUNCEMENT_STATUSES.DRAFT
}

async function loadAnnouncements() {
  loading.value = true
  try {
    const result = await announcementApi.getAdminList({
      page: page.value,
      pageSize: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      category: filters.category === 'ALL' ? undefined : filters.category,
      status: filters.status === 'ALL' ? undefined : filters.status
    })
    announcements.value = result.records
    total.value = result.total
    if (result.stats) stats.value = result.stats
  } catch (error) {
    console.error('加载公告列表失败:', error)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  void loadAnnouncements()
}

function handlePageSizeChange() {
  page.value = 1
  void loadAnnouncements()
}

function openCreateDialog() {
  editing.value = null
  form.title = ''
  form.content = ''
  form.category = 'FEATURE'
  editorVisible.value = true
}

function openEditDialog(row: AdminAnnouncement) {
  editing.value = row
  form.title = row.title
  form.content = row.content
  form.category = row.category
  editorVisible.value = true
}

// ---------- Markdown 工具栏 ----------

function applyFormat(kind: FormatKind) {
  const textarea = contentInputRef.value?.textarea
  const value = form.content
  const start = textarea?.selectionStart ?? value.length
  const end = textarea?.selectionEnd ?? value.length
  const selected = value.slice(start, end)

  let replaceFrom = start
  let replaceTo = end
  let insert = ''
  let selectFrom = start
  let selectTo = start

  const wrap = (before: string, after: string, placeholder: string) => {
    const text = selected || placeholder
    insert = `${before}${text}${after}`
    selectFrom = start + before.length
    selectTo = selectFrom + text.length
  }

  const prefixLines = (prefix: (index: number) => string, placeholder: string) => {
    // 行级格式作用于选区覆盖的整行（无选区时为光标所在行）
    replaceFrom = value.lastIndexOf('\n', start - 1) + 1
    const lineEnd = value.indexOf('\n', end)
    replaceTo = lineEnd === -1 ? value.length : lineEnd
    const block = value.slice(replaceFrom, replaceTo) || placeholder
    insert = block.split('\n').map((line, i) => `${prefix(i)}${line}`).join('\n')
    selectFrom = replaceFrom
    selectTo = replaceFrom + insert.length
  }

  switch (kind) {
    case 'heading':
      prefixLines(() => '## ', '小标题')
      break
    case 'bold':
      wrap('**', '**', '加粗文字')
      break
    case 'italic':
      wrap('*', '*', '斜体文字')
      break
    case 'code':
      wrap('`', '`', 'code')
      break
    case 'ul':
      prefixLines(() => '- ', '列表项')
      break
    case 'ol':
      prefixLines((i) => `${i + 1}. `, '列表项')
      break
    case 'quote':
      prefixLines(() => '> ', '引用内容')
      break
    case 'link': {
      const text = selected || '链接文字'
      insert = `[${text}](https://)`
      // 有选中文字时定位到网址，否则定位到链接文字
      selectFrom = selected ? start + text.length + 3 : start + 1
      selectTo = selected ? selectFrom + 8 : selectFrom + text.length
      break
    }
    case 'hr': {
      const lead = start > 0 && value[start - 1] !== '\n' ? '\n\n' : start > 0 ? '\n' : ''
      insert = `${lead}---\n\n`
      replaceTo = start
      selectFrom = selectTo = start + insert.length
      break
    }
  }

  form.content = value.slice(0, replaceFrom) + insert + value.slice(replaceTo)
  void nextTick(() => {
    textarea?.focus()
    textarea?.setSelectionRange(selectFrom, selectTo)
  })
}

function handleEditorKeydown(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent) || !(event.ctrlKey || event.metaKey)) return
  const key = event.key.toLowerCase()
  if (key === 'b' || key === 'i') {
    event.preventDefault()
    applyFormat(key === 'b' ? 'bold' : 'italic')
  }
}

// ---------- 保存 / 状态操作 ----------

async function submit(publishAfterSave: boolean) {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = { title: form.title.trim(), content: form.content, category: form.category }
    let saved = editing.value
      ? await announcementApi.update(editing.value.id, payload)
      : await announcementApi.create(payload)
    if (publishAfterSave && saved.status !== 'PUBLISHED') {
      saved = await announcementApi.publish(saved.id)
      ElMessage.success('公告已发布，用户登录后将看到弹窗')
    } else {
      ElMessage.success(editing.value ? '公告已更新' : '草稿已保存')
    }
    editorVisible.value = false
    await loadAnnouncements()
  } catch (error) {
    console.error('保存公告失败:', error)
  } finally {
    saving.value = false
  }
}

async function confirmAndRun(
  row: AdminAnnouncement,
  message: string,
  title: string,
  confirmText: string,
  action: () => Promise<unknown>,
  successText: string
) {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: confirmText,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await action()
    ElMessage.success(successText)
    await loadAnnouncements()
  } catch (error) {
    console.error(`${title}失败:`, error)
  } finally {
    actingId.value = null
  }
}

function handlePublish(row: AdminAnnouncement) {
  return confirmAndRun(
    row,
    `发布后所有用户登录时都会弹窗看到「${row.title}」，确定发布吗？`,
    '发布公告',
    '发布',
    () => announcementApi.publish(row.id),
    '公告已发布'
  )
}

function handleRepush(row: AdminAnnouncement) {
  return confirmAndRun(
    row,
    `重新推送后，所有用户（包括已读用户）都会再次看到「${row.title}」的弹窗，确定吗？`,
    '重新推送',
    '重新推送',
    () => announcementApi.repush(row.id),
    '公告已重新推送'
  )
}

function handleOffline(row: AdminAnnouncement) {
  return confirmAndRun(
    row,
    `下线后用户将不再看到「${row.title}」，也不会出现在历史公告中，确定下线吗？`,
    '下线公告',
    '下线',
    () => announcementApi.offline(row.id),
    '公告已下线'
  )
}

function handleDelete(row: AdminAnnouncement) {
  return confirmAndRun(
    row,
    `确定删除公告「${row.title}」吗？删除后无法恢复。`,
    '删除公告',
    '删除',
    () => announcementApi.remove(row.id),
    '公告已删除'
  )
}

function handleMoreCommand(command: MoreCommand, row: AdminAnnouncement) {
  return command === 'offline' ? handleOffline(row) : handleDelete(row)
}

onMounted(loadAnnouncements)
</script>

<style scoped>
.announcement-stats {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.announcement-filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  background: var(--color-surface-muted);
  border-bottom: 1px solid var(--color-border);
}

.keyword-input {
  width: min(320px, 32vw);
}

.filter-select {
  width: 132px;
}

.refresh-button {
  width: 42px;
  padding: 0;
}

.announcement-table-scroll .el-table {
  /* 列最小宽度合计：公告 300 + 状态 110 + 已读 100 + 推送 170 + 更新 160 + 操作 292 */
  min-width: 1140px;
}

/* ---------- 标题 + 摘要 ---------- */
.announcement-title-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  padding: 2px 0;
}

.announcement-category-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 999px;
  background: var(--ann-color);
}

.announcement-title-copy {
  min-width: 0;
}

.announcement-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.announcement-title-line strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--color-text);
}

.announcement-category-label {
  flex: 0 0 auto;
  padding: 0 7px;
  border-radius: 4px;
  background: var(--ann-soft);
  color: var(--ann-color);
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
}

.announcement-excerpt {
  margin: 3px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  color: var(--color-text-muted);
}

/* ---------- 状态 ---------- */
.announcement-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.announcement-status::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--color-border-strong);
}

.announcement-status.is-published {
  color: var(--color-primary);
}

.announcement-status.is-published::before {
  background: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.announcement-status.is-offline {
  color: var(--color-warning);
}

.announcement-status.is-offline::before {
  background: var(--color-warning);
}

.read-count,
.time-text {
  font-variant-numeric: tabular-nums;
}

.read-count {
  font-weight: 600;
}

.push-version {
  display: block;
  font-size: 12px;
  color: var(--color-text-muted);
}

.muted-text {
  color: var(--color-text-muted);
}

.announcement-action-cell {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
}

.announcement-action-cell .el-button + .el-button {
  margin-left: 0;
}

.more-button {
  width: 34px;
  padding: 0;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 68px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* ---------- 编辑器 ---------- */
.editor-meta-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: 16px;
}

.editor-category-item :deep(.el-select) {
  width: 100%;
}

.editor-split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}

.editor-pane-label {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  line-height: 22px;
}

.editor-pane-label small {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-muted);
}

.editor-content-item {
  margin-bottom: 0;
}

.editor-content-item :deep(.el-form-item__content) {
  display: block;
}

.editor-box {
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.editor-box:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.editor-content-item.is-error .editor-box {
  border-color: var(--color-danger);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-muted);
}

.editor-toolbar-divider {
  width: 1px;
  height: 18px;
  margin: 0 6px;
  background: var(--color-border-strong);
}

.editor-tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}

.editor-tool svg {
  width: 15px;
  height: 15px;
}

.editor-tool:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.editor-tool:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.editor-tool.is-heading,
.editor-tool.is-bold {
  font-weight: 800;
}

.editor-tool.is-italic {
  font-style: italic;
  font-family: Georgia, 'Times New Roman', serif;
}

.editor-tool.is-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.editor-textarea :deep(.el-textarea__inner) {
  border: 0;
  border-radius: 0;
  box-shadow: none;
  padding: 12px 14px;
  font-family: 'JetBrains Mono', Consolas, 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.7;
}

.editor-textarea :deep(.el-input__count) {
  background: transparent;
}

.editor-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.editor-preview-stage {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: 0;
  padding: 22px;
  border-radius: 8px;
  background:
    radial-gradient(circle at 1px 1px, rgba(32, 37, 43, 0.08) 1px, transparent 0) 0 0 / 14px 14px,
    #e9edeb;
  overflow: auto;
}

.editor-preview-card {
  width: 100%;
  max-width: 520px;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.16);
}

.editor-preview-card :deep(.ann-card-body) {
  max-height: 300px;
}

.editor-preview-fake-btn {
  margin-left: auto;
  padding: 6px 15px;
  border-radius: 6px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 13px;
  font-weight: 500;
  user-select: none;
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.editor-footer-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.editor-footer-actions .el-button + .el-button {
  margin-left: 0;
}

.editor-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 7px 12px;
  border-radius: 6px;
  background: var(--color-accent-soft);
  color: var(--color-warning);
  font-size: 13px;
  text-align: left;
}

.editor-hint svg {
  flex: 0 0 auto;
  width: 15px;
  height: 15px;
}

@media (max-width: 1100px) {
  .announcement-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .announcement-filter-bar {
    flex-wrap: wrap;
  }

  .keyword-input {
    width: 100%;
  }

  .filter-select {
    flex: 1 1 130px;
    width: auto;
  }

  .editor-meta-row,
  .editor-split {
    grid-template-columns: 1fr;
  }

  .editor-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .pagination-bar {
    align-items: flex-start;
    flex-direction: column;
    overflow-x: auto;
  }
}
</style>

<style>
/* el-dropdown 菜单被传送到 body，需全局样式 */
.el-dropdown-menu__item.danger-dropdown-item {
  color: var(--color-danger);
}

.el-dropdown-menu__item.danger-dropdown-item:not(.is-disabled):hover,
.el-dropdown-menu__item.danger-dropdown-item:not(.is-disabled):focus {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}
</style>
