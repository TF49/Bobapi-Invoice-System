<template>
  <div class="workspace-shell">
    <AppHeader title="我的额度" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid quota-stats-grid" :distance="10">
        <SpotlightCard class="stat-card balance-card stat-card-featured">
          <div class="stat-card-content">
            <span class="stat-icon success"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">当前余额</span>
              <strong class="stat-value quota-stat">
                <CountUp :value="quota.balance" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">可用于开票的剩余余额</p>
          </div>
          <el-button
            type="primary"
            size="small"
            :icon="Plus"
            class="balance-recharge-btn"
            @click="showRechargeDialog = true"
          >
            快速充值
          </el-button>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Coin /></span>
            <div class="stat-copy">
              <span class="stat-label">累计充值</span>
              <strong class="stat-value">
                <CountUp :value="quota.totalRecharged" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">历史获得的算力额度</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Remove /></span>
            <div class="stat-copy">
              <span class="stat-label">累计扣除</span>
              <strong class="stat-value">
                <CountUp :value="quota.totalDeducted" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">已开票或扣除的额度</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel records-panel" :delay="80">
        <div class="panel-header tab-header">
          <div class="header-tabs">
            <button
              type="button"
              class="tab-btn"
              :class="{ 'is-active': activeTab === 'transactions' }"
              @click="activeTab = 'transactions'"
            >
              <span class="tab-icon"><List /></span>
              <span>额度变动明细</span>
            </button>
            <button
              type="button"
              class="tab-btn"
              :class="{ 'is-active': activeTab === 'requests' }"
              @click="activeTab = 'requests'"
            >
              <span class="tab-icon"><Tickets /></span>
              <span>充值申请记录</span>
              <span v-if="pendingRequestsCount > 0" class="pending-badge">{{ pendingRequestsCount }}</span>
            </button>
            <button
              type="button"
              class="tab-btn"
              :class="{ 'is-active': activeTab === 'api' }"
              @click="switchToApiTab"
            >
              <span class="tab-icon"><Connection /></span>
              <span>OpenAPI 开发者密钥</span>
            </button>
          </div>

          <div class="panel-right-actions">
            <!-- 额度流水筛选 -->
            <div v-if="activeTab === 'transactions'" class="filter-control">
              <span class="filter-label"><Filter /> 筛选类型</span>
              <el-select
                v-model="filterType"
                placeholder="全部类型"
                clearable
                style="width: 130px"
                @change="loadTransactions"
              >
                <el-option label="全部类型" value="" />
                <el-option label="充值" value="RECHARGE" />
                <el-option label="扣除" value="DEDUCT" />
                <el-option label="调整" value="ADJUST" />
              </el-select>
            </div>

            <!-- 充值申请筛选 -->
            <div v-else-if="activeTab === 'requests'" class="filter-control">
              <span class="filter-label"><Filter /> 筛选状态</span>
              <el-select
                v-model="requestFilterStatus"
                placeholder="全部状态"
                clearable
                style="width: 130px"
              >
                <el-option label="全部状态" value="" />
                <el-option label="待审核" value="PENDING" />
                <el-option label="已通过" value="APPROVED" />
                <el-option label="已拒绝" value="REJECTED" />
              </el-select>
            </div>

            <el-button v-if="activeTab !== 'api'" type="primary" :icon="Plus" class="recharge-action-btn" @click="showRechargeDialog = true">
              申请充值
            </el-button>
          </div>
        </div>

        <!-- 1. 额度变动明细 Tab 内容 -->
        <template v-if="activeTab === 'transactions'">
          <div v-if="!loading && transactions.length === 0" class="table-empty-state">
            <span><Wallet /></span>
            <strong>暂无额度使用记录</strong>
          </div>

          <div v-else class="table-scroll desktop-records">
            <el-table :data="paginatedTransactions" v-loading="loading" class="records-table">
              <el-table-column prop="id" label="记录编号" width="112">
                <template #default="{ row }">
                  <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="transactionType" label="类型" width="104" align="center">
                <template #default="{ row }">
                  <el-tag class="status-tag" :class="getTransactionTagClass(row.transactionType)">
                    <i class="status-dot"></i>
                    {{ getTransactionTypeLabel(row.transactionType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="变动金额" width="140" align="right">
                <template #default="{ row }">
                  <span class="money-cell" :class="row.amount >= 0 ? 'amount-positive' : 'amount-negative'">
                    {{ row.amount >= 0 ? '+' : '' }}{{ formatCurrency(row.amount) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="balanceAfter" label="变动后余额" width="140" align="right">
                <template #default="{ row }">
                  <span class="balance-after">{{ formatCurrency(row.balanceAfter) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="200">
                <template #default="{ row }">
                  <span class="remark-cell">{{ row.remark || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="时间" width="168">
                <template #default="{ row }">
                  <div class="date-cell">
                    <span>{{ formatDateParts(row.createdAt).date }}</span>
                    <small>{{ formatDateParts(row.createdAt).time }}</small>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 移动端明细卡片 -->
          <div v-if="transactions.length > 0" v-loading="loading" class="mobile-records">
            <article v-for="row in paginatedTransactions" :key="row.id" class="quota-record-card">
              <div class="record-card-header">
                <div class="record-id-wrap">
                  <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
                  <span class="record-time">{{ formatDateTime(row.createdAt) }}</span>
                </div>
                <el-tag class="status-tag" :class="getTransactionTagClass(row.transactionType)">
                  <i class="status-dot"></i>
                  {{ getTransactionTypeLabel(row.transactionType) }}
                </el-tag>
              </div>
              <div class="quota-card-details">
                <div class="detail-item">
                  <span class="detail-label">变动金额</span>
                  <strong class="money-cell" :class="row.amount >= 0 ? 'amount-positive' : 'amount-negative'">
                    {{ row.amount >= 0 ? '+' : '' }}{{ formatCurrency(row.amount) }}
                  </strong>
                </div>
                <div class="detail-item">
                  <span class="detail-label">变动后余额</span>
                  <span class="balance-after">{{ formatCurrency(row.balanceAfter) }}</span>
                </div>
                <div v-if="row.remark" class="detail-item full-width">
                  <span class="detail-label">说明</span>
                  <span class="remark-cell">{{ row.remark }}</span>
                </div>
              </div>
            </article>
          </div>

          <div v-if="transactions.length > 0" class="pagination-bar">
            <span>共 {{ transactions.length }} 条记录</span>
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="transactions.length"
              layout="sizes, prev, pager, next, jumper"
              background
              @size-change="handlePageSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </template>

        <!-- 2. 充值申请记录 Tab 内容 -->
        <template v-else-if="activeTab === 'requests'">
          <div v-if="!loadingRequests && filteredMyRequests.length === 0" class="table-empty-state">
            <span><Tickets /></span>
            <strong>暂无充值申请记录</strong>
          </div>

          <div v-else class="table-scroll desktop-records">
            <el-table :data="paginatedMyRequests" v-loading="loadingRequests" class="records-table">
              <el-table-column prop="id" label="申请编号" width="112">
                <template #default="{ row }">
                  <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="feeAmount" label="手续费" width="140" align="right">
                <template #default="{ row }">
                  <span v-if="row.feeAmount != null" class="money-cell">{{ formatCurrency(row.feeAmount) }}</span>
                  <span v-else class="text-muted">历史记录</span>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="申请额度" width="140" align="right">
                <template #default="{ row }">
                  <span class="money-cell amount-positive">{{ formatCurrency(row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="审核状态" width="120" align="center">
                <template #default="{ row }">
                  <el-tag class="status-tag" :class="getRequestStatusClass(row.status)">
                    <i class="status-dot"></i>
                    {{ getRequestStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="screenshotUrl" label="充值凭证" width="110" align="center">
                <template #default="{ row }">
                  <el-image
                    v-if="row.screenshotUrl"
                    :src="getImageUrl(row.screenshotUrl)"
                    :preview-src-list="[getImageUrl(row.screenshotUrl)]"
                    preview-teleported
                    fit="cover"
                    class="screenshot-thumb"
                  />
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="用户备注" min-width="150">
                <template #default="{ row }">
                  <span class="remark-cell">{{ row.remark || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="adminRemark" label="管理员反馈" min-width="160">
                <template #default="{ row }">
                  <span v-if="row.adminRemark" :class="row.status === 'REJECTED' ? 'danger-note' : 'remark-cell'">
                    {{ row.adminRemark }}
                  </span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="申请时间" width="168">
                <template #default="{ row }">
                  <div class="date-cell">
                    <span>{{ formatDateParts(row.createdAt).date }}</span>
                    <small>{{ formatDateParts(row.createdAt).time }}</small>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 移动端充值记录卡片 -->
          <div v-if="filteredMyRequests.length > 0" v-loading="loadingRequests" class="mobile-records">
            <article v-for="row in paginatedMyRequests" :key="row.id" class="quota-record-card">
              <div class="record-card-header">
                <div class="record-id-wrap">
                  <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
                  <span class="record-time">{{ formatDateTime(row.createdAt) }}</span>
                </div>
                <el-tag class="status-tag" :class="getRequestStatusClass(row.status)">
                  <i class="status-dot"></i>
                  {{ getRequestStatusLabel(row.status) }}
                </el-tag>
              </div>
              <div class="quota-card-details">
                <div class="detail-item">
                  <span class="detail-label">手续费</span>
                  <strong v-if="row.feeAmount != null" class="money-cell">{{ formatCurrency(row.feeAmount) }}</strong>
                  <span v-else class="text-muted">历史记录</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">申请额度</span>
                  <strong class="money-cell amount-positive">{{ formatCurrency(row.amount) }}</strong>
                </div>
                <div v-if="row.screenshotUrl" class="detail-item">
                  <span class="detail-label">充值凭证</span>
                  <el-image
                    :src="getImageUrl(row.screenshotUrl)"
                    :preview-src-list="[getImageUrl(row.screenshotUrl)]"
                    preview-teleported
                    fit="cover"
                    class="screenshot-thumb"
                  />
                </div>
                <div v-if="row.remark" class="detail-item full-width">
                  <span class="detail-label">用户备注</span>
                  <span class="remark-cell">{{ row.remark }}</span>
                </div>
                <div v-if="row.adminRemark" class="detail-item full-width">
                  <span class="detail-label">管理员反馈</span>
                  <span :class="row.status === 'REJECTED' ? 'danger-note' : 'remark-cell'">{{ row.adminRemark }}</span>
                </div>
              </div>
            </article>
          </div>

          <div v-if="filteredMyRequests.length > 0" class="pagination-bar">
            <span>共 {{ filteredMyRequests.length }} 条记录</span>
            <el-pagination
              v-model:current-page="requestPage"
              v-model:page-size="requestPageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="filteredMyRequests.length"
              layout="sizes, prev, pager, next, jumper"
              background
              @size-change="handleRequestPageSizeChange"
              @current-change="handleRequestCurrentChange"
            />
          </div>
        </template>

        <!-- 3. OpenAPI 开发者对接 Tab 内容 -->
        <template v-else-if="activeTab === 'api'">
          <div class="api-dev-tab-content">
            <div class="api-key-panel">
              <div class="api-key-header">
                <div>
                  <h3 class="api-key-title">我的 OpenAPI 调用凭证</h3>
                  <p class="api-key-subtitle">
                    外部系统调用发票开放接口时，请在 HTTP 请求头中携带 <code class="inline-code">X-API-KEY</code>。
                  </p>
                </div>
                <el-tag :type="apiKeyData.apiKey && apiKeyData.apiKeyEnabled !== false ? 'success' : 'danger'" size="large">
                  {{ apiKeyData.apiKey ? (apiKeyData.apiKeyEnabled !== false ? 'API Key 生效中' : 'API Key 已禁用') : '未生成 Key' }}
                </el-tag>
              </div>

              <div class="api-key-controls">
                <el-input
                  :model-value="apiKeyData.apiKey || '暂未生成 API Key，请点击右侧按钮生成'"
                  readonly
                  :type="showApiKeyText ? 'text' : 'password'"
                  class="api-key-input"
                />
                <div class="api-key-btns">
                  <el-button
                    v-if="apiKeyData.apiKey"
                    :icon="CopyDocument"
                    @click="handleCopyApiKey(apiKeyData.apiKey)"
                  >
                    复制 Key
                  </el-button>
                  <el-button
                    type="primary"
                    :loading="apiKeyGenerating"
                    @click="handleGenerateApiKey"
                  >
                    {{ apiKeyData.apiKey ? '重置生成新 Key' : '立即生成 API Key' }}
                  </el-button>
                </div>
              </div>

              <div v-if="apiKeyData.apiKey" class="api-key-switch-row">
                <span>启用状态：</span>
                <el-switch
                  :model-value="apiKeyData.apiKeyEnabled !== false"
                  :loading="apiKeyStatusLoading"
                  inline-prompt
                  active-text="启用"
                  inactive-text="禁用"
                  @change="(val: string | number | boolean) => handleToggleApiKeyStatus(Boolean(val))"
                />
                <span class="api-key-hint">禁用后外部接口将拒绝调用</span>
              </div>
            </div>

            <!-- OpenAPI 对接示例指南 -->
            <div class="openapi-guide-card">
              <div class="openapi-guide-header">
                <div>
                  <h3 class="openapi-title">⚡ 快速接入与接口返回示例 (OpenAPI v1)</h3>
                  <p class="openapi-desc">
                    所有接口均以 JSON 格式通信。调用时请在 HTTP 请求头添加 <code class="inline-code">X-API-KEY: {{ apiKeyData.apiKey || 'your_api_key' }}</code>。
                  </p>
                </div>
                <div class="openapi-base-url-badge">
                  <span class="badge-label">Base URL:</span>
                  <code>{{ currentBaseUrl }}</code>
                </div>
              </div>

              <!-- 接口 1: 提交发票申请 -->
              <div class="api-doc-item">
                <div class="api-item-header">
                  <span class="http-badge post">POST</span>
                  <span class="api-item-title">1. 提交发票申请 (自动扣减额度)</span>
                  <span class="api-item-path">/open/v1/invoices</span>
                </div>
                <p class="api-item-desc">
                  提交单条发票申请，根据开票金额 (<code class="inline-code">amount</code>) 实时扣减账户可用额度。支持通过 <code class="inline-code">outTradeNo</code>（外部商户订单号）进行幂等防重与后续状态回查。
                </p>
                <div class="api-code-grid">
                  <!-- 请求示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">请求示例 (cURL)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetSubmitReq, 'cURL 请求示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetSubmitReq }}</code></pre>
                  </div>
                  <!-- 返回示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">响应示例 (JSON - HTTP 200)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetSubmitResp, '响应示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetSubmitResp }}</code></pre>
                  </div>
                </div>
              </div>

              <!-- 接口 2: 按外部商户订单号查询开票状态与发票下载 -->
              <div class="api-doc-item">
                <div class="api-item-header">
                  <span class="http-badge get">GET</span>
                  <span class="api-item-title">2. 按外部商户订单号查询开票状态与发票下载</span>
                  <span class="api-item-path">/open/v1/invoices/by-out-trade-no/{outTradeNo}</span>
                </div>
                <p class="api-item-desc">
                  无需维护发票系统内部 ID，直接传入外部商户单号回查状态。<code class="inline-code">status</code> 为 <code class="inline-code">PENDING</code>(开票中)、<code class="inline-code">COMPLETED</code>(已开票) 或 <code class="inline-code">CANCELLED</code>(已取消)。当已开票且 <code class="inline-code">downloadable</code> 为 <code class="inline-code">true</code> 时可直接下载发票文件。
                </p>
                <div class="api-code-grid">
                  <!-- 请求示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">请求示例 (cURL)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetQueryReq, 'cURL 请求示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetQueryReq }}</code></pre>
                  </div>
                  <!-- 返回示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">响应示例 (JSON - HTTP 200 已开票)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetQueryResp, '响应示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetQueryResp }}</code></pre>
                  </div>
                </div>

                <!-- 附带发票下载命令 -->
                <div class="api-sub-tip">
                  <div class="api-sub-tip-title">📎 发票文件直接下载接口 (cURL)：</div>
                  <div class="code-box" style="margin-top: 8px;">
                    <div class="code-box-header">
                      <span class="code-box-title">下载命令 (GET /open/v1/invoices/by-out-trade-no/{outTradeNo}/download)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetDownloadReq, '下载命令')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetDownloadReq }}</code></pre>
                  </div>
                </div>
              </div>

              <!-- 接口 3: 取消待开票申请 (自动退还额度) -->
              <div class="api-doc-item">
                <div class="api-item-header">
                  <span class="http-badge post">POST</span>
                  <span class="api-item-title">3. 取消待开票申请 (自动退还额度)</span>
                  <span class="api-item-path">/open/v1/invoices/by-out-trade-no/{outTradeNo}/cancel</span>
                </div>
                <p class="api-item-desc">
                  仅允许取消状态为 <code class="inline-code">PENDING</code>（待开票）的发票申请。取消成功后，扣减的额度将<strong>实时原路退回</strong>账户可用余额。已开票（<code class="inline-code">COMPLETED</code>）的发票不允许取消。
                </p>
                <div class="api-code-grid">
                  <!-- 请求示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">请求示例 (cURL)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetCancelReq, 'cURL 请求示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetCancelReq }}</code></pre>
                  </div>
                  <!-- 返回示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">响应示例 (JSON - HTTP 200 已取消)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetCancelResp, '响应示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetCancelResp }}</code></pre>
                  </div>
                </div>

                <!-- 附带按发票 ID 取消命令 -->
                <div class="api-sub-tip">
                  <div class="api-sub-tip-title">📎 亦可按发票内部 ID 取消接口 (cURL)：</div>
                  <div class="code-box" style="margin-top: 8px;">
                    <div class="code-box-header">
                      <span class="code-box-title">取消命令 (POST /open/v1/invoices/{id}/cancel)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetCancelByIdReq, '按 ID 取消命令')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetCancelByIdReq }}</code></pre>
                  </div>
                </div>
              </div>

              <!-- 接口 4: 查询当前账户剩余可用额度 -->
              <div class="api-doc-item">
                <div class="api-item-header">
                  <span class="http-badge get">GET</span>
                  <span class="api-item-title">4. 查询当前账户剩余可用额度</span>
                  <span class="api-item-path">/open/v1/quota</span>
                </div>
                <p class="api-item-desc">
                  查询当前 API Key 关联账户的可用额度余额 (<code class="inline-code">balance</code>)、累计充值与累计消耗额度，便于外部系统前置做余额预警。
                </p>
                <div class="api-code-grid">
                  <!-- 请求示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">请求示例 (cURL)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetQuotaReq, 'cURL 请求示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetQuotaReq }}</code></pre>
                  </div>
                  <!-- 返回示例 -->
                  <div class="code-box">
                    <div class="code-box-header">
                      <span class="code-box-title">响应示例 (JSON - HTTP 200)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetQuotaResp, '响应示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetQuotaResp }}</code></pre>
                  </div>
                </div>
              </div>

              <!-- 接口 5: 异常与错误返回示例 -->
              <div class="api-doc-item" style="margin-bottom: 0;">
                <div class="api-item-header">
                  <span class="http-badge error">ERROR</span>
                  <span class="api-item-title">5. 统一异常 / 错误返回格式</span>
                  <span class="api-item-path">HTTP 400 / 401 / 429</span>
                </div>
                <p class="api-item-desc">
                  当请求发生错误（如额度不足 <code class="inline-code">40002</code>、密钥失效 <code class="inline-code">40101</code>、频率受限 <code class="inline-code">42902</code> 等），接口统一返回标准 JSON 结构。
                </p>
                <div class="api-code-grid">
                  <div class="code-box" style="grid-column: 1 / -1;">
                    <div class="code-box-header">
                      <span class="code-box-title">错误响应示例 (JSON - 额度不足 400 Bad Request)</span>
                      <button class="code-copy-btn" type="button" @click="handleCopyText(snippetErrorResp, '错误响应示例')">
                        <el-icon :size="12"><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                    <pre class="code-box-content"><code>{{ snippetErrorResp }}</code></pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </AnimatedContent>
    </main>

    <RechargeRequestDialog
      v-model="showRechargeDialog"
      @success="handleRechargeSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Coin, Connection, CopyDocument, Filter, List, Plus, Remove, Tickets, Wallet } from '@element-plus/icons-vue'
import { quotaApi, type QuotaTransaction, type UserQuota } from '@/api/quota'
import { rechargeRequestApi, type RechargeRequest } from '@/api/rechargeRequest'
import { userApi, type ApiKeyData } from '@/api/user'
import { getImageUrl } from '@/utils/imageUrl'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import RechargeRequestDialog from '@/components/RechargeRequestDialog.vue'

const activeTab = ref<'transactions' | 'requests' | 'api'>('transactions')
const loading = ref(false)
const loadingRequests = ref(false)
const loadingApiKey = ref(false)
const apiKeyGenerating = ref(false)
const apiKeyStatusLoading = ref(false)
const showApiKeyText = ref(true)

const apiKeyData = ref<ApiKeyData>({
  apiKey: null,
  apiKeyEnabled: true
})

const quota = ref<UserQuota>({
  userId: 0,
  balance: 0,
  totalRecharged: 0,
  totalDeducted: 0
})

const transactions = ref<QuotaTransaction[]>([])
const myRequests = ref<RechargeRequest[]>([])
const filterType = ref('')
const requestFilterStatus = ref('')
const showRechargeDialog = ref(false)

const page = ref(1)
const pageSize = ref(10)
const requestPage = ref(1)
const requestPageSize = ref(10)

const pendingRequestsCount = computed(() =>
  myRequests.value.filter((r) => r.status === 'PENDING').length
)

const filteredMyRequests = computed(() => {
  if (!requestFilterStatus.value) return myRequests.value
  return myRequests.value.filter((r) => r.status === requestFilterStatus.value)
})

const paginatedTransactions = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return transactions.value.slice(start, start + pageSize.value)
})

const paginatedMyRequests = computed(() => {
  const start = (requestPage.value - 1) * requestPageSize.value
  return filteredMyRequests.value.slice(start, start + requestPageSize.value)
})

watch(transactions, (newList) => {
  const maxPage = Math.ceil(newList.length / pageSize.value) || 1
  if (page.value > maxPage) {
    page.value = maxPage
  }
})

watch(filteredMyRequests, (newList) => {
  const maxPage = Math.ceil(newList.length / requestPageSize.value) || 1
  if (requestPage.value > maxPage) {
    requestPage.value = maxPage
  }
})

const handlePageSizeChange = (val: number) => {
  pageSize.value = val
  page.value = 1
}

const handleCurrentChange = (val: number) => {
  page.value = val
}

const handleRequestPageSizeChange = (val: number) => {
  requestPageSize.value = val
  requestPage.value = 1
}

const handleRequestCurrentChange = (val: number) => {
  requestPage.value = val
}

const loadQuota = async () => {
  try {
    quota.value = await quotaApi.getMyQuota()
  } catch (error: any) {
    ElMessage.error(error.message || '加载额度信息失败')
  }
}

const loadTransactions = async () => {
  loading.value = true
  page.value = 1
  try {
    transactions.value = await quotaApi.getMyTransactions(filterType.value || undefined)
  } catch (error: any) {
    ElMessage.error(error.message || '加载额度记录失败')
  } finally {
    loading.value = false
  }
}

const loadMyRequests = async () => {
  loadingRequests.value = true
  try {
    myRequests.value = await rechargeRequestApi.getMyRequests()
  } catch (error: any) {
    ElMessage.error(error.message || '加载充值申请记录失败')
  } finally {
    loadingRequests.value = false
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

const getTransactionTagClass = (type: string) => {
  const classes: Record<string, string> = {
    RECHARGE: 'is-completed',
    DEDUCT: 'is-danger',
    ADJUST: 'is-pending'
  }
  return classes[type] || ''
}

const getRequestStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝'
  }
  return labels[status] || status
}

const getRequestStatusClass = (status: string) => {
  const classes: Record<string, string> = {
    PENDING: 'is-pending',
    APPROVED: 'is-completed',
    REJECTED: 'is-danger'
  }
  return classes[status] || ''
}

const formatCurrency = (amount: number) => new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
}).format(Number(amount))

const formatDateTime = (value: string) => {
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

const formatDateParts = (value: string) => {
  const formatted = formatDateTime(value)
  const separatorIndex = formatted.lastIndexOf(' ')
  if (separatorIndex < 0) return { date: formatted, time: '' }
  return {
    date: formatted.slice(0, separatorIndex),
    time: formatted.slice(separatorIndex + 1)
  }
}

const handleRechargeSuccess = () => {
  loadQuota()
  loadTransactions()
  loadMyRequests()
}

const switchToApiTab = () => {
  activeTab.value = 'api'
  loadApiKey()
}

const loadApiKey = async () => {
  loadingApiKey.value = true
  try {
    const res = await userApi.getMyApiKey()
    apiKeyData.value = res
  } catch (error: any) {
    console.error('加载 API Key 失败', error)
  } finally {
    loadingApiKey.value = false
  }
}

const currentBaseUrl = computed(() => {
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    return window.location.origin
  }
  return 'http://localhost:9090'
})

const snippetSubmitReq = computed(() => `curl -X POST "${currentBaseUrl.value}/open/v1/invoices" \\
  -H "Content-Type: application/json" \\
  -H "X-API-KEY: ${apiKeyData.value.apiKey || 'your_api_key'}" \\
  -d '{
    "outTradeNo": "ORDER_20260828001",
    "companyName": "测试企业有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "外部系统自动化推单"
  }'`)

const snippetSubmitResp = computed(() => `{
  "code": 200,
  "message": "申请成功",
  "data": {
    "id": 1024,
    "outTradeNo": "ORDER_20260828001",
    "companyName": "测试企业有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "外部系统自动化推单",
    "status": "PENDING",
    "downloadable": false,
    "fileName": null,
    "createdAt": "2026-08-28T14:00:00",
    "completedAt": null
  }
}`)

const snippetQueryReq = computed(() => `curl -X GET "${currentBaseUrl.value}/open/v1/invoices/by-out-trade-no/ORDER_20260828001" \\
  -H "X-API-KEY: ${apiKeyData.value.apiKey || 'your_api_key'}"`)

const snippetQueryResp = computed(() => `{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1024,
    "outTradeNo": "ORDER_20260828001",
    "companyName": "测试企业有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "外部系统自动化推单",
    "status": "COMPLETED",
    "downloadable": true,
    "fileName": "invoice_1024.png",
    "createdAt": "2026-08-28T14:00:00",
    "completedAt": "2026-08-28T14:15:00"
  }
}`)

const snippetDownloadReq = computed(() => `curl -X GET "${currentBaseUrl.value}/open/v1/invoices/by-out-trade-no/ORDER_20260828001/download" \\
  -H "X-API-KEY: ${apiKeyData.value.apiKey || 'your_api_key'}" \\
  -O -J`)

const snippetCancelReq = computed(() => `curl -X POST "${currentBaseUrl.value}/open/v1/invoices/by-out-trade-no/ORDER_20260828001/cancel" \\
  -H "X-API-KEY: ${apiKeyData.value.apiKey || 'your_api_key'}"`)

const snippetCancelByIdReq = computed(() => `curl -X POST "${currentBaseUrl.value}/open/v1/invoices/1024/cancel" \\
  -H "X-API-KEY: ${apiKeyData.value.apiKey || 'your_api_key'}"`)

const snippetCancelResp = computed(() => `{
  "code": 200,
  "message": "取消成功",
  "data": {
    "id": 1024,
    "outTradeNo": "ORDER_20260828001",
    "companyName": "测试企业有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "外部系统自动化推单",
    "status": "CANCELLED",
    "downloadable": false,
    "fileName": null,
    "createdAt": "2026-08-28T14:00:00",
    "completedAt": null
  }
}`)

const snippetQuotaReq = computed(() => `curl -X GET "${currentBaseUrl.value}/open/v1/quota" \\
  -H "X-API-KEY: ${apiKeyData.value.apiKey || 'your_api_key'}"`)

const snippetQuotaResp = computed(() => `{
  "code": 200,
  "message": "success",
  "data": {
    "balance": 18500.00,
    "totalRecharged": 20000.00,
    "totalDeducted": 1500.00
  }
}`)

const snippetErrorResp = computed(() => `{
  "code": 40002,
  "message": "账户可用额度不足，请先充值",
  "data": null
}`)

const handleCopyText = async (text: string, label: string = '内容') => {
  let copied = false
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      copied = true
    } catch {
      // Fall through to textarea fallback
    }
  }
  if (!copied) {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = text
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
    ElMessage.success(`${label}已复制到剪贴板`)
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const handleCopyApiKey = (key: string) => {
  handleCopyText(key, 'API Key')
}

const handleGenerateApiKey = async () => {
  if (apiKeyGenerating.value) return
  const isReset = Boolean(apiKeyData.value.apiKey)
  if (isReset) {
    try {
      await ElMessageBox.confirm(
        '重置后旧 API Key 将立即失效，您的外部系统接口调用将中断。确定要生成新的 API Key 吗？',
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
    const res = await userApi.generateMyApiKey()
    apiKeyData.value = res
    ElMessage.success(isReset ? 'API Key 重置成功' : 'API Key 生成成功')
  } catch (error: any) {
    ElMessage.error(error?.message || '生成 API Key 失败')
  } finally {
    apiKeyGenerating.value = false
  }
}

const handleToggleApiKeyStatus = async (enabled: boolean) => {
  if (apiKeyStatusLoading.value) return
  apiKeyStatusLoading.value = true
  try {
    const res = await userApi.updateMyApiKeyStatus(enabled)
    apiKeyData.value = res
    ElMessage.success(enabled ? 'API Key 已启用' : 'API Key 已禁用')
  } catch (error: any) {
    ElMessage.error(error?.message || '更新 API Key 状态失败')
  } finally {
    apiKeyStatusLoading.value = false
  }
}

let quotaInvoiceBroadcastChannel: BroadcastChannel | null = null

const handleRefreshQuotaOnProcessed = () => {
  loadQuota()
  loadTransactions()
}

onMounted(() => {
  loadQuota()
  loadTransactions()
  loadMyRequests()

  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      quotaInvoiceBroadcastChannel = new BroadcastChannel('bobapi-invoice-events')
      quotaInvoiceBroadcastChannel.onmessage = (event) => {
        if (event.data?.type === 'invoice-red-flush-processed') {
          handleRefreshQuotaOnProcessed()
        }
      }
    } catch (_) {}
  }
  window.addEventListener('invoice-red-flush-processed', handleRefreshQuotaOnProcessed)
})

onBeforeUnmount(() => {
  if (quotaInvoiceBroadcastChannel) {
    quotaInvoiceBroadcastChannel.close()
    quotaInvoiceBroadcastChannel = null
  }
  window.removeEventListener('invoice-red-flush-processed', handleRefreshQuotaOnProcessed)
})
</script>

<style scoped>
.quota-stats-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.record-id {
  display: inline-flex;
  padding: 4px 7px;
  color: #61716b;
  background: #f1f5f3;
  border: 1px solid #e1e9e6;
  border-radius: 5px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.status-tag.is-danger {
  color: #c9463d;
  background: #fdf2f1;
}

.amount-positive {
  color: #12715b;
}

.amount-negative {
  color: #c9463d;
}

.text-muted {
  color: var(--color-text-muted);
}

.remark-cell {
  color: var(--color-text);
  font-size: 13px;
}

.records-panel {
  overflow: hidden;
}

.records-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
}

.records-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.date-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.date-cell small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.mobile-records {
  display: none;
}

.balance-card {
  position: relative;
}

.balance-card :deep(.spotlight-card) {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.balance-recharge-btn {
  position: absolute;
  top: 18px;
  right: 18px;
  border-radius: 6px;
  font-weight: 550;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.header-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--color-surface-soft, #f4f7f6);
  padding: 4px;
  border-radius: 8px;
}

.panel-right-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.recharge-action-btn {
  font-weight: 600;
  border-radius: 6px;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 550;
  border-radius: 6px;
  cursor: pointer;
  transition: all 160ms ease;
}

.tab-btn:hover {
  color: var(--color-text);
}

.tab-btn.is-active {
  background: var(--color-surface);
  color: var(--color-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.tab-icon {
  display: flex;
  align-items: center;
}

.pending-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  font-size: 11px;
  color: #fff;
  background: #c9463d;
  border-radius: 9px;
  line-height: 1;
}

.screenshot-thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  cursor: pointer;
}

.danger-note {
  color: #c9463d;
  font-size: 13px;
  font-weight: 550;
}

/* OpenAPI 页面样式 */
.api-dev-tab-content {
  padding: 20px;
}

.api-key-panel {
  background: var(--color-surface-subtle, #f8faf9);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}

.api-key-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.api-key-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px 0;
}

.api-key-subtitle {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
}

.api-key-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.api-key-input {
  flex: 1;
  max-width: 480px;
  min-width: 200px;
}

.api-key-btns {
  display: flex;
  gap: 8px;
  align-items: center;
}

.api-key-switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.api-key-hint {
  color: var(--color-text-muted);
  font-size: 12px;
}

/* OpenAPI 接口文档与示例样式 */
.openapi-guide-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 24px;
}

.openapi-guide-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.openapi-title {
  font-size: 16px;
  font-weight: 650;
  margin: 0 0 6px 0;
  color: var(--color-text);
}

.openapi-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
  line-height: 1.6;
}

.openapi-base-url-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 12px;
  white-space: nowrap;
}

.openapi-base-url-badge .badge-label {
  color: var(--color-text-muted);
  font-weight: 500;
}

.openapi-base-url-badge code {
  color: var(--color-primary);
  font-weight: 600;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.api-doc-item {
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 20px;
}

.api-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.http-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.http-badge.post {
  background: #0284c7;
  color: #ffffff;
}

.http-badge.get {
  background: #059669;
  color: #ffffff;
}

.http-badge.error {
  background: #dc2626;
  color: #ffffff;
}

.api-item-title {
  font-size: 14px;
  font-weight: 650;
  color: var(--color-text);
}

.api-item-path {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: var(--color-text-muted);
  background: rgba(0, 0, 0, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
}

.api-item-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 14px 0;
  line-height: 1.6;
}

.inline-code {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  color: var(--color-text);
}

.api-code-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.code-box {
  background: #181825;
  border: 1px solid #313244;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.code-box-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #1e1e2e;
  border-bottom: 1px solid #313244;
}

.code-box-title {
  font-size: 12px;
  font-weight: 600;
  color: #cdd6f4;
  letter-spacing: 0.2px;
}

.code-copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 11px;
  color: #a6adc8;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  cursor: pointer;
  transition: all 160ms ease;
}

.code-copy-btn:hover {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.code-box-content {
  margin: 0;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.55;
  color: #cdd6f4;
  overflow-x: auto;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  white-space: pre;
}

.api-sub-tip {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}

.api-sub-tip-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

/* 移动端媒体查询适配 */
@media (max-width: 768px) {
  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: grid;
    gap: 12px;
    padding: 14px;
    background: #f7f9f8;
  }

  .quota-record-card {
    padding: 14px 16px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(24, 39, 34, 0.04);
  }

  .record-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--color-border);
    gap: 10px;
  }

  .record-id-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .record-time {
    color: var(--color-text-muted);
    font-size: 11px;
  }

  .quota-card-details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 12px;
    margin-top: 10px;
  }

  .detail-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .detail-item.full-width {
    grid-column: 1 / -1;
  }

  .detail-label {
    color: var(--color-text-muted);
    font-size: 11px;
    font-weight: 600;
  }

  .mobile-screenshot-thumb {
    width: 60px;
    height: 60px;
    border-radius: 6px;
    border: 1px solid var(--color-border);
    cursor: pointer;
  }

  .header-tabs {
    width: 100%;
    overflow-x: auto;
    flex-wrap: nowrap;
    -webkit-overflow-scrolling: touch;
  }

  .tab-btn {
    white-space: nowrap;
    flex-shrink: 0;
    padding: 6px 10px;
    font-size: 12px;
  }

  .panel-right-actions {
    width: 100%;
    gap: 8px;
  }

  .panel-right-actions .filter-control {
    flex: 1;
  }

  .panel-right-actions .filter-control .el-select {
    width: 100% !important;
  }

  .panel-right-actions .recharge-action-btn {
    flex: 1;
  }

  .api-dev-tab-content {
    padding: 12px;
  }

  .api-key-panel {
    padding: 16px;
  }

  .api-key-controls {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .api-key-input {
    max-width: 100%;
  }

  .api-key-btns {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    width: 100%;
  }

  .api-key-btns .el-button {
    width: 100%;
    margin-left: 0 !important;
  }

  .openapi-guide-card {
    padding: 16px;
  }

  .api-code-grid {
    grid-template-columns: 1fr;
  }
}
</style>
