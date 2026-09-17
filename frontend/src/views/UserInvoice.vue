<template>
  <div class="workspace-shell">
    <AppHeader title="我的发票" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总数</span>
              <strong class="stat-value"><CountUp :value="invoices.length" /></strong>
            </div>
            <p class="stat-note">全部开票记录</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">待开票</span>
              <strong class="stat-value"><CountUp :value="pendingCount" /></strong>
            </div>
            <p class="stat-note">等待管理员处理</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">已开票</span>
              <strong class="stat-value"><CountUp :value="completedCount" /></strong>
            </div>
            <p class="stat-note">可下载电子凭证</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">已开票金额</span>
              <strong class="stat-value amount-stat"><CountUp :value="completedAmount" :decimals="2" prefix="¥" /></strong>
            </div>
            <p class="stat-note">已完成申请合计</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card stat-card-featured">
          <div class="stat-card-content">
            <span class="stat-icon success"><Coin /></span>
            <div class="stat-copy">
              <span class="stat-label">我的额度</span>
              <strong class="stat-value quota-stat"><CountUp :value="quotaBalance" :decimals="2" prefix="¥" /></strong>
            </div>
            <p class="stat-note">可用于开票的余额</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel records-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><List /></span>
            <div>
              <h2>发票记录</h2>
              <p>查看申请进度与电子凭证</p>
            </div>
          </div>
          <div class="panel-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索公司、税号、编号或备注"
              :prefix-icon="Search"
              clearable
              class="search-input"
              aria-label="搜索发票"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <div class="filter-control">
              <span class="filter-label"><Calendar />时间</span>
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                :shortcuts="dateShortcuts"
                clearable
                class="date-picker"
                aria-label="筛选申请时间"
              />
            </div>
            <div class="filter-control">
              <span class="filter-label"><Filter />状态</span>
              <el-select
                v-model="statusFilter"
                aria-label="筛选开票状态"
                class="status-select"
              >
                <el-option label="全部状态" value="ALL" />
                <el-option label="待开票" value="PENDING" />
                <el-option label="已开票 (全部)" value="COMPLETED" />
                <el-option label="已开票 (未处理)" value="COMPLETED_UNPROCESSED" />
                <el-option label="已开票 (已处理)" value="COMPLETED_PROCESSED" />
                <el-option label="待红冲" value="RED_FLUSH_PENDING" />
                <el-option label="已红冲" value="RED_FLUSH_COMPLETED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </div>
            <div class="filter-control">
              <span class="filter-label"><Connection />方式</span>
              <el-select
                v-model="submissionTypeFilter"
                aria-label="筛选提交方式"
                class="submission-type-select"
              >
                <el-option label="全部方式" value="ALL" />
                <el-option label="API 提交" value="API" />
                <el-option label="手动提交" value="MANUAL" />
              </el-select>
            </div>
            <el-button :icon="Search" class="search-button" @click="handleSearch">搜索</el-button>
            <div class="action-buttons-wrap">
              <span class="result-count"><i></i>{{ filteredInvoices.length }} 条记录</span>
              <el-button
                type="primary"
                :icon="Plus"
                class="submit-action-button"
                @click="showSubmitDialog"
              >
                提交申请
              </el-button>
              <el-button
                class="batch-import-button"
                :icon="Upload"
                :disabled="submitting"
                @click="showBatchImportDialog"
              >
                批量导入
              </el-button>
            </div>
          </div>
        </div>

        <div v-if="!loading && filteredInvoices.length === 0" class="table-empty-state">
          <span><Files /></span>
          <strong>{{ emptyText }}</strong>
        </div>
        <div v-else class="table-scroll desktop-records">
          <el-table :data="paginatedInvoices" v-loading="loading" class="records-table">
            <el-table-column prop="id" label="申请编号" width="105">
              <template #default="{ row }">
                <span class="invoice-id">{{ formatInvoiceId(row.id) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="companyName" label="公司名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="company-cell">
                  <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                  <strong class="company-name">{{ row.companyName }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="submissionType" label="提交方式" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.submissionType === 'API' ? 'warning' : row.submissionType === 'MANUAL' ? 'info' : 'danger'" size="small">
                  {{ row.submissionType === 'API' ? 'API' : row.submissionType === 'MANUAL' ? '手动' : '未知' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="taxNumber" label="税号" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="tax-number-cell">{{ row.taxNumber || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="开票金额" width="115" align="right">
              <template #default="{ row }">
                <span class="money-cell">{{ formatCurrency(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="invoiceType" label="开票类型" width="165">
              <template #default="{ row }">
                <span v-if="row.invoiceCategory === 'VAT_SPECIAL'" class="vat-special-badge" title="增值税专用发票（专票），额度按 3 倍扣除">
                  <el-icon class="vat-special-ico"><Tickets /></el-icon>
                  <span>专票</span>
                </span>
                <span v-else class="normal-badge" title="增值税普通发票（普票）">
                  <el-icon class="normal-ico"><Document /></el-icon>
                  <span>普票</span>
                </span>
                <el-tag
                  v-if="(row.invoiceType?.trim() || '技术服务费') !== '技术服务费'"
                  size="small"
                  type="danger"
                  effect="plain"
                  class="warning-type-tag"
                >
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  <span>{{ row.invoiceType }}</span>
                </el-tag>
                <el-tag
                  v-else
                  size="small"
                  type="info"
                  effect="plain"
                >
                  {{ row.invoiceType || '技术服务费' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.remark?.trim()" class="remark-warning-text">
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  <span>{{ row.remark }}</span>
                </span>
                <span v-else>{{ row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="开票状态" width="115" align="center">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.status === 'COMPLETED' && row.redFlushStatus === 'PENDING'"
                  :content="`红冲申请原因：${row.redFlushReason || '未填写'}`"
                  placement="top"
                >
                  <el-tag class="status-tag" :class="getStatusClass(row)">
                    <i class="status-dot"></i>
                    {{ getStatusLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <el-tooltip
                  v-else-if="row.status === 'COMPLETED' && row.redFlushStatus === 'COMPLETED'"
                  :content="`该发票已红冲作废，额度已退还${row.redFlushRemark ? '（备注：' + row.redFlushRemark + '）' : ''}`"
                  placement="top"
                >
                  <el-tag class="status-tag" :class="getStatusClass(row)">
                    <i class="status-dot"></i>
                    {{ getStatusLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <el-tooltip
                  v-else-if="row.status === 'COMPLETED' && row.redFlushStatus === 'REJECTED'"
                  :content="`红冲申请已被驳回：${row.redFlushRemark || '未填写原因'}`"
                  placement="top"
                >
                  <el-tag class="status-tag" :class="getStatusClass(row)">
                    <i class="status-dot"></i>
                    {{ getStatusLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <el-tag v-else class="status-tag" :class="getStatusClass(row)">
                  <i class="status-dot"></i>
                  {{ getStatusLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="140">
              <template #default="{ row }">
                <div class="date-cell">
                  <span>{{ formatDateParts(row.createdAt).date }}</span>
                  <small>{{ formatDateParts(row.createdAt).time }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" align="center" fixed="right">
              <template #default="{ row }">
                <div v-if="row.status === 'COMPLETED'" class="record-actions">
                  <template v-if="row.downloadable && row.fileExists">
                    <!-- 1. 红冲审核中场景 -->
                    <template v-if="row.redFlushStatus === 'PENDING'">
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-tag size="small" type="warning" effect="plain" class="red-flush-status-pill">
                        <el-icon class="is-loading"><Loading /></el-icon>
                        红冲审核中
                      </el-tag>
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDropdownCommand(cmd, row)">
                        <el-button size="small" class="record-action-button icon-only-btn more-dropdown-btn" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="download" :icon="Download">下载发票</el-dropdown-item>
                            <el-dropdown-item command="copy" :icon="CopyDocument">复制图片</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>

                    <!-- 2. 红冲驳回场景 -->
                    <template v-else-if="row.redFlushStatus === 'REJECTED'">
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-tooltip :content="`开票员驳回：${row.redFlushRemark || '未说明理由'}`" placement="top">
                        <el-button
                          class="record-action-button red-flush-action-btn"
                          type="danger"
                          plain
                          size="small"
                          :icon="RefreshRight"
                          @click="handleOpenApplyRedFlush(row)"
                        >
                          重申红冲
                        </el-button>
                      </el-tooltip>
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDropdownCommand(cmd, row)">
                        <el-button size="small" class="record-action-button icon-only-btn more-dropdown-btn" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="download" :icon="Download">下载发票</el-dropdown-item>
                            <el-dropdown-item command="copy" :icon="CopyDocument">复制图片</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>

                    <!-- 3. 已红冲结单场景 -->
                    <template v-else-if="row.redFlushStatus === 'COMPLETED'">
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="Download"
                        @click="handleDownload(row)"
                      >
                        下载
                      </el-button>
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDropdownCommand(cmd, row)">
                        <el-button size="small" class="record-action-button icon-only-btn more-dropdown-btn" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="copy" :icon="CopyDocument">复制图片</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>

                    <!-- 4. 正常已开票场景：查看 + 下载 + 复制 + 已处理 + 更多(申请红冲) -->
                    <template v-else>
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="Download"
                        @click="handleDownload(row)"
                      >
                        下载
                      </el-button>
                      <el-button
                        class="record-action-button"
                        type="primary"
                        plain
                        size="small"
                        :icon="CopyDocument"
                        :loading="copyingId === row.id"
                        @click="handleCopyImage(row)"
                      >
                        复制
                      </el-button>
                      <el-tooltip :content="row.isProcessed ? '已处理（点击取消）' : '未处理（点击标记已处理）'" placement="top">
                        <button
                          type="button"
                          class="processed-row-indicator-btn"
                          :class="{ 'is-done': row.isProcessed }"
                          :disabled="togglingRowId === row.id"
                          @click.stop="handleDirectToggleRow(row)"
                        >
                          <el-icon v-if="togglingRowId === row.id" class="is-loading"><Loading /></el-icon>
                          <template v-else>{{ row.isProcessed ? '✓' : '○' }}</template>
                        </button>
                      </el-tooltip>
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDropdownCommand(cmd, row)">
                        <el-button size="small" class="record-action-button icon-only-btn more-dropdown-btn" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="applyRedFlush" class="red-flush-action-btn" :icon="DocumentDelete">申请红冲</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>
                  </template>
                </div>
                <div v-else-if="row.status === 'PENDING'" class="record-actions">
                  <el-button
                    class="record-action-button cancel-action-btn"
                    type="danger"
                    plain
                    size="small"
                    :icon="CircleClose"
                    :loading="cancellingId === row.id"
                    @click="handleCancel(row)"
                  >
                    取消申请
                  </el-button>
                </div>
                <span v-else-if="row.status === 'CANCELLED'" class="empty-action"><i></i>已取消</span>
                <span v-else class="empty-action"><i></i>等待开票</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="filteredInvoices.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in paginatedInvoices" :key="row.id" class="invoice-record-card">
            <div class="record-card-header">
              <div class="company-cell">
                <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                <div class="record-company-copy">
                  <strong class="company-name">{{ row.companyName }}</strong>
                  <span>{{ formatInvoiceId(row.id) }}</span>
                </div>
              </div>
              <div class="card-header-tags">
                <el-tag class="status-tag" :class="getStatusClass(row)">
                  <i class="status-dot"></i>
                  {{ getStatusLabel(row) }}
                </el-tag>
                <button
                  v-if="row.status === 'COMPLETED'"
                  type="button"
                  class="mobile-processed-toggle-btn"
                  :class="{ 'is-done': row.isProcessed }"
                  :disabled="togglingRowId === row.id"
                  @click.stop="handleDirectToggleRow(row)"
                >
                  <el-icon v-if="togglingRowId === row.id" class="is-loading"><Loading /></el-icon>
                  <template v-else>{{ row.isProcessed ? '✓ 已处理' : '○ 标记处理' }}</template>
                </button>
              </div>
            </div>

            <dl class="record-card-details">
              <div>
                <dt>税号</dt>
                <dd class="tax-number-cell">{{ row.taxNumber || '-' }}</dd>
              </div>
              <div>
                <dt>提交方式</dt>
                <dd>
                  <el-tag :type="row.submissionType === 'API' ? 'warning' : row.submissionType === 'MANUAL' ? 'info' : 'danger'" size="small">
                    {{ row.submissionType === 'API' ? 'API提交' : row.submissionType === 'MANUAL' ? '手动提交' : '来源未知' }}
                  </el-tag>
                </dd>
              </div>
              <div>
                <dt>开票金额</dt>
                <dd class="money-cell">{{ formatCurrency(row.amount) }}</dd>
              </div>
              <div>
                <dt>开票类型</dt>
                <dd :class="{ 'warning-invoice-type-text': (row.invoiceType?.trim() || '技术服务费') !== '技术服务费' }">
                  <span v-if="row.invoiceCategory === 'VAT_SPECIAL'" class="vat-special-badge" title="增值税专用发票（专票），额度按 3 倍扣除">
                    <el-icon class="vat-special-ico"><Tickets /></el-icon>
                    <span>专票</span>
                  </span>
                  <span v-else class="normal-badge" title="增值税普通发票（普票）">
                    <el-icon class="normal-ico"><Document /></el-icon>
                    <span>普票</span>
                  </span>
                  <el-icon v-if="(row.invoiceType?.trim() || '技术服务费') !== '技术服务费'" class="warning-icon-inline"><Warning /></el-icon>
                  {{ row.invoiceType || '技术服务费' }}
                </dd>
              </div>
              <div v-if="row.remark?.trim()">
                <dt>备注</dt>
                <dd class="remark-warning-text">
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  {{ row.remark }}
                </dd>
              </div>
              <div v-if="row.status === 'COMPLETED' && row.redFlushReason">
                <dt>红冲原因</dt>
                <dd class="remark-warning-text">{{ row.redFlushReason }}</dd>
              </div>
              <div v-if="row.status === 'COMPLETED' && row.redFlushRemark">
                <dt>红冲备注</dt>
                <dd>{{ row.redFlushRemark }}</dd>
              </div>
              <div>
                <dt>申请时间</dt>
                <dd>{{ formatDate(row.createdAt) }}</dd>
              </div>
            </dl>

            <div v-if="row.status === 'COMPLETED'" class="mobile-record-actions">
              <template v-if="row.downloadable && row.fileExists">
                <el-button
                  type="primary"
                  :icon="ZoomIn"
                  :loading="previewingId === row.id"
                  @click="handlePreview(row)"
                >
                  查看发票
                </el-button>
                <el-button type="primary" plain :icon="Download" @click="handleDownload(row)">
                  下载文件
                </el-button>
                <el-button
                  type="primary"
                  plain
                  :icon="CopyDocument"
                  :loading="copyingId === row.id"
                  @click="handleCopyImage(row)"
                >
                  复制图片
                </el-button>
              </template>
              <template v-if="!row.redFlushStatus || row.redFlushStatus === 'NONE'">
                <el-button
                  type="warning"
                  plain
                  :icon="DocumentDelete"
                  @click="handleOpenApplyRedFlush(row)"
                >
                  申请红冲
                </el-button>
              </template>
              <template v-else-if="row.redFlushStatus === 'REJECTED'">
                <el-button
                  type="danger"
                  plain
                  :icon="RefreshRight"
                  @click="handleOpenApplyRedFlush(row)"
                >
                  重申红冲
                </el-button>
              </template>
            </div>
            <div v-else-if="row.status === 'PENDING'" class="mobile-record-actions">
              <el-button
                type="danger"
                plain
                :icon="CircleClose"
                :loading="cancellingId === row.id"
                @click="handleCancel(row)"
              >
                取消申请
              </el-button>
            </div>
            <div v-else-if="row.status === 'CANCELLED'" class="record-pending-note is-cancelled">
              <CircleClose />
              <span>申请已取消，额度已退还</span>
            </div>
            <div v-else class="record-pending-note">
              <Clock />
              <span>管理员处理完成后，可在这里查看和下载发票</span>
            </div>
          </article>
        </div>

        <div v-if="filteredInvoices.length > 0" class="pagination-bar">
          <span>共 {{ filteredInvoices.length }} 条记录</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredInvoices.length"
            layout="sizes, prev, pager, next, jumper"
            background
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </AnimatedContent>
    </main>

    <!-- 提交发票申请弹窗 -->
    <el-dialog
      v-model="submitDialogVisible"
      title="提交发票申请"
      width="520px"
      class="submit-invoice-dialog"
      destroy-on-close
      @closed="resetAiParseState"
    >
      <!-- AI 智能识别栏 -->
      <div class="ai-parse-card" :class="{ 'is-open': aiParseExpanded }">
        <div class="ai-parse-header" @click="aiStep === 'idle' && (aiParseExpanded = !aiParseExpanded)">
          <div class="ai-parse-title">
            <el-icon class="ai-sparkle-icon"><MagicStick /></el-icon>
            <span>AI 智能识别自动填单</span>
            <el-tag size="small" type="success" effect="plain" class="ai-tag">智能提取</el-tag>
          </div>
          <el-button link type="primary" size="small" class="ai-toggle-btn" :disabled="aiStep !== 'idle'">
            {{ aiParseExpanded ? '收起' : '展开文本识别' }}
          </el-button>
        </div>

        <div v-show="aiParseExpanded" class="ai-parse-body">
          <p class="ai-parse-hint">直接粘贴包含发票的文本信息，AI 将自动识别公司名称、税号、开票金额：</p>
          <el-input
            v-model="aiRawText"
            type="textarea"
            :rows="3"
            placeholder="例如：公司名称：北京某某科技有限公司，税号：91110108MA01XXXXXX，金额：1500.00元..."
            maxlength="2000"
            show-word-limit
            :disabled="aiStep !== 'idle'"
          />

          <!-- 进度条（AI 处理中） -->
          <div v-if="aiStep !== 'idle'" class="ai-progress-container">
            <div class="ai-progress-steps">
              <div class="ai-step-item" :class="aiStep === 'extracting' ? 'is-active' : 'is-done'">
                <span class="ai-step-dot">
                  <el-icon v-if="aiStep === 'extracting'" class="is-loading"><Loading /></el-icon>
                  <el-icon v-else class="ai-step-check"><Check /></el-icon>
                </span>
                <span class="ai-step-label">AI 提取中</span>
              </div>
              <span class="ai-step-line" :class="{ 'is-active': aiStep === 'verifying' }"></span>
              <div class="ai-step-item" :class="aiStep === 'verifying' ? 'is-active' : 'is-pending'">
                <span class="ai-step-dot">
                  <el-icon v-if="aiStep === 'verifying'" class="is-loading"><Loading /></el-icon>
                  <span v-else class="ai-step-number">2</span>
                </span>
                <span class="ai-step-label">AI 审核中</span>
              </div>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.max(0, Math.round(aiProgress)))"
              :stroke-width="5"
              :show-text="false"
              color="#059669"
              class="ai-progress-bar"
            />
          </div>

          <!-- 操作按鈕（空闲时） -->
          <div v-else class="ai-parse-actions">
            <el-button size="small" :disabled="!aiRawText" @click="aiRawText = ''">
              清空
            </el-button>
            <el-button
              type="primary"
              size="small"
              :icon="MagicStick"
              :disabled="!aiRawText.trim()"
              @click="handleAiParse"
            >
              识别并填充表单
            </el-button>
          </div>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="form.companyName" :prefix-icon="OfficeBuilding" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input
            v-model="form.taxNumber"
            :prefix-icon="Postcard"
            placeholder="选填，例如：91110108...（个人/无税号可留空）"
            maxlength="100"
            @input="normalizeTaxNumber"
          />
        </el-form-item>
        <el-form-item label="开票金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :max="9999999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            class="amount-input"
          />
        </el-form-item>
        <el-form-item label="开票类型" prop="invoiceType">
          <el-select v-model="form.invoiceType" placeholder="请选择开票类型" class="type-select">
            <el-option label="技术服务费" value="技术服务费" />
            <el-option label="AI订阅服务费" value="AI订阅服务费" />
            <el-option label="计算服务费" value="计算服务费" />
            <el-option label="研发和技术服务" value="研发和技术服务" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票票种" prop="invoiceCategory">
          <el-radio-group v-model="form.invoiceCategory" class="invoice-category-group">
            <el-radio value="NORMAL">
              <span class="category-label normal-label">
                <span class="normal-badge-inline">普</span>普票
              </span>
              <span class="category-desc">增值税普通发票（按开票金额扣除额度）</span>
            </el-radio>
            <el-radio value="VAT_SPECIAL">
              <span class="category-label vat-special-label">
                <span class="vat-special-badge-inline">专</span>专票
              </span>
              <span class="category-desc category-desc--warning">增值税专用发票（按开票金额 <strong>3 倍</strong>扣除额度）</span>
            </el-radio>
          </el-radio-group>
          <div v-if="form.invoiceCategory === 'VAT_SPECIAL'" class="vat-special-hint">
            <el-icon class="hint-icon"><InfoFilled /></el-icon>
            <span>专票实际扣除额度：<strong>¥{{ (form.amount * 3).toFixed(2) }}</strong>（开票金额 ¥{{ form.amount.toFixed(2) }} × 3）</span>
          </div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer submit-dialog-footer">
          <div class="submit-dialog-actions">
            <el-button @click="submitDialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="submitting"
              @click="handleSubmit"
            >
              {{ submitting ? '正在提交' : '提交申请' }}
            </el-button>
          </div>
          <div class="submit-dialog-tip">
            <el-icon class="submit-tip-icon"><InfoFilled /></el-icon>
            <span>提示：开发票需要 2 到 3 个工作日</span>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 发票图片预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="90%"
      class="invoice-preview-dialog"
      destroy-on-close
      @close="onPreviewClose"
      @closed="onPreviewClosed"
    >
      <div class="preview-body">
        <div v-if="previewError" class="preview-error">
          <span class="preview-error-icon"><PictureRounded /></span>
          <p>图片加载失败，请稍后重试或使用下载功能</p>
        </div>
        <img
          v-else-if="previewSrc"
          :src="previewSrc"
          class="preview-image"
          alt="发票图片"
          @error="previewError = true"
        />
        <div v-else class="preview-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中…</span>
        </div>
      </div>
      <template #footer v-if="previewSrc && !previewError">
        <div class="dialog-footer preview-dialog-footer">
          <el-checkbox
            v-model="isCurrentPreviewProcessed"
            :disabled="updatingProcessed"
            @change="handleToggleProcessed"
            class="preview-processed-check"
            :class="{ 'is-done': isCurrentPreviewProcessed }"
          >
            {{ isCurrentPreviewProcessed ? '✓ 已处理' : '标记已处理' }}
          </el-checkbox>
          <el-button
            v-if="previewingRow?.status === 'COMPLETED' && (!previewingRow?.redFlushStatus || previewingRow?.redFlushStatus === 'NONE' || previewingRow?.redFlushStatus === 'REJECTED')"
            type="warning"
            plain
            :icon="DocumentDelete"
            @click="handlePreviewApplyRedFlush"
          >
            {{ previewingRow?.redFlushStatus === 'REJECTED' ? '重申红冲' : '申请红冲' }}
          </el-button>
          <el-button type="primary" plain :icon="CopyDocument" @click="handleCopyPreviewImage">复制图片</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- AI 识别结果确认弹窗 -->
    <el-dialog
      v-model="aiConfirmVisible"
      title="AI 识别结果"
      width="420px"
      class="ai-confirm-dialog"
      :close-on-click-modal="false"
      :append-to-body="true"
    >
      <div class="ai-confirm-body">
        <div class="ai-confirm-intro">
          <el-icon class="ai-confirm-sparkle"><MagicStick /></el-icon>
          <span>AI 已完成识别，点击「填入表单」将数据回填，您可手动复核后再提交</span>
        </div>
        <dl class="ai-confirm-fields">
          <div class="ai-confirm-row">
            <dt>公司名称</dt>
            <dd>{{ aiConfirmData?.companyName || '-' }}</dd>
          </div>
          <div class="ai-confirm-row">
            <dt>税号</dt>
            <dd class="ai-confirm-code">{{ aiConfirmData?.taxNumber || '-' }}</dd>
          </div>
          <div class="ai-confirm-row">
            <dt>开票金额</dt>
            <dd class="ai-confirm-money">{{ aiConfirmData?.amount != null ? formatCurrency(aiConfirmData.amount) : '-' }}</dd>
          </div>
          <div class="ai-confirm-row">
            <dt>开票类型</dt>
            <dd>
              <el-tag
                v-if="(aiConfirmData?.invoiceType?.trim() || form.invoiceType?.trim() || '技术服务费') !== '技术服务费'"
                size="small"
                type="danger"
                effect="plain"
                class="warning-type-tag"
              >
                <el-icon class="warning-icon-inline"><Warning /></el-icon>
                <span>{{ aiConfirmData?.invoiceType || form.invoiceType }}</span>
              </el-tag>
              <el-tag
                v-else
                size="small"
                type="info"
                effect="plain"
              >
                {{ aiConfirmData?.invoiceType || form.invoiceType || '技术服务费' }}
              </el-tag>
            </dd>
          </div>
        </dl>
        <div class="ai-confirm-hint">
          <el-icon><InfoFilled /></el-icon>
          请在表单中仔细核对 AI 识别结果是否准确，确认无误后再点击「提交申请」
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="aiConfirmVisible = false">重新识别</el-button>
          <el-button
            type="primary"
            :icon="EditPen"
            @click="handleAiConfirm"
          >
            填入表单
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 申请发票红冲弹窗 -->
    <el-dialog
      v-model="applyRedFlushVisible"
      title="申请发票红冲"
      width="540px"
      destroy-on-close
      class="red-flush-dialog"
    >
      <div v-if="targetRedFlushInvoice" class="red-flush-dialog-content">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="red-flush-alert"
          title="红冲说明"
          description="提交红冲申请后，开票员将核实发票信息并标记作废冲红；标记确认后，系统将自动把该发票金额全额退还至您的开票额度账户。"
        />
        <div class="target-invoice-summary">
          <div class="summary-item">
            <span class="summary-label">发票抬头：</span>
            <strong>{{ targetRedFlushInvoice.companyName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">企业税号：</span>
            <span>{{ targetRedFlushInvoice.taxNumber || '-' }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">开票金额：</span>
            <span class="money-cell">{{ formatCurrency(targetRedFlushInvoice.amount) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">开票类型：</span>
            <span>{{ targetRedFlushInvoice.invoiceType || '技术服务费' }}</span>
          </div>
          <div v-if="targetRedFlushInvoice.redFlushStatus === 'REJECTED' && targetRedFlushInvoice.redFlushRemark" class="summary-item rejected-reason-item">
            <span class="summary-label">上次驳回原因：</span>
            <span class="rejected-text">{{ targetRedFlushInvoice.redFlushRemark }}</span>
          </div>
        </div>

        <div class="red-flush-reason-section">
          <div class="section-title">快捷选择原因</div>
          <div class="quick-reasons">
            <el-tag
              v-for="r in commonRedFlushReasons"
              :key="r"
              class="quick-reason-tag"
              effect="plain"
              @click="handleSelectQuickReason(r)"
            >
              {{ r }}
            </el-tag>
          </div>
          <div class="section-title reason-input-title">详细申请原因 <span class="required-star">*</span></div>
          <el-input
            v-model="redFlushReason"
            type="textarea"
            :rows="3"
            placeholder="请详细说明申请红冲的具体原因（如：税号填写错误需重开、业务已退款作废等）"
            maxlength="500"
            show-word-limit
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="applyRedFlushVisible = false">取消</el-button>
          <el-button
            type="danger"
            :loading="redFlushSubmitting"
            :disabled="!redFlushReason.trim()"
            @click="handleSubmitRedFlush"
          >
            确认提交红冲申请
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <InvoiceBatchImportDialog
      v-model="batchImportVisible"
      @success="handleBatchImportSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  Calendar,
  Check,
  CircleCheck,
  CircleClose,
  Clock,
  Coin,
  Connection,
  CopyDocument,
  Document,
  DocumentDelete,
  Download,
  EditPen,
  Files,
  Filter,
  InfoFilled,
  List,
  Loading,
  MagicStick,
  MoreFilled,
  OfficeBuilding,
  PictureRounded,
  Plus,
  Postcard,
  Promotion,
  RefreshRight,
  Search,
  Tickets,
  Upload,
  Wallet,
  Warning,
  ZoomIn
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice, type InvoiceRequest } from '@/api/invoice'
import { quotaApi } from '@/api/quota'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import InvoiceBatchImportDialog from '@/components/InvoiceBatchImportDialog.vue'
import { saveBlobResponse } from '@/utils/download'
import { copyImageToClipboard } from '@/utils/clipboard'
import { generateIdempotencyKey } from '@/utils/idempotency'
import { ApiRequestError } from '@/utils/request'

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const invoices = ref<Invoice[]>([])
const pendingIdempotencyKey = ref<string | null>(null)
const copyingId = ref<number | null>(null)
const cancellingId = ref<number | null>(null)

// 筛选相关状态
const searchKeyword = ref('')
const statusFilter = ref<'ALL' | 'PENDING' | 'COMPLETED' | 'COMPLETED_UNPROCESSED' | 'COMPLETED_PROCESSED' | 'RED_FLUSH_PENDING' | 'RED_FLUSH_COMPLETED' | 'CANCELLED'>('ALL')
const submissionTypeFilter = ref<'ALL' | 'API' | 'MANUAL'>('ALL')
const dateRange = ref<[string, string] | null>(null)
const dateShortcuts = [
  {
    text: '近 7 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
      return [start, end]
    }
  },
  {
    text: '近 30 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 30)
      return [start, end]
    }
  },
  {
    text: '本月',
    value: () => {
      const end = new Date()
      const start = new Date(end.getFullYear(), end.getMonth(), 1)
      return [start, end]
    }
  }
]

const filteredInvoices = computed(() => {
  return invoices.value.filter(invoice => {
    // 关键字搜索 (公司名称、税号、发票编号、备注、开票类型、文件名、金额)
    const kw = searchKeyword.value.trim().toLowerCase()
    if (kw) {
      const formattedId = formatInvoiceId(invoice.id).toLowerCase()
      const rawId = String(invoice.id)
      const matchesKeyword =
        (invoice.companyName && invoice.companyName.toLowerCase().includes(kw)) ||
        (invoice.taxNumber && invoice.taxNumber.toLowerCase().includes(kw)) ||
        (invoice.remark && invoice.remark.toLowerCase().includes(kw)) ||
        (invoice.invoiceType && invoice.invoiceType.toLowerCase().includes(kw)) ||
        (invoice.fileName && invoice.fileName.toLowerCase().includes(kw)) ||
        formattedId.includes(kw) ||
        rawId.includes(kw) ||
        String(invoice.amount).includes(kw) ||
        (typeof invoice.amount === 'number' && invoice.amount.toFixed(2).includes(kw)) ||
        (kw === 'api' && invoice.submissionType === 'API') ||
        (kw.includes('手动') && invoice.submissionType === 'MANUAL')
      if (!matchesKeyword) return false
    }

    // 提交方式筛选
    if (submissionTypeFilter.value !== 'ALL') {
      const type = invoice.submissionType || 'UNKNOWN'
      if (type !== submissionTypeFilter.value) return false
    }

    // 状态筛选
    if (statusFilter.value === 'COMPLETED_PROCESSED') {
      if (invoice.status !== 'COMPLETED' || !invoice.isProcessed || invoice.redFlushStatus === 'COMPLETED') return false
    } else if (statusFilter.value === 'COMPLETED_UNPROCESSED') {
      if (invoice.status !== 'COMPLETED' || invoice.isProcessed || invoice.redFlushStatus === 'COMPLETED') return false
    } else if (statusFilter.value === 'RED_FLUSH_PENDING') {
      if (invoice.status !== 'COMPLETED' || invoice.redFlushStatus !== 'PENDING') return false
    } else if (statusFilter.value === 'RED_FLUSH_COMPLETED') {
      if (invoice.status !== 'COMPLETED' || invoice.redFlushStatus !== 'COMPLETED') return false
    } else if (statusFilter.value !== 'ALL' && invoice.status !== statusFilter.value) {
      return false
    }
    // 上传时间/申请时间筛选
    if (dateRange.value && dateRange.value.length === 2 && dateRange.value[0] && dateRange.value[1]) {
      const startMs = new Date(`${dateRange.value[0]}T00:00:00`).getTime()
      const endMs = new Date(`${dateRange.value[1]}T23:59:59.999`).getTime()
      const createdAtMs = new Date(invoice.createdAt).getTime()
      if (Number.isNaN(createdAtMs) || createdAtMs < startMs || createdAtMs > endMs) {
        return false
      }
    }
    return true
  })
})

const page = ref(1)
const pageSize = ref(10)

const paginatedInvoices = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredInvoices.value.slice(start, start + pageSize.value)
})

const handleSearch = () => {
  page.value = 1
}

const emptyText = computed(() => {
  if (invoices.value.length === 0) return '暂无发票记录'
  if (searchKeyword.value.trim()) return '未找到匹配的发票记录'
  if (statusFilter.value !== 'ALL' || dateRange.value || submissionTypeFilter.value !== 'ALL') return '未找到符合筛选条件的发票记录'
  return '暂无发票记录'
})

watch([searchKeyword, statusFilter, dateRange, submissionTypeFilter], () => {
  page.value = 1
})

watch(filteredInvoices, (newList) => {
  const maxPage = Math.ceil(newList.length / pageSize.value) || 1
  if (page.value > maxPage) {
    page.value = maxPage
  }
})

const handlePageSizeChange = (val: number) => {
  pageSize.value = val
  page.value = 1
}

const handleCurrentChange = (val: number) => {
  page.value = val
}

// 批量导入相关
const batchImportVisible = ref(false)

// 额度相关状态
const quotaBalance = ref(0)

// 预览相关状态
const previewVisible = ref(false)
const previewingId = ref<number | null>(null)
const previewingRow = ref<Invoice | null>(null)
const previewSrc = ref<string | null>(null)
const previewTitle = ref('')
const previewError = ref(false)
let previewController: AbortController | null = null
let previewRequestId = 0

// 已处理标记状态（服务端持久化与实时同步）
const updatingProcessed = ref(false)
const isCurrentPreviewProcessed = computed({
  get: () => Boolean(previewingRow.value?.isProcessed),
  set: (val: boolean) => {
    if (previewingRow.value) {
      previewingRow.value.isProcessed = val
    }
  }
})

const handleToggleProcessed = async (val: boolean | string | number) => {
  if (!previewingRow.value) return
  const row = previewingRow.value
  const targetVal = Boolean(val)
  const originalVal = !targetVal
  updatingProcessed.value = true
  try {
    const updated = await invoiceApi.updateProcessed(row.id, targetVal)
    row.isProcessed = updated.isProcessed
    const targetItem = invoices.value.find(item => item.id === row.id)
    if (targetItem) {
      targetItem.isProcessed = updated.isProcessed
    }
    ElMessage.success(targetVal ? '已标记为已处理' : '已取消处理处理标记')
  } catch {
    row.isProcessed = originalVal
    const targetItem = invoices.value.find(item => item.id === row.id)
    if (targetItem) {
      targetItem.isProcessed = originalVal
    }
    ElMessage.error('更新处理状态失败，请重试')
  } finally {
    updatingProcessed.value = false
  }
}

// 表格行快速切换已处理状态
const togglingRowId = ref<number | null>(null)
const handleDirectToggleRow = async (row: Invoice) => {
  if (togglingRowId.value !== null) return
  if (row.status !== 'COMPLETED') return
  const targetVal = !row.isProcessed
  const originalVal = row.isProcessed
  togglingRowId.value = row.id
  row.isProcessed = targetVal
  try {
    const updated = await invoiceApi.updateProcessed(row.id, targetVal)
    row.isProcessed = updated.isProcessed
    ElMessage.success(targetVal ? '已标记为已处理' : '已取消处理标记')
  } catch {
    row.isProcessed = originalVal
    ElMessage.error('更新处理状态失败，请重试')
  } finally {
    togglingRowId.value = null
  }
}

// 自动将旧版存储在客户端 localStorage 的历史已处理记录无感迁移至服务端数据库
const migrateLegacyProcessedData = async () => {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return
    const keysToMigrate: string[] = []
    const allLegacyIds = new Set<number>()

    const len = window.localStorage.length || 0
    for (let i = 0; i < len; i++) {
      const key = typeof window.localStorage.key === 'function' ? window.localStorage.key(i) : null
      if (key && key.startsWith('processedInvoiceIds_')) {
        keysToMigrate.push(key)
        try {
          const raw = window.localStorage.getItem(key)
          if (raw) {
            const ids = JSON.parse(raw)
            if (Array.isArray(ids)) {
              ids.forEach(id => {
                const num = Number(id)
                if (Number.isFinite(num)) allLegacyIds.add(num)
              })
            }
          }
        } catch { /* ignore parse error */ }
      }
    }

    if (allLegacyIds.size > 0) {
      const idArray = Array.from(allLegacyIds)
      const chunkSize = 200
      for (let i = 0; i < idArray.length; i += chunkSize) {
        const chunk = idArray.slice(i, i + chunkSize)
        await invoiceApi.batchUpdateProcessed(chunk, true)
      }
      for (const inv of invoices.value) {
        if (allLegacyIds.has(inv.id) && inv.status === 'COMPLETED') {
          inv.isProcessed = true
        }
      }
    }

    keysToMigrate.forEach(k => window.localStorage.removeItem(k))
  } catch (e) {
    console.warn('历史已处理记录自动同步异常:', e)
  }
}

const form = reactive<InvoiceRequest>({
  companyName: '',
  taxNumber: '',
  amount: 0.01,
  invoiceType: '技术服务费',
  invoiceCategory: 'NORMAL',
  remark: ''
})

const pendingCount = computed(() => invoices.value.filter(invoice => invoice.status === 'PENDING').length)
const completedCount = computed(() => invoices.value.filter(invoice => invoice.status === 'COMPLETED' && invoice.redFlushStatus !== 'COMPLETED').length)
const completedAmount = computed(() => invoices.value
  .filter(invoice => invoice.status === 'COMPLETED' && invoice.redFlushStatus !== 'COMPLETED')
  .reduce((total, invoice) => total + Number(invoice.amount), 0))

const rules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  taxNumber: [
    { max: 100, message: '税号不能超过 100 个字符', trigger: 'blur' }
  ],
  amount: [
    { required: true, message: '请输入开票金额', trigger: 'blur' },
    { type: 'number', min: 0.01, max: 9999999999.99, message: '开票金额必须在有效范围内', trigger: 'change' }
  ],
  invoiceType: [{ required: true, message: '请选择开票类型', trigger: 'change' }]
}

const formatCurrency = (amount: number) => new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
}).format(Number(amount))

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

const formatDateParts = (value: string) => {
  const formatted = formatDate(value)
  const separatorIndex = formatted.lastIndexOf(' ')
  if (separatorIndex < 0) return { date: formatted, time: '' }
  return {
    date: formatted.slice(0, separatorIndex),
    time: formatted.slice(separatorIndex + 1)
  }
}

const formatInvoiceId = (id: number) => `#${String(id).padStart(4, '0')}`

const getStatusLabel = (item: Invoice | string) => {
  if (typeof item === 'object' && item !== null) {
    if (item.status === 'COMPLETED') {
      if (item.redFlushStatus === 'PENDING') return '待红冲'
      if (item.redFlushStatus === 'COMPLETED') return '已红冲'
      if (item.redFlushStatus === 'REJECTED') return '红冲驳回'
      return '已开票'
    }
    if (item.status === 'PENDING') return '待开票'
    if (item.status === 'CANCELLED') return '已取消'
    return item.status
  }
  switch (item) {
    case 'COMPLETED':
      return '已开票'
    case 'PENDING':
      return '待开票'
    case 'CANCELLED':
      return '已取消'
    case 'RED_FLUSH_PENDING':
      return '待红冲'
    case 'RED_FLUSH_COMPLETED':
      return '已红冲'
    default:
      return item
  }
}

const getStatusClass = (item: Invoice | string) => {
  if (typeof item === 'object' && item !== null) {
    if (item.status === 'COMPLETED') {
      if (item.redFlushStatus === 'PENDING') return 'is-red-flush-pending'
      if (item.redFlushStatus === 'COMPLETED') return 'is-red-flushed'
      if (item.redFlushStatus === 'REJECTED') return 'is-red-flush-rejected'
      return 'is-completed'
    }
    if (item.status === 'PENDING') return 'is-pending'
    if (item.status === 'CANCELLED') return 'is-cancelled'
    return ''
  }
  switch (item) {
    case 'COMPLETED':
      return 'is-completed'
    case 'PENDING':
      return 'is-pending'
    case 'CANCELLED':
      return 'is-cancelled'
    case 'RED_FLUSH_PENDING':
      return 'is-red-flush-pending'
    case 'RED_FLUSH_COMPLETED':
      return 'is-red-flushed'
    default:
      return ''
  }
}

const getCompanyInitial = (companyName: string) => companyName.trim().charAt(0) || '企'

const normalizeTaxNumber = (value: string) => {
  form.taxNumber = value
}

const loadInvoices = async () => {
  loading.value = true
  try {
    invoices.value = await invoiceApi.getMyInvoices()
    await migrateLegacyProcessedData()
  } catch (error) {
    console.error('加载发票列表失败', error)
  } finally {
    loading.value = false
  }
}

const loadQuota = async () => {
  try {
    const quota = await quotaApi.getMyQuota()
    quotaBalance.value = quota.balance
  } catch (error) {
    console.error('加载额度信息失败', error)
  }
}

// 提交申请弹窗与 AI 识别状态
const submitDialogVisible = ref(false)
const aiParseExpanded = ref(false)
const aiRawText = ref('')
const aiStep = ref<'idle' | 'extracting' | 'verifying'>('idle')
const aiProgress = ref(0)
const aiConfirmVisible = ref(false)
const aiConfirmData = ref<{
  companyName: string | null
  taxNumber: string | null
  amount: number | null
  invoiceType: string
} | null>(null)
let rafId: number | null = null
/** 当前动画的 resolve 函数，用于在 resetAiParseState 中翟时解封 Promise，防止异步函数永久挂起 */
let currentAnimationResolver: (() => void) | null = null
/** 会话 ID：每次 resetAiParseState 自增，防止诎期 API 响应在重置后弹出确认框 */
let aiParseSessionId = 0

const showSubmitDialog = () => {
  submitDialogVisible.value = true
}

const resetAiParseState = () => {
  aiParseSessionId++                              // 使所有进行中的 handleAiParse 尽快退出
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
  if (currentAnimationResolver) { currentAnimationResolver(); currentAnimationResolver = null } // 翟时解封成2 Promise
  aiParseExpanded.value = false
  aiRawText.value = ''
  aiStep.value = 'idle'
  aiProgress.value = 0
  aiConfirmVisible.value = false
  aiConfirmData.value = null
}

/** requestAnimationFrame 驱动的进度条平滑动画 */
const animateProgress = (from: number, to: number, durationMs: number): Promise<void> => {
  // 如果有正在进行的动画，先解封其 Promise（避免挂起）
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
  if (currentAnimationResolver) { currentAnimationResolver(); currentAnimationResolver = null }
  return new Promise(resolve => {
    currentAnimationResolver = resolve
    aiProgress.value = from
    const startTime = Date.now()
    const tick = () => {
      const elapsed = Date.now() - startTime
      const t = durationMs <= 0 ? 1 : Math.min(1, elapsed / durationMs)
      aiProgress.value = from + (to - from) * t
      if (t < 1) {
        rafId = requestAnimationFrame(tick)
      } else {
        rafId = null
        currentAnimationResolver = null
        resolve()
      }
    }
    rafId = requestAnimationFrame(tick)
  })
}

const handleAiParse = async () => {
  const text = aiRawText.value.trim()
  if (!text) {
    ElMessage.warning('请先输入或粘贴包含发票信息的文本')
    return
  }

  const sessionId = aiParseSessionId // 捕获当前会话，用于检测是否被重置

  aiStep.value = 'extracting'
  aiProgress.value = 0

  try {
    // 第一阶段：提取（动画 0→45% 与 API 调用并行，取较慢者）
    const [res1] = await Promise.all([
      invoiceApi.parseInvoiceText(text),
      animateProgress(0, 45, 1500)
    ])
    if (aiParseSessionId !== sessionId) return // 弹窗已关闭，丢弃结果

    if (!res1.companyName && !res1.taxNumber && res1.amount === null) {
      aiStep.value = 'idle'
      aiProgress.value = 0
      ElMessage.warning(res1.hint || '未能从文本中识别出发票相关信息，请手动填写')
      return
    }

    // 第二阶段：审核（动画 50→90% 与 API 调用并行）
    aiStep.value = 'verifying'
    aiProgress.value = 50

    const [res2] = await Promise.all([
      invoiceApi.verifyInvoiceText(text, {
        companyName: res1.companyName,
        taxNumber: res1.taxNumber,
        amount: res1.amount !== null ? Number(res1.amount) : null,
        invoiceType: res1.invoiceType ?? null
      }),
      animateProgress(50, 90, 1500)
    ])
    if (aiParseSessionId !== sessionId) return // 弹窗已关闭，丢弃结果

    // 完成动画至 100%
    await animateProgress(aiProgress.value, 100, 300)
    if (aiParseSessionId !== sessionId) return
    await new Promise<void>(r => setTimeout(r, 200))
    if (aiParseSessionId !== sessionId) return

    // 合并结果：优先使用审核结果，回退到提取结果
    const companyName = res2.companyName || res1.companyName || null
    const taxNumber   = res2.taxNumber   || res1.taxNumber   || null
    const rawAmount   = res2.amount      ?? res1.amount
    const amount = rawAmount !== null
      ? (() => {
          const n = Math.round(Number(rawAmount) * 100) / 100
          return Number.isFinite(n) && n >= 0.01 ? n : null
        })()
      : null

    aiStep.value = 'idle'
    aiProgress.value = 0
    // invoiceType 优先使用 AI 识别结果，未识别到（null）时回退到表单当前值
    const recognizedInvoiceType = res2.invoiceType || res1.invoiceType || form.invoiceType || '技术服务费'
    aiConfirmData.value = { companyName, taxNumber, amount, invoiceType: recognizedInvoiceType }
    aiConfirmVisible.value = true

  } catch (error: any) {
    aiStep.value = 'idle'
    if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
    aiProgress.value = 0
    console.error('AI 识别发票信息失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error(error.message || 'AI 识别失败，请稍后重试或手动输入')
    }
  }
}

/** 用户在确认弹窗中点击「填入表单」— 只回填，不自动提交，让用户手动复核后再提交 */
const handleAiConfirm = () => {
  const data = aiConfirmData.value
  if (!data) return

  // 将 AI 识别结果回填到表单
  if (data.companyName) form.companyName = data.companyName.trim()
  if (data.taxNumber)   form.taxNumber   = data.taxNumber.trim()
  if (data.amount !== null && Number.isFinite(data.amount) && data.amount >= 0.01) {
    form.amount = data.amount
  }
  form.invoiceType = data.invoiceType || form.invoiceType || '技术服务费'

  // 关闭确认弹窗，收起 AI 面板，让用户回到表单自行复核
  aiConfirmVisible.value = false
  aiConfirmData.value = null
  aiParseExpanded.value = false
  ElMessage.success('AI 识别结果已回填，请复核后点击「提交申请」')
}

const handleSubmit = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请检查并完善申请信息')
      return
    }

    // 检查额度是否充足（专票按 3 倍扣除）
    const requiredQuota = form.invoiceCategory === 'VAT_SPECIAL' ? form.amount * 3 : form.amount
    if (requiredQuota > quotaBalance.value) {
      ElMessage.error(
        form.invoiceCategory === 'VAT_SPECIAL'
          ? `额度不足，当前余额 ¥${quotaBalance.value.toFixed(2)}，专票按 3 倍扣除需 ¥${requiredQuota.toFixed(2)}`
          : `额度不足，当前余额 ¥${quotaBalance.value.toFixed(2)}，需要 ¥${form.amount.toFixed(2)}`
      )
      return
    }

    const idempotencyKey = pendingIdempotencyKey.value || generateIdempotencyKey()
    pendingIdempotencyKey.value = idempotencyKey
    await invoiceApi.createInvoice({
      companyName: form.companyName.trim(),
      taxNumber: form.taxNumber?.trim() || undefined,
      amount: form.amount,
      invoiceType: form.invoiceType,
      invoiceCategory: form.invoiceCategory || 'NORMAL',
      remark: form.remark?.trim() || undefined
    }, idempotencyKey)
    ElMessage.success('提交成功')
    pendingIdempotencyKey.value = null
    formRef.value?.resetFields()
    submitDialogVisible.value = false
    await loadInvoices()
    await loadQuota() // 刷新额度
  } catch (error) {
    // 保留幂等键，网络失败后再次提交会复用同一请求
    console.error('提交发票申请失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error('提交失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

const handlePreview = async (row: Invoice) => {
  if (previewingId.value !== null) return
  const requestId = ++previewRequestId
  previewController = new AbortController()
  previewingId.value = row.id
  previewingRow.value = row
  previewTitle.value = `发票预览 — ${row.companyName}` + (row.invoiceCategory === 'VAT_SPECIAL' ? ' 【专票】' : '')
  previewError.value = false
  previewSrc.value = null
  previewVisible.value = true

  try {
    const response = await invoiceApi.previewInvoice(row.id, previewController.signal)
    const blob = response.data
    const objectUrl = URL.createObjectURL(blob)
    if (requestId !== previewRequestId || !previewVisible.value) {
      URL.revokeObjectURL(objectUrl)
      return
    }
    releasePreviewUrl()
    previewSrc.value = objectUrl
  } catch {
    if (requestId === previewRequestId && !previewController?.signal.aborted) {
      previewError.value = true
    }
  } finally {
    if (requestId === previewRequestId) {
      previewController = null
      previewingId.value = null
    }
  }
}

const releasePreviewUrl = () => {
  if (previewSrc.value) {
    URL.revokeObjectURL(previewSrc.value)
    previewSrc.value = null
  }
}

const onPreviewClose = () => {
  previewRequestId += 1
  previewController?.abort()
  previewController = null
  previewingId.value = null
  releasePreviewUrl()
}

const onPreviewClosed = () => {
  previewError.value = false
  // Only clear the row reference when the dialog is truly closed.
  // If the user quickly opens another invoice during the close animation,
  // previewVisible will already be true again — don't overwrite the new row.
  if (!previewVisible.value) {
    previewingRow.value = null
  }
}

const handleDownload = async (row: Invoice) => {
  try {
    const response = await invoiceApi.downloadInvoice(row.id)
    saveBlobResponse(response, row.fileName || `发票_${row.companyName}_${row.id}`)
    ElMessage.success('下载成功')
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

const handleCopyImage = async (row: Invoice) => {
  if (copyingId.value !== null) return
  copyingId.value = row.id
  try {
    const response = await invoiceApi.previewInvoice(row.id)
    const blob = response.data

    if (blob.type === 'application/pdf' || row.fileName?.toLowerCase().endsWith('.pdf')) {
      ElMessage.warning('该发票为 PDF 格式，暂不支持直接复制图片，请使用下载功能')
      return
    }

    await copyImageToClipboard(blob)
    ElMessage.success('发票图片已复制到剪贴板')
  } catch (error: any) {
    console.error('复制发票图片失败', error)
    const msg = error?.message || '复制图片失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    copyingId.value = null
  }
}

const handleCancel = async (row: Invoice) => {
  if (cancellingId.value !== null) return
  
  try {
    await ElMessageBox.confirm(
      `确定要取消编号为 ${formatInvoiceId(row.id)} 的发票申请吗？取消后将退还开票金额 ${formatCurrency(row.amount)} 到您的账户余额。`,
      '取消发票申请',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  cancellingId.value = row.id
  try {
    await invoiceApi.cancelInvoice(row.id)
    ElMessage.success('发票申请已取消，金额已退还到账户余额')
    await loadInvoices()
    await loadQuota()
  } catch (error: any) {
    console.error('取消发票申请失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error(error?.message || '取消失败，请稍后重试')
    }
  } finally {
    cancellingId.value = null
  }
}

const handleCopyPreviewImage = async () => {
  if (!previewSrc.value) return
  try {
    const res = await fetch(previewSrc.value)
    const blob = await res.blob()
    await copyImageToClipboard(blob)
    ElMessage.success('发票图片已复制到剪贴板')
  } catch (error: any) {
    ElMessage.error(error?.message || '复制图片失败，请稍后重试')
  }
}

const showBatchImportDialog = () => {
  batchImportVisible.value = true
}

const handleBatchImportSuccess = async () => {
  await Promise.all([loadInvoices(), loadQuota()])
}

// 申请发票红冲相关状态与操作
const applyRedFlushVisible = ref(false)
const targetRedFlushInvoice = ref<Invoice | null>(null)
const redFlushReason = ref('')
const redFlushSubmitting = ref(false)
const commonRedFlushReasons = [
  '企业抬头/税号填写有误',
  '开票金额有误需重开',
  '业务发生退款/订单取消',
  '开票类目选择错误',
  '其他原因'
]

const handleOpenApplyRedFlush = (row: Invoice) => {
  targetRedFlushInvoice.value = row
  redFlushReason.value = row.redFlushReason || ''
  applyRedFlushVisible.value = true
}

const handlePreviewApplyRedFlush = () => {
  if (!previewingRow.value) return
  handleOpenApplyRedFlush(previewingRow.value)
}

const handleDropdownCommand = (command: string, row: Invoice) => {
  if (command === 'download') {
    handleDownload(row)
  } else if (command === 'copy') {
    handleCopyImage(row)
  } else if (command === 'applyRedFlush') {
    handleOpenApplyRedFlush(row)
  }
}

const handleSelectQuickReason = (reason: string) => {
  if (reason === '其他原因') {
    redFlushReason.value = ''
  } else {
    redFlushReason.value = reason
  }
}

const handleSubmitRedFlush = async () => {
  if (!targetRedFlushInvoice.value) return
  const reason = redFlushReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写申请红冲的原因')
    return
  }

  redFlushSubmitting.value = true
  const invoiceId = targetRedFlushInvoice.value.id
  try {
    await invoiceApi.applyRedFlush(invoiceId, reason)
    applyRedFlushVisible.value = false
    ElMessage.success('红冲申请已提交，请等待开票员核验标记')

    // 跨标签页即时同步通知开票员/管理员
    if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
      try {
        const bc = new BroadcastChannel('bobapi-invoice-events')
        bc.postMessage({ type: 'invoice-red-flush-applied', invoiceId })
        bc.close()
      } catch (_) {}
    }
    window.dispatchEvent(new CustomEvent('invoice-red-flush-applied', { detail: { invoiceId } }))

    await Promise.all([loadInvoices(), loadQuota()])
  } catch (error: any) {
    console.error('提交红冲申请失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error(error?.message || '提交红冲申请失败，请稍后重试')
    }
  } finally {
    redFlushSubmitting.value = false
  }
}

watch(
  () => [form.companyName, form.taxNumber, form.amount, form.invoiceType, form.remark],
  () => {
    if (!submitting.value) pendingIdempotencyKey.value = null
  }
)

let userInvoiceBroadcastChannel: BroadcastChannel | null = null

const handleRefreshOnProcessed = () => {
  loadInvoices()
  loadQuota()
}

onMounted(() => {
  loadInvoices()
  loadQuota()

  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      userInvoiceBroadcastChannel = new BroadcastChannel('bobapi-invoice-events')
      userInvoiceBroadcastChannel.onmessage = (event) => {
        if (event.data?.type === 'invoice-red-flush-processed') {
          handleRefreshOnProcessed()
        }
      }
    } catch (_) {}
  }
  window.addEventListener('invoice-red-flush-processed', handleRefreshOnProcessed)
})

onBeforeUnmount(() => {
  onPreviewClose()
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
  if (userInvoiceBroadcastChannel) {
    userInvoiceBroadcastChannel.close()
    userInvoiceBroadcastChannel = null
  }
  window.removeEventListener('invoice-red-flush-processed', handleRefreshOnProcessed)
})
</script>

<style scoped>
.records-panel .panel-header {
  flex-wrap: wrap;
  gap: 16px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-control {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.search-input {
  width: 220px !important;
  flex-shrink: 0 !important;
}

.search-button {
  flex-shrink: 0;
}

.date-picker {
  width: 250px !important;
  flex-shrink: 0 !important;
}

.status-select {
  width: 125px !important;
  flex-shrink: 0 !important;
}

.submission-type-select {
  width: 125px !important;
  flex-shrink: 0 !important;
}

.action-buttons-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.submit-action-button {
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(18, 113, 91, 0.2);
}

.batch-import-button {
  flex-shrink: 0;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-color: #c8ded7;
}

.amount-input,
.type-select {
  width: 100%;
}

.batch-import-button:hover,
.batch-import-button:focus-visible {
  color: #fff;
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.result-count {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  color: var(--color-text-secondary);
  background: var(--color-primary-soft);
  border: 1px solid #d6e8e2;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
}

.result-count i {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(18, 113, 91, 0.1);
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

.invoice-id {
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

.company-cell {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.company-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  color: var(--color-primary);
  background: linear-gradient(145deg, #edf7f4, #dfeee9);
  border: 1px solid #d7e8e2;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 700;
}

.company-name {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.status-tag :deep(.el-tag__content) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 12%, transparent);
}

.record-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  white-space: nowrap;
}

.record-actions :deep(.el-button),
.record-actions .el-button {
  margin-left: 0 !important;
}

.record-actions :deep(.record-action-button.el-button) {
  min-width: auto;
  height: 28px;
  min-height: 28px;
  margin: 0;
  padding: 0 8px;
  font-size: 12px;
  border-radius: 6px;
}

.record-actions :deep(.icon-only-btn) {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.record-actions :deep(.el-dropdown) {
  display: inline-flex;
  vertical-align: middle;
}

.record-actions :deep(.more-dropdown-btn) {
  color: var(--color-text-secondary);
  border-color: var(--color-border);
}

.record-actions :deep(.more-dropdown-btn:hover) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--color-primary-soft);
}

.empty-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.empty-action i {
  width: 6px;
  height: 6px;
  background: #aab4b0;
  border-radius: 50%;
}

.mobile-records {
  display: none;
}

.amount-stat {
  font-size: 22px;
}

/* 提交申请弹窗 */
:global(.submit-invoice-dialog) {
  max-width: 520px;
}

:global(.submit-invoice-dialog .el-dialog__body) {
  padding: 16px 24px 8px;
}

/* AI 智能识别卡片 */
.ai-parse-card {
  margin-bottom: 16px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.04) 0%, rgba(6, 95, 70, 0.06) 100%);
  border: 1px dashed rgba(16, 185, 129, 0.35);
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.ai-parse-card.is-open {
  border-style: solid;
  border-color: rgba(16, 185, 129, 0.5);
  background: #f8fdfa;
}

.ai-parse-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
}

.ai-parse-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #065f46;
}

.ai-sparkle-icon {
  font-size: 15px;
  color: #059669;
}

.ai-tag {
  font-size: 11px;
  height: 20px;
  padding: 0 6px;
}

.ai-toggle-btn {
  font-size: 12px;
  padding: 0;
}

.ai-parse-body {
  padding: 0 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-parse-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted, #6b7280);
  line-height: 1.4;
}

.ai-parse-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* AI 两阶段进度条 */
.ai-progress-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.ai-progress-steps {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-step-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  transition: color 0.25s;
  white-space: nowrap;
}

.ai-step-item.is-active { color: #059669; }
.ai-step-item.is-done   { color: #059669; opacity: 0.75; }

.ai-step-dot {
  display: grid;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  place-items: center;
  background: #f0faf6;
  border: 1.5px solid #d0e9df;
  border-radius: 50%;
  font-size: 11px;
  transition: all 0.25s;
}

.ai-step-item.is-active .ai-step-dot {
  background: #059669;
  border-color: #059669;
  color: #fff;
}

.ai-step-item.is-done .ai-step-dot {
  background: #d1fae5;
  border-color: #6ee7b7;
  color: #059669;
}

.ai-step-check { font-size: 11px; }

.ai-step-number {
  font-size: 10px;
  font-weight: 700;
}

.ai-step-line {
  flex: 1;
  height: 1.5px;
  background: #e0ede8;
  border-radius: 1px;
  transition: background 0.3s;
}

.ai-step-line.is-active { background: #059669; }

.ai-progress-bar :deep(.el-progress-bar__outer) {
  background: #e8f5f0;
  border-radius: 99px;
}

/* AI 识别结果确认弹窗 */
:global(.ai-confirm-dialog) {
  max-width: 420px;
}

:global(.ai-confirm-dialog .el-dialog__body) {
  padding: 4px 24px 16px;
}

.ai-confirm-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-confirm-intro {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.06), rgba(6, 95, 70, 0.08));
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 8px;
  font-size: 13px;
  color: #065f46;
  line-height: 1.4;
}

.ai-confirm-sparkle {
  font-size: 16px;
  color: #059669;
  flex-shrink: 0;
}

.ai-confirm-fields {
  margin: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.ai-confirm-row {
  display: grid;
  grid-template-columns: 76px 1fr;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--color-border);
}

.ai-confirm-row:last-child { border-bottom: none; }

.ai-confirm-row dt {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.ai-confirm-row dd {
  margin: 0;
  color: var(--color-text);
  font-size: 13px;
  font-weight: 500;
  word-break: break-all;
}

.ai-confirm-code {
  font-family: 'SFMono-Regular', Consolas, monospace !important;
  font-size: 12px !important;
  letter-spacing: 0.04em;
}

.ai-confirm-money {
  font-size: 17px !important;
  font-weight: 700 !important;
  color: var(--color-text) !important;
}

.ai-confirm-hint {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 12px;
}

.ai-confirm-hint .el-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.warning-icon-inline {
  margin-right: 3px;
  font-size: 13px;
  vertical-align: -1.5px;
  flex-shrink: 0;
}

.warning-type-tag {
  display: inline-flex;
  align-items: center;
  font-weight: 600;
}

.remark-warning-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  color: var(--color-danger, #c9463d) !important;
  font-weight: 550;
}

.remark-warning-text > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warning-invoice-type-text {
  display: inline-flex;
  align-items: center;
  color: var(--color-danger, #c9463d) !important;
  font-weight: 600;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.submit-dialog-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
}

.submit-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.submit-dialog-tip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
  line-height: 1.4;
  user-select: none;
}

.submit-dialog-tip .submit-tip-icon {
  font-size: 13px;
  color: var(--color-warning, #b66d0b);
}

:global(.invoice-preview-dialog .el-dialog__footer .preview-dialog-footer) {
  justify-content: space-between;
  align-items: center;
}

/* 已处理打标控件 */
.preview-processed-check {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.preview-processed-check.is-done :deep(.el-checkbox__label) {
  color: var(--el-color-success);
  font-weight: 500;
}

.preview-processed-check.is-done :deep(.el-checkbox__inner) {
  background-color: var(--el-color-success);
  border-color: var(--el-color-success);
}

/* 已处理小指示符/快捷按钮（桌面表格，按钮右侧） */
.processed-row-indicator-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1.5px dashed #b0bec5;
  background-color: transparent;
  color: #90a4ae;
  font-size: 12px;
  font-weight: bold;
  cursor: pointer;
  padding: 0;
  margin: 0;
  line-height: 1;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.processed-row-indicator-btn:hover {
  border-style: solid;
  border-color: var(--el-color-success);
  color: var(--el-color-success);
  background-color: color-mix(in srgb, var(--el-color-success) 10%, transparent);
  transform: scale(1.1);
}

.processed-row-indicator-btn.is-done {
  border: none;
  background-color: var(--el-color-success);
  color: #fff;
  font-size: 13px;
  box-shadow: 0 1px 4px rgba(46, 125, 50, 0.35);
}

.processed-row-indicator-btn.is-done:hover {
  background-color: color-mix(in srgb, var(--el-color-success) 85%, black);
  transform: scale(1.1);
}

/* 已处理标签（移动端卡片） */
.processed-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}

/* 预览弹窗 */
:global(.invoice-preview-dialog) {
  max-width: 960px;
}

:global(.invoice-preview-dialog .el-dialog__body) {
  padding: 12px 20px 20px;
}

.preview-body {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.preview-image {
  display: block;
  max-width: 100%;
  max-height: 80vh;
  border-radius: 8px;
  object-fit: contain;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: 14px;
}

.preview-loading .el-icon {
  font-size: 28px;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--color-text-muted);
  text-align: center;
}

.preview-error-icon {
  font-size: 40px;
  opacity: 0.5;
}

.preview-error p {
  font-size: 14px;
  margin: 0;
}

@media (max-width: 1180px) {
  .user-work-grid {
    grid-template-columns: 1fr;
  }

  .form-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .records-panel .panel-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 14px 16px;
  }

  .records-panel .panel-actions {
    display: flex;
    flex-direction: column;
    width: 100%;
    gap: 10px;
  }

  .records-panel .filter-control {
    width: 100%;
  }

  .records-panel .search-input {
    width: 100% !important;
  }

  .records-panel .search-button {
    width: 100%;
  }

  .records-panel .date-picker,
  .records-panel .status-select,
  .records-panel .submission-type-select {
    flex: 1;
    width: 100% !important;
  }

  .records-panel .action-buttons-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }

  .records-panel .action-buttons-wrap .result-count {
    flex: 0 0 auto;
    font-size: 11px;
    padding: 4px 8px;
  }

  .records-panel .action-buttons-wrap .submit-action-button,
  .records-panel .action-buttons-wrap .batch-import-button {
    flex: 1;
    min-width: 0;
    padding: 0 8px;
    font-size: 12.5px;
  }

  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: grid;
    gap: 12px;
    padding: 14px;
    background: #f7f9f8;
  }

  .invoice-record-card {
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
    flex-wrap: wrap;
    gap: 8px 10px;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--color-border);
  }

  .card-header-tags {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }

  .mobile-processed-toggle-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 2px 8px;
    height: 24px;
    font-size: 11px;
    font-weight: 600;
    border-radius: 4px;
    border: 1px dashed #b0bec5;
    background: transparent;
    color: #64748b;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .mobile-processed-toggle-btn.is-done {
    border-style: solid;
    border-color: var(--el-color-success, #12715b);
    background: var(--color-primary-soft, #e9f4f0);
    color: var(--el-color-success, #12715b);
  }

  .record-company-copy {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 2px;
  }

  .record-company-copy > span {
    color: var(--color-text-muted);
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 10px;
  }

  .record-card-details {
    display: grid;
    gap: 10px;
    margin: 12px 0;
  }

  .record-card-details > div {
    display: grid;
    grid-template-columns: 72px minmax(0, 1fr);
    align-items: baseline;
    gap: 8px;
  }

  .record-card-details dt {
    color: var(--color-text-muted);
    font-size: 11px;
    font-weight: 600;
  }

  .record-card-details dd {
    min-width: 0;
    margin: 0;
    overflow: hidden;
    color: var(--color-text-secondary);
    font-size: 12.5px;
    text-align: right;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .record-card-details .money-cell {
    color: var(--color-text);
    font-size: 14px;
    font-weight: 650;
  }

  .mobile-record-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .mobile-record-actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    margin: 0;
    padding: 0 6px;
    font-size: 12px;
    height: 34px;
  }

  .record-pending-note {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    color: #8d641d;
    background: #fff8e8;
    border: 1px solid #f2e3bd;
    border-radius: 6px;
    font-size: 11px;
    line-height: 1.4;
  }

  .record-pending-note.is-cancelled {
    color: #64748b;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
  }

  .record-pending-note.is-red-flush-note {
    color: #b45309;
    background: #fffbeb;
    border: 1px solid #fde68a;
  }

  .record-pending-note svg {
    flex: 0 0 auto;
    width: 14px;
    height: 14px;
  }

  .submit-dialog-footer {
    align-items: stretch;
    width: 100%;
  }

  .submit-dialog-actions {
    width: 100%;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .submit-dialog-actions .el-button {
    width: 100%;
    margin-left: 0 !important;
  }

  .submit-dialog-tip {
    width: 100%;
    justify-content: center;
    text-align: center;
  }

  .target-invoice-summary {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 12px 14px;
  }

  :global(.invoice-preview-dialog .el-dialog__footer .preview-dialog-footer) {
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: stretch;
  }

  :global(.invoice-preview-dialog .el-dialog__footer .preview-dialog-footer > .el-button) {
    width: 100%;
    margin-left: 0 !important;
  }
}

.status-tag.is-red-flush-pending {
  color: #b45309 !important;
  background-color: #fffbeb !important;
  border-color: #fde68a !important;
}

.status-tag.is-red-flush-pending .status-dot {
  background-color: #f59e0b !important;
}

.status-tag.is-red-flushed {
  color: #b91c1c !important;
  background-color: #fef2f2 !important;
  border-color: #fecaca !important;
}

.status-tag.is-red-flushed .status-dot {
  background-color: #ef4444 !important;
}

.status-tag.is-red-flush-rejected {
  color: #6b7280 !important;
  background-color: #f3f4f6 !important;
  border-color: #e5e7eb !important;
}

.status-tag.is-red-flush-rejected .status-dot {
  background-color: #9ca3af !important;
}

.red-flush-action-btn {
  margin-left: 4px;
}

.red-flush-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.red-flush-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.target-invoice-summary {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  padding: 14px 16px;
  background: var(--color-surface-muted, #f9fafb);
  border: 1px solid var(--color-border, #e5e7eb);
  border-radius: 8px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.summary-item.rejected-reason-item {
  grid-column: 1 / -1;
  color: #dc2626;
}

.summary-label {
  color: var(--color-text-muted, #6b7280);
}

.quick-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.quick-reason-tag {
  cursor: pointer;
  transition: all 160ms ease;
}

.quick-reason-tag:hover {
  color: var(--color-primary, #059669);
  border-color: var(--color-primary, #059669);
}

.reason-input-title {
  margin-top: 14px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text, #111827);
}

.required-star {
  color: #ef4444;
}

.invoice-category-group {
  display: flex !important;
  flex-direction: column !important;
  align-items: flex-start !important;
  gap: 10px;
  width: 100%;
}

.invoice-category-group :deep(.el-radio),
.invoice-category-group .el-radio {
  display: inline-flex;
  align-items: center;
  height: auto;
  line-height: 1.5;
  margin-right: 0 !important;
  margin-left: 0 !important;
  padding-left: 0 !important;
}

.invoice-category-group :deep(.el-radio__input) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin: 0 !important;
}

.invoice-category-group :deep(.el-radio__label) {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  padding-left: 8px;
}

.category-label {
  font-weight: 600;
  min-width: 68px;
  display: inline-flex;
  align-items: center;
  margin-right: 6px;
  flex-shrink: 0;
}

.vat-special-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.normal-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.category-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}

.category-desc--warning {
  color: #e6821e;
}

.normal-badge-inline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  background: #3b82f6;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  border-radius: 3px;
  vertical-align: middle;
}

.vat-special-badge-inline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  background: #f5a623;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  border-radius: 3px;
  vertical-align: middle;
}

.vat-special-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 6px;
  font-size: 13px;
  color: #875e00;
}

.vat-special-hint .hint-icon {
  color: #f5a623;
  font-size: 16px;
  flex-shrink: 0;
}

.vat-special-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  background: linear-gradient(135deg, #f5a623 0%, #d48806 100%);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  border-radius: 4px;
  margin-right: 6px;
  vertical-align: middle;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(212, 136, 6, 0.35);
  border: 1px solid #d48806;
  letter-spacing: 0.5px;
}

.vat-special-badge .vat-special-ico {
  font-size: 12px;
}

.normal-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  margin-right: 6px;
  vertical-align: middle;
  flex-shrink: 0;
  border: 1px solid #bfdbfe;
  letter-spacing: 0.5px;
}

.normal-badge .normal-ico {
  font-size: 12px;
  color: #2563eb;
}
</style>
