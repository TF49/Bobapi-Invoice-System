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
            aria-label="按用户名搜索"
            placeholder="搜索用户名"
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
            <el-table-column prop="username" label="用户名" min-width="190">
              <template #default="{ row }">
                <div class="username-cell">
                  <span class="row-avatar">{{ row.username.slice(0, 1).toUpperCase() }}</span>
                  <div>
                    <strong>{{ row.username }}</strong>
                    <small v-if="row.self">当前账号</small>
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
            <el-table-column prop="createdAt" label="创建时间" width="172">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="102" align="center" fixed="right">
              <template #default="{ row }">
                <el-tooltip :content="row.self ? '重置自己的密码后需重新登录' : '设置新密码'" placement="top">
                  <el-button
                    :icon="Key"
                    size="small"
                    plain
                    :aria-label="`重置 ${row.username} 的密码`"
                    @click="openPasswordDialog(row)"
                  >
                    重置
                  </el-button>
                </el-tooltip>
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
            layout="sizes, prev, pager, next"
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
          <el-input v-model="createForm.username" maxlength="20" autocomplete="off" placeholder="3-20 位字母、数字或下划线" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Avatar,
  CircleCheck,
  CircleClose,
  Key,
  Plus,
  RefreshRight,
  Search,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { userApi, type ManagedUser, type UserQuery, type UserRole, type UserStats } from '@/api/user'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { useUserStore } from '@/stores/user'
import { validatePassword, validateUsername } from '@/utils/userValidation'

type RoleFilter = UserRole | 'ALL'
type StatusFilter = 'ALL' | 'ENABLED' | 'DISABLED'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const users = ref<ManagedUser[]>([])
const stats = ref<UserStats>({ totalUsers: 0, enabledUsers: 0, disabledUsers: 0, adminUsers: 0 })
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
const createForm = reactive<{ username: string; password: string; role: UserRole }>({
  username: '',
  password: '',
  role: 'USER'
})
const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '管理员', value: 'ADMIN' }
]

const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordTarget = ref<ManagedUser | null>(null)
const passwordForm = reactive({ password: '', confirmPassword: '' })

const fieldValidator = (validator: (value: string) => string | null) =>
  (_rule: unknown, value: string, callback: (error?: Error) => void) => {
    const message = validator(value)
    callback(message ? new Error(message) : undefined)
  }

const createRules: FormRules = {
  username: [{ validator: fieldValidator(validateUsername), trigger: 'blur' }],
  password: [{ validator: fieldValidator(validatePassword), trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
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

const openCreateDialog = () => {
  createForm.username = ''
  createForm.password = ''
  createForm.role = 'USER'
  createDialogVisible.value = true
}

const submitCreateUser = async () => {
  if (!createFormRef.value || createSubmitting.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  createSubmitting.value = true
  try {
    await userApi.createUser({ ...createForm })
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
  try {
    await ElMessageBox.confirm(
      `确认将“${user.username}”调整为${role === 'ADMIN' ? '管理员' : '普通用户'}？该用户现有登录将立即失效。`,
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
  min-width: 850px;
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

.username-cell div {
  display: grid;
  gap: 2px;
}

.username-cell strong {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 650;
}

.username-cell small {
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 600;
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
