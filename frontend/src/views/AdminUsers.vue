<template>
  <div class="workspace-shell">
    <AppHeader title="用户管理" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><User /></span>
            <div class="stat-copy">
              <span class="stat-label">用户总数</span>
              <strong class="stat-value"><CountUp :value="stats.totalUsers" /></strong>
            </div>
            <p class="stat-note">全部有效账号</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">启用账号</span>
              <strong class="stat-value"><CountUp :value="stats.enabledUsers" /></strong>
            </div>
            <p class="stat-note">可以正常登录</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon danger"><CircleClose /></span>
            <div class="stat-copy">
              <span class="stat-label">禁用账号</span>
              <strong class="stat-value"><CountUp :value="stats.disabledUsers" /></strong>
            </div>
            <p class="stat-note">凭证已即时失效</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Avatar /></span>
            <div class="stat-copy">
              <span class="stat-label">管理员</span>
              <strong class="stat-value"><CountUp :value="stats.adminUsers" /></strong>
            </div>
            <p class="stat-note">拥有后台管理权限</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">开票员</span>
              <strong class="stat-value"><CountUp :value="stats.clerkUsers" /></strong>
            </div>
            <p class="stat-note">负责发票处理</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><UserFilled /></span>
            <div>
              <h2>账号目录</h2>
              <p>维护角色、登录状态与访问凭证</p>
            </div>
          </div>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">创建用户</el-button>
        </div>

        <div class="user-filter-bar">
          <el-input
            v-model="filters.keyword"
            class="keyword-input"
            clearable
            :prefix-icon="Search"
            aria-label="按用户名或备注搜索"
            placeholder="搜索用户名或备注"
            maxlength="50"
            @keyup.enter="applyFilters"
            @clear="applyFilters"
          />
          <el-select
            v-model="filters.role"
            class="filter-select"
            aria-label="筛选用户角色"
            @change="applyFilters"
          >
            <el-option label="全部角色" value="ALL" />
            <el-option label="普通用户" value="USER" />
            <el-option label="开票员" value="INVOICE_CLERK" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
          <el-select
            v-model="filters.status"
            class="filter-select"
            aria-label="筛选账号状态"
            @change="applyFilters"
          >
            <el-option label="全部状态" value="ALL" />
            <el-option label="已启用" value="ENABLED" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
          <el-button :icon="Search" @click="applyFilters">查询</el-button>
          <el-tooltip content="刷新列表" placement="top">
            <el-button
              class="refresh-button"
              :icon="RefreshRight"
              :loading="loading"
              aria-label="刷新用户列表"
              @click="loadUsers"
            />
          </el-tooltip>
        </div>

        <div v-if="!loading && users.length === 0" class="table-empty-state">
          <span><User /></span>
          <strong>当前条件下暂无用户</strong>
        </div>
        <div v-else class="table-scroll user-table-scroll">
          <el-table :data="users" v-loading="loading" row-key="id">
            <el-table-column prop="username" label="用户名" min-width="220">
              <template #default="{ row }">
                <div class="username-cell">
                  <span class="row-avatar">{{ row.username.slice(0, 1).toUpperCase() }}</span>
                  <div class="username-info">
                    <div class="username-title">
                      <strong>{{ row.username }}</strong>
                      <small v-if="row.self">当前账号</small>
                    </div>

                    <!-- 备注编辑态 -->
                    <div v-if="editingRemarkId === row.id" class="inline-remark-editor" @click.stop>
                      <el-input
                        ref="inlineRemarkInputRef"
                        v-model="currentEditingRemark"
                        size="small"
                        placeholder="输入备注，最多200字"
                        maxlength="200"
                        clearable
                        @keyup.enter="saveInlineRemark(row)"
                        @keyup.esc="cancelInlineRemark"
                      />
                      <div class="inline-remark-actions">
                        <el-button size="small" @click="cancelInlineRemark">取消</el-button>
                        <el-button
                          size="small"
                          type="primary"
                          :loading="remarkLoadingId === row.id"
                          @click="saveInlineRemark(row)"
                        >
                          保存
                        </el-button>
                      </div>
                    </div>

                    <!-- 备注展示态 -->
                    <div v-else class="remark-display" @click="startEditRemark(row)">
                      <span
                        v-if="row.remark"
                        class="remark-filled"
                        :title="`备注: ${row.remark} (点击修改)`"
                      >
                        <el-icon class="remark-icon"><EditPen /></el-icon>
                        <span class="remark-val">{{ row.remark }}</span>
                      </span>
                      <span
                        v-else
                        class="remark-placeholder"
                        title="点击添加备注"
                      >
                        <el-icon class="remark-icon"><Plus /></el-icon>
                        <span>添加备注</span>
                      </span>
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="role" label="角色" width="160">
              <template #default="{ row }">
                <el-tooltip :disabled="!row.self" content="不能修改当前账号的角色" placement="top">
                  <span class="inline-control-wrap">
                    <el-select
                      class="role-select"
                      size="small"
                      :model-value="row.role"
                      :disabled="row.self || roleLoadingId === row.id"
                      :loading="roleLoadingId === row.id"
                      :aria-label="`调整 ${row.username} 的角色`"
                      @change="(role: UserRole) => confirmRoleChange(row, role)"
                    >
                      <el-option label="普通用户" value="USER" />
                      <el-option label="开票员" value="INVOICE_CLERK" />
                      <el-option label="管理员" value="ADMIN" />
                    </el-select>
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="142" align="center">
              <template #default="{ row }">
                <el-tooltip :disabled="!row.self" content="不能修改当前账号的状态" placement="top">
                  <span class="status-switch-wrap">
                    <el-switch
                      :model-value="row.enabled"
                      :disabled="row.self || statusLoadingId === row.id"
                      :loading="statusLoadingId === row.id"
                      inline-prompt
                      active-text="启用"
                      inactive-text="禁用"
                      :aria-label="`切换 ${row.username} 的账号状态`"
                      @change="(enabled: string | number | boolean) => confirmStatusChange(row, Boolean(enabled))"
                    />
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="quota" label="额度情况" min-width="210" align="left">
              <template #default="{ row }">
                <template v-if="row.role === 'USER'">
                  <div class="quota-cell">
                    <div class="quota-cell-balance">
                      <span class="quota-balance-label">余额:</span>
                      <span
                        class="quota-balance-value"
                        title="点击管理额度"
                        @click="openQuotaDialog(row)"
                      >¥{{ row.quota ? Number(row.quota.balance).toFixed(2) : '0.00' }}</span>
                      <el-button
                        size="small"
                        type="primary"
                        link
                        class="quota-manage-btn"
                        :aria-label="`管理 ${row.username} 的额度`"
                        @click="openQuotaDialog(row)"
                      >
                        管理
                      </el-button>
                    </div>
                    <div class="quota-cell-meta">
                      <span>充值 ¥{{ row.quota ? Number(row.quota.totalRecharged).toFixed(2) : '0.00' }}</span>
                      <span>扣除 ¥{{ row.quota ? Number(row.quota.totalDeducted).toFixed(2) : '0.00' }}</span>
                    </div>
                  </div>
                </template>
                <span v-else style="color: var(--color-text-muted);">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="172">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="186" align="center" fixed="right">
              <template #default="{ row }">
                <div class="user-action-cell">
                  <el-tooltip v-if="row.role === 'USER'" content="OpenAPI 开发者密钥管理" placement="top">
                    <el-button
                      type="primary"
                      link
                      class="user-action-btn"
                      :icon="Connection"
                      :aria-label="`管理 ${row.username} 的 API Key`"
                      @click="openApiKeyDialog(row)"
                    >
                      API Key
                    </el-button>
                  </el-tooltip>
                  <span v-if="row.role === 'USER'" class="action-divider" />
                  <el-tooltip :content="row.self ? '重置自己的密码后需重新登录' : '设置新密码'" placement="top">
                    <el-button
                      type="primary"
                      link
                      class="user-action-btn"
                      :icon="Key"
                      :aria-label="`重置 ${row.username} 的密码`"
                      @click="openPasswordDialog(row)"
                    >
                      重置密码
                    </el-button>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="total > 0" class="pagination-bar">
          <span>共 {{ total }} 个用户</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="sizes, prev, pager, next, jumper"
            background
            @size-change="handlePageSizeChange"
            @current-change="loadUsers"
          />
        </div>
      </AnimatedContent>
    </main>

    <el-dialog v-model="createDialogVisible" title="创建用户" width="min(92vw, 460px)" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" maxlength="20" autocomplete="off" placeholder="2-20 位汉字、字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" maxlength="200" autocomplete="off" placeholder="可选，由管理员填写用户备注" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password maxlength="20" autocomplete="new-password" placeholder="6-20 位，必须包含字母和数字" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-segmented v-model="createForm.role" :options="roleOptions" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="createSubmitting" @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateUser">创建用户</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="min(92vw, 460px)" destroy-on-close>
      <p class="dialog-context">
        正在为 <strong>{{ passwordTarget?.username }}</strong> 设置新密码。
        <span v-if="passwordTarget?.self">保存后当前登录将立即失效。</span>
      </p>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="passwordForm.password" type="password" show-password maxlength="20" autocomplete="new-password" placeholder="6-20 位，必须包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password maxlength="20" autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="passwordSubmitting" @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="passwordSubmitting" @click="submitPasswordReset">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 额度管理对话框 -->
    <el-dialog v-model="quotaDialogVisible" title="额度管理" width="min(92vw, 700px)" destroy-on-close>
      <template v-if="quotaTarget && quotaInfo">
        <p class="dialog-context">
          正在管理 <strong>{{ quotaTarget.username }}</strong> 的额度。
        </p>
        
        <div class="quota-summary">
          <div class="quota-summary-item">
            <span class="label">当前余额</span>
            <span class="value balance">{{ quotaInfo.balance.toFixed(2) }}</span>
          </div>
          <div class="quota-summary-item">
            <span class="label">总充值</span>
            <span class="value">{{ quotaInfo.totalRecharged.toFixed(2) }}</span>
          </div>
          <div class="quota-summary-item">
            <span class="label">总扣除</span>
            <span class="value">{{ quotaInfo.totalDeducted.toFixed(2) }}</span>
          </div>
        </div>

        <el-tabs v-model="quotaActiveTab">
          <el-tab-pane label="充值" name="recharge">
            <el-form ref="quotaFormRef" :model="quotaForm" label-position="top">
              <el-form-item label="充值金额" prop="amount" :rules="[{ required: true, message: '请输入充值金额' }, { type: 'number', min: 0.01, message: '充值金额必须大于0' }]">
                <el-input-number v-model="quotaForm.amount" :min="0.01" :max="999999.99" :precision="2" :step="100" controls-position="right" style="width: 100%" />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="quotaForm.remark" maxlength="200" placeholder="可选，最多200个字符" />
              </el-form-item>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="调整" name="adjust">
            <el-form ref="quotaFormRef" :model="quotaForm" label-position="top">
              <el-form-item label="调整金额" prop="amount" :rules="[{ required: true, message: '请输入调整金额' }]">
                <el-input-number v-model="quotaForm.amount" :precision="2" :step="100" controls-position="right" style="width: 100%" />
                <div class="form-tip">正数表示增加，负数表示减少</div>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="quotaForm.remark" maxlength="200" placeholder="可选，最多200个字符" />
              </el-form-item>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="历史记录" name="history">
            <el-table :data="paginatedQuotaTransactions" stripe max-height="300">
              <el-table-column prop="transactionType" label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="getTransactionTypeTag(row.transactionType)" size="small">
                    {{ getTransactionTypeLabel(row.transactionType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="金额" width="120">
                <template #default="{ row }">
                  <span :class="row.amount > 0 ? 'amount-positive' : 'amount-negative'">
                    {{ row.amount > 0 ? '+' : '' }}{{ row.amount.toFixed(2) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="balanceBefore" label="变更前" width="100">
                <template #default="{ row }">{{ row.balanceBefore.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="balanceAfter" label="变更后" width="100">
                <template #default="{ row }">{{ row.balanceAfter.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" />
              <el-table-column prop="createdAt" label="时间" width="160">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <div v-if="quotaTransactions.length > 0" class="dialog-pagination-bar">
              <span>共 {{ quotaTransactions.length }} 条记录</span>
              <el-pagination
                v-model:current-page="quotaHistoryPage"
                v-model:page-size="quotaHistoryPageSize"
                :page-sizes="[5, 10, 20]"
                :total="quotaTransactions.length"
                layout="sizes, prev, pager, next"
                small
                background
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer>
        <el-button @click="quotaDialogVisible = false">关闭</el-button>
        <el-button v-if="quotaActiveTab === 'recharge'" type="primary" :loading="quotaSubmitting" @click="submitRechargeQuota">确认充值</el-button>
        <el-button v-if="quotaActiveTab === 'adjust'" type="warning" :loading="quotaSubmitting" @click="submitAdjustQuota">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- API Key 管理弹窗 -->
    <el-dialog
      v-model="apiKeyDialogVisible"
      :title="`OpenAPI 开发者密钥 - ${selectedUserForApiKey?.username || ''}`"
      width="min(92vw, 520px)"
      destroy-on-close
    >
      <div v-if="selectedUserForApiKey" class="api-key-dialog-content">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 18px"
        >
          <template #title>
            此 API Key 用于外部业务系统调用 OpenAPI（如提单、查单、查额度）。请妥善保管。
          </template>
        </el-alert>

        <div style="background: var(--color-surface-subtle); padding: 16px; border-radius: 8px; margin-bottom: 16px; border: 1px solid var(--color-border);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
            <span style="font-size: 13px; font-weight: 500;">当前 API Key</span>
            <el-tag :type="selectedUserForApiKey.apiKeyEnabled !== false ? 'success' : 'danger'" size="small">
              {{ selectedUserForApiKey.apiKeyEnabled !== false ? '已启用' : '已禁用' }}
            </el-tag>
          </div>
          <div style="display: flex; gap: 8px; align-items: center;">
            <el-input
              :model-value="selectedUserForApiKey.apiKey || '暂未生成 API Key'"
              readonly
              :type="showApiKeyText ? 'text' : 'password'"
            />
            <el-button
              v-if="selectedUserForApiKey.apiKey"
              :icon="CopyDocument"
              @click="handleCopyApiKey(selectedUserForApiKey.apiKey)"
            >
              复制
            </el-button>
          </div>
        </div>

        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div style="display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 13px;">密钥状态开关：</span>
            <el-switch
              v-if="selectedUserForApiKey.apiKey"
              :model-value="selectedUserForApiKey.apiKeyEnabled !== false"
              :loading="apiKeyStatusLoading"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(val: string | number | boolean) => handleToggleApiKeyStatus(Boolean(val))"
            />
            <span v-else style="font-size: 12px; color: var(--color-text-muted);">需先生成密钥</span>
          </div>

          <el-button
            type="primary"
            :loading="apiKeyGenerating"
            @click="handleGenerateApiKey"
          >
            {{ selectedUserForApiKey.apiKey ? '重置生成新 Key' : '生成 API Key' }}
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="apiKeyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Avatar,
  CircleCheck,
  CircleClose,
  Connection,
  CopyDocument,
  EditPen,
  Key,
  Plus,
  RefreshRight,
  Search,
  Tickets,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { userApi, type ManagedUser, type UserQuery, type UserRole, type UserStats } from '@/api/user'
import { quotaApi, type UserQuota, type QuotaTransaction, type RechargeQuotaRequest, type AdjustQuotaRequest } from '@/api/quota'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { useUserStore } from '@/stores/user'
import { validatePassword, validateUsername } from '@/utils/userValidation'
import { generateIdempotencyKey } from '@/utils/idempotency'

type RoleFilter = UserRole | 'ALL'
type StatusFilter = 'ALL' | 'ENABLED' | 'DISABLED'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const users = ref<ManagedUser[]>([])
const stats = ref<UserStats>({ totalUsers: 0, enabledUsers: 0, disabledUsers: 0, adminUsers: 0, clerkUsers: 0 })
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const roleLoadingId = ref<number | null>(null)
const statusLoadingId = ref<number | null>(null)

const filters = reactive<{ keyword: string; role: RoleFilter; status: StatusFilter }>({
  keyword: '',
  role: 'ALL',
  status: 'ALL'
})

const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<{ username: string; password: string; role: UserRole; remark: string }>({
  username: '',
  password: '',
  role: 'USER',
  remark: ''
})
const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '开票员', value: 'INVOICE_CLERK' },
  { label: '管理员', value: 'ADMIN' }
]

const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordTarget = ref<ManagedUser | null>(null)
const passwordForm = reactive({ password: '', confirmPassword: '' })

// 额度管理相关状态
const quotaDialogVisible = ref(false)
const quotaSubmitting = ref(false)
const quotaFormRef = ref<FormInstance>()
const quotaTarget = ref<ManagedUser | null>(null)
const quotaInfo = ref<UserQuota | null>(null)
const quotaTransactions = ref<QuotaTransaction[]>([])
const quotaActiveTab = ref('recharge')
const quotaForm = reactive<{ amount: number; remark: string }>({
  amount: 0,
  remark: ''
})
const quotaHistoryPage = ref(1)
const quotaHistoryPageSize = ref(10)

const paginatedQuotaTransactions = computed(() => {
  const start = (quotaHistoryPage.value - 1) * quotaHistoryPageSize.value
  return quotaTransactions.value.slice(start, start + quotaHistoryPageSize.value)
})
const quotaPendingIdempotencyKey = ref<string | null>(null)
let quotaDialogRequestId = 0

// API Key 管理相关
const apiKeyDialogVisible = ref(false)
const selectedUserForApiKey = ref<ManagedUser | null>(null)
const apiKeyGenerating = ref(false)
const apiKeyStatusLoading = ref(false)
const showApiKeyText = ref(true)

const openApiKeyDialog = (row: ManagedUser) => {
  selectedUserForApiKey.value = row
  apiKeyDialogVisible.value = true
}

const handleCopyApiKey = async (key: string) => {
  let copied = false
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(key)
      copied = true
    } catch {
      // Fall through to textarea fallback
    }
  }
  if (!copied) {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = key
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      copied = document.execCommand('copy')
      document.body.removeChild(textarea)
    } catch {
      copied = false
    }
  }
  if (copied) {
    ElMessage.success('API Key 已复制到剪贴板')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const handleGenerateApiKey = async () => {
  if (!selectedUserForApiKey.value || apiKeyGenerating.value) return
  const isReset = Boolean(selectedUserForApiKey.value.apiKey)
  if (isReset) {
    try {
      await ElMessageBox.confirm(
        '重置后旧 API Key 将立即失效，外部系统调用将中断。确定要生成新的 API Key 吗？',
        '重置 API Key 警告',
        {
          confirmButtonText: '确定重置',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }

  apiKeyGenerating.value = true
  try {
    const res = await userApi.generateApiKey(selectedUserForApiKey.value.id)
    selectedUserForApiKey.value.apiKey = res.apiKey
    selectedUserForApiKey.value.apiKeyEnabled = res.apiKeyEnabled
    const row = users.value.find((u) => u.id === selectedUserForApiKey.value?.id)
    if (row) {
      row.apiKey = res.apiKey
      row.apiKeyEnabled = res.apiKeyEnabled
    }
    ElMessage.success(isReset ? 'API Key 重置成功' : 'API Key 生成成功')
  } catch (error: any) {
    ElMessage.error(error?.message || '生成 API Key 失败')
  } finally {
    apiKeyGenerating.value = false
  }
}

const handleToggleApiKeyStatus = async (enabled: boolean) => {
  if (!selectedUserForApiKey.value || apiKeyStatusLoading.value) return
  apiKeyStatusLoading.value = true
  try {
    const res = await userApi.updateApiKeyStatus(selectedUserForApiKey.value.id, enabled)
    selectedUserForApiKey.value.apiKeyEnabled = res.apiKeyEnabled
    const row = users.value.find((u) => u.id === selectedUserForApiKey.value?.id)
    if (row) {
      row.apiKeyEnabled = res.apiKeyEnabled
    }
    ElMessage.success(enabled ? 'API Key 已启用' : 'API Key 已禁用')
  } catch (error: any) {
    ElMessage.error(error?.message || '更新 API Key 状态失败')
  } finally {
    apiKeyStatusLoading.value = false
  }
}

const fieldValidator = (validator: (value: string) => string | null) =>
  (_rule: unknown, value: string, callback: (error?: Error) => void) => {
    const message = validator(value)
    callback(message ? new Error(message) : undefined)
  }

const createRules: FormRules = {
  username: [{ validator: fieldValidator(validateUsername), trigger: 'blur' }],
  password: [{ validator: fieldValidator(validatePassword), trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  remark: [{ max: 200, message: '备注不能超过 200 个字符', trigger: 'blur' }]
}

const passwordRules: FormRules = {
  password: [{ validator: fieldValidator(validatePassword), trigger: 'blur' }],
  confirmPassword: [{
    validator: (_rule, value: string, callback) => {
      if (!value) callback(new Error('请再次输入新密码'))
      else if (value !== passwordForm.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    },
    trigger: 'blur'
  }]
}

const buildQuery = (): UserQuery => ({
  page: page.value,
  pageSize: pageSize.value,
  keyword: filters.keyword.trim() || undefined,
  role: filters.role === 'ALL' ? undefined : filters.role,
  enabled: filters.status === 'ALL' ? undefined : filters.status === 'ENABLED'
})

const loadUsers = async () => {
  loading.value = true
  try {
    const result = await userApi.getUsers(buildQuery())
    users.value = result.users
    total.value = result.total
    stats.value = result.stats
    if (result.totalPages > 0 && page.value > result.totalPages) {
      page.value = result.totalPages
      await loadUsers()
    }
  } catch (error) {
    console.error('加载用户列表失败', error)
  } finally {
    loading.value = false
  }
}

const applyFilters = () => {
  page.value = 1
  loadUsers()
}

const handlePageSizeChange = () => {
  page.value = 1
  loadUsers()
}

const editingRemarkId = ref<number | null>(null)
const currentEditingRemark = ref('')
const remarkLoadingId = ref<number | null>(null)
const inlineRemarkInputRef = ref<any>()

const startEditRemark = (row: ManagedUser) => {
  editingRemarkId.value = row.id
  currentEditingRemark.value = row.remark || ''
  setTimeout(() => {
    inlineRemarkInputRef.value?.focus?.()
  }, 50)
}

const cancelInlineRemark = () => {
  editingRemarkId.value = null
  currentEditingRemark.value = ''
}

const saveInlineRemark = async (row: ManagedUser) => {
  if (remarkLoadingId.value !== null) return
  const newRemark = currentEditingRemark.value.trim()
  remarkLoadingId.value = row.id
  try {
    const updated = await userApi.updateRemark(row.id, newRemark)
    row.remark = updated.remark
    ElMessage.success('备注更新成功')
    editingRemarkId.value = null
    currentEditingRemark.value = ''
  } catch (error: any) {
    console.error('更新备注失败', error)
    ElMessage.error(error?.message || '更新备注失败')
  } finally {
    remarkLoadingId.value = null
  }
}

const openCreateDialog = () => {
  createForm.username = ''
  createForm.password = ''
  createForm.role = 'USER'
  createForm.remark = ''
  createDialogVisible.value = true
}

const submitCreateUser = async () => {
  if (!createFormRef.value || createSubmitting.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  createSubmitting.value = true
  try {
    await userApi.createUser({
      username: createForm.username,
      password: createForm.password,
      role: createForm.role,
      remark: createForm.remark.trim() || undefined
    })
    ElMessage.success('用户创建成功')
    createDialogVisible.value = false
    page.value = 1
    await loadUsers()
  } catch (error) {
    console.error('创建用户失败', error)
  } finally {
    createSubmitting.value = false
  }
}

const confirmRoleChange = async (user: ManagedUser, role: UserRole) => {
  if (role === user.role || user.self || roleLoadingId.value !== null) return
  const roleTextMap: Record<UserRole, string> = {
    ADMIN: '管理员',
    INVOICE_CLERK: '开票员',
    USER: '普通用户'
  }
  try {
    await ElMessageBox.confirm(
      `确认将“${user.username}”调整为${roleTextMap[role]}？该用户现有登录将立即失效。`,
      '调整用户角色',
      { type: 'warning', confirmButtonText: '确认调整', cancelButtonText: '取消' }
    )
    roleLoadingId.value = user.id
    await userApi.updateRole(user.id, role)
    ElMessage.success('角色更新成功')
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('角色更新失败', error)
  } finally {
    roleLoadingId.value = null
  }
}

const confirmStatusChange = async (user: ManagedUser, enabled: boolean) => {
  if (enabled === user.enabled || user.self || statusLoadingId.value !== null) return
  try {
    await ElMessageBox.confirm(
      enabled
        ? `确认启用“${user.username}”？启用后该用户可以重新登录。`
        : `确认禁用“${user.username}”？该用户现有登录将立即失效。`,
      enabled ? '启用账号' : '禁用账号',
      {
        type: enabled ? 'info' : 'warning',
        confirmButtonText: enabled ? '确认启用' : '确认禁用',
        cancelButtonText: '取消'
      }
    )
    statusLoadingId.value = user.id
    await userApi.updateStatus(user.id, enabled)
    ElMessage.success(enabled ? '账号已启用' : '账号已禁用')
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('账号状态更新失败', error)
  } finally {
    statusLoadingId.value = null
  }
}

const openPasswordDialog = (user: ManagedUser) => {
  passwordTarget.value = user
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

const submitPasswordReset = async () => {
  if (!passwordFormRef.value || !passwordTarget.value || passwordSubmitting.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  const target = passwordTarget.value
  passwordSubmitting.value = true
  try {
    await userApi.resetPassword(target.id, passwordForm.password)
    passwordDialogVisible.value = false
    if (target.self) {
      userStore.logout()
      ElMessage.success('密码已重置，请使用新密码重新登录')
      await router.replace('/login')
      return
    }
    ElMessage.success('密码重置成功')
  } catch (error) {
    console.error('密码重置失败', error)
  } finally {
    passwordSubmitting.value = false
  }
}

const formatDate = (value: string) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

// 额度管理相关函数
const openQuotaDialog = async (user: ManagedUser) => {
  const requestId = ++quotaDialogRequestId
  quotaTarget.value = user
  quotaActiveTab.value = 'recharge'
  quotaForm.amount = 0
  quotaForm.remark = ''
  quotaPendingIdempotencyKey.value = null
  quotaHistoryPage.value = 1
  
  try {
    const [userQuota, transactions] = await Promise.all([
      quotaApi.getUserQuota(user.id),
      quotaApi.getUserTransactions(user.id)
    ])
    if (requestId !== quotaDialogRequestId || quotaTarget.value?.id !== user.id) return
    quotaInfo.value = userQuota
    quotaTransactions.value = transactions
    const row = users.value.find(u => u.id === user.id)
    if (row) row.quota = { ...userQuota }
    quotaDialogVisible.value = true
  } catch (error) {
    if (requestId !== quotaDialogRequestId) return
    console.error('加载额度信息失败', error)
    ElMessage.error('加载额度信息失败')
  }
}

const submitRechargeQuota = async () => {
  if (!quotaFormRef.value || !quotaTarget.value || quotaSubmitting.value) return
  const valid = await quotaFormRef.value.validate().catch(() => false)
  if (!valid) return

  const target = quotaTarget.value
  quotaSubmitting.value = true
  try {
    const data: RechargeQuotaRequest = {
      amount: quotaForm.amount,
      remark: quotaForm.remark || undefined
    }
    const idempotencyKey = quotaPendingIdempotencyKey.value || generateIdempotencyKey('quota')
    quotaPendingIdempotencyKey.value = idempotencyKey
    const updatedQuota = await quotaApi.rechargeQuota(target.id, data, idempotencyKey)
    quotaInfo.value = updatedQuota
    // Sync the inline quota snapshot in the table row
    const row = users.value.find(u => u.id === target.id)
    if (row) row.quota = { ...updatedQuota }
    try {
      quotaTransactions.value = await quotaApi.getUserTransactions(target.id)
    } catch (refreshError) {
      console.error('刷新额度历史失败', refreshError)
      ElMessage.warning('充值成功，但额度历史刷新失败')
    }
    ElMessage.success('充值成功')
    quotaPendingIdempotencyKey.value = null
    quotaForm.amount = 0
    quotaForm.remark = ''
  } catch (error) {
    console.error('充值失败', error)
    ElMessage.error('充值失败')
  } finally {
    quotaSubmitting.value = false
  }
}

const submitAdjustQuota = async () => {
  if (!quotaFormRef.value || !quotaTarget.value || quotaSubmitting.value) return
  const valid = await quotaFormRef.value.validate().catch(() => false)
  if (!valid) return

  const target = quotaTarget.value
  quotaSubmitting.value = true
  try {
    const data: AdjustQuotaRequest = {
      amount: quotaForm.amount,
      remark: quotaForm.remark || undefined
    }
    const idempotencyKey = quotaPendingIdempotencyKey.value || generateIdempotencyKey('quota')
    quotaPendingIdempotencyKey.value = idempotencyKey
    const updatedQuota = await quotaApi.adjustQuota(target.id, data, idempotencyKey)
    quotaInfo.value = updatedQuota
    // Sync the inline quota snapshot in the table row
    const row = users.value.find(u => u.id === target.id)
    if (row) row.quota = { ...updatedQuota }
    try {
      quotaTransactions.value = await quotaApi.getUserTransactions(target.id)
    } catch (refreshError) {
      console.error('刷新额度历史失败', refreshError)
      ElMessage.warning('调整成功，但额度历史刷新失败')
    }
    ElMessage.success('调整成功')
    quotaPendingIdempotencyKey.value = null
    quotaForm.amount = 0
    quotaForm.remark = ''
  } catch (error) {
    console.error('调整失败', error)
    ElMessage.error('调整失败')
  } finally {
    quotaSubmitting.value = false
  }
}

const getTransactionTypeLabel = (type: string) => {
  const labels: Record<string, string> = {
    RECHARGE: '充值',
    DEDUCT: '扣除',
    ADJUST: '调整'
  }
  return labels[type] || type
}

const getTransactionTypeTag = (type: string) => {
  const tags: Record<string, any> = {
    RECHARGE: 'success',
    DEDUCT: 'danger',
    ADJUST: 'warning'
  }
  return tags[type] || 'info'
}

watch(
  () => [quotaTarget.value?.id, quotaActiveTab.value, quotaForm.amount, quotaForm.remark],
  () => {
    // Reset the pending idempotency key whenever the target user, active tab, or form
    // fields change. This ensures each logically distinct operation gets a fresh key,
    // while a failed submission with unchanged fields retains its key so the user can
    // safely retry and the server will deduplicate the request correctly.
    if (!quotaSubmitting.value) quotaPendingIdempotencyKey.value = null
  }
)

onMounted(loadUsers)
</script>

<style scoped>
.stat-icon.danger {
  color: var(--color-danger);
  background: #fbecea;
}

.user-filter-bar {
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

.user-table-scroll .el-table {
  /* Minimum width accounts for all columns:
   * username(220) + role(160) + status(142) + quota(210) + createdAt(172) + action(186) = 1090px */
  min-width: 1080px;
}

.username-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
}

.username-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.username-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.username-title strong {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 650;
}

.username-title small {
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 600;
}

.remark-display {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  width: fit-content;
}

.remark-filled {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #606266);
  background: var(--color-surface-muted, #f4f5f7);
  padding: 1px 6px;
  border-radius: 4px;
  max-width: 170px;
  transition: all 0.15s ease;
  border: 1px solid transparent;
}

.remark-filled:hover {
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.remark-val {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remark-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--color-text-muted, #909399);
  padding: 1px 4px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.remark-placeholder:hover {
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.remark-icon {
  font-size: 11px;
  flex-shrink: 0;
}

.inline-remark-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px;
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-primary, #409eff);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  width: 200px;
  margin-top: 2px;
}

.inline-remark-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.inline-control-wrap,
.status-switch-wrap {
  display: inline-flex;
}

.role-select {
  width: 126px;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 68px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.pagination-bar > span {
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.dialog-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.dialog-pagination-bar > span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.dialog-context {
  margin: 0 0 20px;
  padding: 12px 14px;
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
  border-left: 3px solid var(--color-accent);
  font-size: 13px;
  line-height: 1.65;
}

.dialog-context span {
  display: block;
  color: var(--color-danger);
}

:deep(.el-segmented) {
  width: 100%;
}

/* 额度管理对话框样式 */
.quota-summary {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--color-surface-muted);
  border-radius: 8px;
}

.quota-summary-item {
  flex: 1;
  text-align: center;
}

.quota-summary-item .label {
  display: block;
  margin-bottom: 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.quota-summary-item .value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
}

.quota-summary-item .value.balance {
  color: var(--color-primary);
  font-size: 24px;
}

.form-tip {
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.amount-positive {
  color: var(--color-success);
}

.amount-negative {
  color: var(--color-danger);
}

/* 额度情况列 */
.quota-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.quota-cell-balance {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quota-balance-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.quota-balance-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary);
  cursor: pointer;
  transition: opacity 0.15s;
}

.quota-balance-value:hover {
  opacity: 0.75;
  text-decoration: underline;
}

.quota-manage-btn {
  padding: 0 4px;
  font-size: 12px;
  height: auto;
}

.quota-cell-meta {
  display: flex;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.user-action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 100%;
  white-space: nowrap;
}

.user-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  font-weight: 500;
  padding: 4px 6px;
  margin: 0 !important;
  border-radius: 4px;
  height: 28px;
  transition: all 0.15s ease;
}

.user-action-btn:hover {
  background: var(--color-primary-subtle, rgba(18, 113, 91, 0.08));
}

.action-divider {
  display: inline-block;
  width: 1px;
  height: 12px;
  margin: 0 4px;
  background: var(--color-border);
  flex-shrink: 0;
}

@media (max-width: 820px) {
  .user-filter-bar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .keyword-input {
    width: 100%;
  }

  .filter-select {
    flex: 1 1 130px;
    width: auto;
  }

  .pagination-bar {
    align-items: flex-start;
    flex-direction: column;
    overflow-x: auto;
  }
}

@media (max-width: 480px) {
  .user-filter-bar {
    padding: 14px;
  }

  .user-filter-bar > .el-button:not(.refresh-button) {
    flex: 1;
  }

  .pagination-bar {
    padding: 12px 14px;
  }
}
</style>
