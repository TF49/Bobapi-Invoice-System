<template>
  <div class="workspace-shell">
    <AppHeader title="发票管理" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总数</span>
              <strong class="stat-value"><CountUp :value="invoices.length" /></strong>
            </div>
            <p class="stat-note">当前全部申请</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">待处理</span>
              <strong class="stat-value"><CountUp :value="pendingCount" /></strong>
            </div>
            <p class="stat-note">等待上传发票</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">已完成</span>
              <strong class="stat-value"><CountUp :value="completedCount" /></strong>
            </div>
            <p class="stat-note">已交付电子凭证</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总金额</span>
              <strong class="stat-value amount-stat"><CountUp :value="totalAmount" :decimals="2" prefix="¥" /></strong>
            </div>
            <p class="stat-note">全部申请合计</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card stat-card-featured" :class="{ 'stat-card--alert': redFlushPendingCount > 0 }">
          <div class="stat-card-content">
            <span class="stat-icon" :class="redFlushPendingCount > 0 ? 'danger' : 'neutral'"><DocumentDelete /></span>
            <div class="stat-copy">
              <span class="stat-label">待红冲标记</span>
              <strong class="stat-value" :class="{ 'warning-stat': redFlushPendingCount > 0 }">
                <CountUp :value="redFlushPendingCount" />
              </strong>
            </div>
            <p class="stat-note">{{ redFlushPendingCount > 0 ? '需开票员核验冲红' : '暂无红冲申请' }}</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel records-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><List /></span>
            <div>
              <h2>发票申请</h2>
              <p>{{ userStore.role === 'INVOICE_CLERK' ? '处理申请并上传电子发票' : '审核申请并上传电子发票' }}</p>
            </div>
          </div>

          <div class="table-tools">
            <span class="result-count"><i></i>共 {{ filteredInvoices.length }} 条</span>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索公司、税号或申请人"
              :prefix-icon="Search"
              clearable
              class="search-input"
            />
            <div class="filter-control">
              <span class="filter-label"><Filter />状态</span>
              <el-select v-model="statusFilter" aria-label="筛选发票状态" class="status-select">
                <el-option label="全部状态" value="ALL" />
                <el-option label="待开票" value="PENDING" />
                <el-option label="待红冲" value="RED_FLUSH_PENDING" />
                <el-option label="已红冲" value="RED_FLUSH_COMPLETED" />
                <el-option label="已开票 (全部)" value="COMPLETED" />
                <el-option label="已开票 (未处理)" value="COMPLETED_UNPROCESSED" />
                <el-option label="已开票 (已处理)" value="COMPLETED_PROCESSED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </div>
            <div class="filter-control">
              <span class="filter-label"><Connection />方式</span>
              <el-select v-model="submissionTypeFilter" aria-label="筛选提交方式" class="submission-type-select">
                <el-option label="全部方式" value="ALL" />
                <el-option label="API 提交" value="API" />
                <el-option label="手动提交" value="MANUAL" />
                <el-option label="来源未知" value="UNKNOWN" />
              </el-select>
            </div>
            <div class="filter-control">
              <span class="filter-label"><Tickets />票种</span>
              <el-select v-model="categoryFilter" aria-label="筛选发票票种" class="category-select" style="width: 110px">
                <el-option label="全部票种" value="ALL" />
                <el-option label="普票" value="NORMAL" />
                <el-option label="专票" value="VAT_SPECIAL" />
              </el-select>
            </div>
            <div class="filter-control">
              <span class="filter-label"><User />用户</span>
              <el-select
                v-model="userFilter"
                aria-label="筛选申请用户"
                class="user-select"
                clearable
                placeholder="全部用户"
                filterable
              >
                <el-option
                  v-for="u in userOptions"
                  :key="u.value"
                  :label="u.label"
                  :value="u.value"
                />
              </el-select>
            </div>
            <el-tooltip content="刷新列表" placement="top">
              <el-button class="refresh-button" :icon="RefreshRight" :loading="loading" aria-label="刷新列表" @click="loadInvoices" />
            </el-tooltip>

            <div v-if="userStore.role === 'ADMIN'" class="admin-create-actions">
              <el-button type="primary" :icon="Plus" class="submit-action-button" @click="showSubmitDialog">
                提交申请
              </el-button>
              <el-button type="success" plain :icon="UploadFilled" class="batch-action-button" @click="showBatchDialog = true">
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
            <el-table-column prop="companyName" label="公司名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="company-cell">
                  <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                  <strong class="company-name">{{ row.companyName }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="申请用户" min-width="95" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="applicant-user-cell">{{ row.username || `用户#${row.userId}` }}</span>
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
            <el-table-column prop="amount" label="金额" width="115" align="right">
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
                  class="type-tag warning-type-tag"
                >
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  <span>{{ row.invoiceType }}</span>
                </el-tag>
                <el-tag
                  v-else
                  size="small"
                  type="info"
                  effect="plain"
                  class="type-tag"
                >
                  {{ row.invoiceType || '技术服务费' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.remark?.trim()" class="remark-text remark-warning-text">
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  <span>{{ row.remark }}</span>
                </span>
                <span v-else class="remark-text">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="115" align="center">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.status === 'COMPLETED' && row.redFlushStatus === 'PENDING'"
                  :content="`用户申请红冲原因：${row.redFlushReason || '未说明'}`"
                  placement="top"
                >
                  <el-tag class="status-tag" :class="getStatusClass(row)">
                    <i class="status-dot"></i>
                    {{ getStatusLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <el-tooltip
                  v-else-if="row.status === 'COMPLETED' && row.redFlushStatus === 'COMPLETED'"
                  :content="`已红冲作废，额度已退还${row.redFlushRemark ? '（处理备注：' + row.redFlushRemark + '）' : ''}`"
                  placement="top"
                >
                  <el-tag class="status-tag" :class="getStatusClass(row)">
                    <i class="status-dot"></i>
                    {{ getStatusLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <el-tooltip
                  v-else-if="row.status === 'COMPLETED' && row.redFlushStatus === 'REJECTED'"
                  :content="`已驳回红冲：${row.redFlushRemark || '未说明原因'}`"
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
                <div class="action-cell-wrapper">
                  <!-- 1. 待开票状态：快捷粘贴 + 选择文件 + 修改发票 -->
                  <template v-if="row.status === 'PENDING'">
                    <el-tooltip content="点击直接粘贴剪贴板中的图片（亦支持 Ctrl+V）" placement="top">
                      <div
                        :id="`paste-zone-${row.id}`"
                        class="paste-zone"
                        tabindex="0"
                        role="button"
                        :aria-label="`粘贴发票图片`"
                        :class="{ 'paste-zone--active': pasteActiveId === row.id, 'paste-zone--uploading': uploadingId === row.id }"
                        @click="handlePasteButtonClick(row)"
                        @focus="pasteActiveId = row.id"
                        @blur="pasteActiveId = null"
                        @paste="handlePaste($event, row)"
                      >
                        <CopyDocument class="paste-icon" />
                        <span>{{ uploadingId === row.id ? '上传中…' : '粘贴图片' }}</span>
                      </div>
                    </el-tooltip>
                    <el-upload
                      :key="`upload-${row.id}`"
                      :show-file-list="false"
                      :before-upload="createUploadHandler(row)"
                      :disabled="uploadingId !== null"
                      accept=".jpg,.jpeg,.png"
                      class="upload-btn-wrapper"
                    >
                      <el-button type="primary" size="small" :icon="UploadFilled" :loading="uploadingId === row.id" plain class="action-btn">
                        选择
                      </el-button>
                    </el-upload>
                    <el-tooltip content="修改发票信息" placement="top">
                      <el-button
                        :key="`edit-${row.id}`"
                        size="small"
                        :icon="EditPen"
                        class="action-btn icon-only-btn"
                        @click="handleEditInvoice(row)"
                      />
                    </el-tooltip>
                  </template>

                  <!-- 2. 已上传发票场景 -->
                  <template v-else-if="row.downloadable && row.fileExists">
                    <!-- 2.1 待红冲审核场景：重点突出核验与处理 -->
                    <template v-if="row.redFlushStatus === 'PENDING'">
                      <el-button
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        class="action-btn"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-button
                        type="danger"
                        size="small"
                        :icon="Finished"
                        class="action-btn red-flush-btn"
                        @click="handleOpenConfirmRedFlush(row)"
                      >
                        标记红冲
                      </el-button>
                      <el-button
                        size="small"
                        plain
                        :icon="Close"
                        class="action-btn reject-btn"
                        @click="handleOpenRejectRedFlush(row)"
                      >
                        驳回
                      </el-button>
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDropdownCommand(cmd, row)">
                        <el-button size="small" class="action-btn icon-only-btn more-dropdown-btn" aria-label="更多操作">
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

                    <!-- 2.2 正常已开票场景：查看 + 下载 + 复制 + 已处理 + 更多(主动冲红/修改) -->
                    <template v-else-if="row.status === 'COMPLETED' && row.redFlushStatus !== 'COMPLETED'">
                      <el-button
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        class="action-btn"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-button
                        type="primary"
                        plain
                        size="small"
                        :icon="Download"
                        class="action-btn"
                        @click="handleDownload(row)"
                      >
                        下载
                      </el-button>
                      <el-button
                        type="primary"
                        plain
                        size="small"
                        :icon="CopyDocument"
                        :loading="copyingId === row.id"
                        class="action-btn"
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
                        <el-button size="small" class="action-btn icon-only-btn more-dropdown-btn" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="edit" :icon="EditPen">修改信息 / 替换</el-dropdown-item>
                            <el-dropdown-item command="replacePaste" :icon="CopyDocument">重新粘贴发票</el-dropdown-item>
                            <el-dropdown-item command="replaceUpload" :icon="UploadFilled">选择文件替换</el-dropdown-item>
                            <el-dropdown-item command="directRedFlush" :icon="DocumentDelete" divided>主动冲红</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>

                    <!-- 2.3 已红冲作废场景 -->
                    <template v-else-if="row.status === 'COMPLETED' && row.redFlushStatus === 'COMPLETED'">
                      <el-button
                        type="primary"
                        plain
                        size="small"
                        :icon="ZoomIn"
                        :loading="previewingId === row.id"
                        class="action-btn"
                        @click="handlePreview(row)"
                      >
                        查看
                      </el-button>
                      <el-button
                        type="primary"
                        plain
                        size="small"
                        :icon="Download"
                        class="action-btn"
                        @click="handleDownload(row)"
                      >
                        下载
                      </el-button>
                      <el-dropdown trigger="click" @command="(cmd: string) => handleDropdownCommand(cmd, row)">
                        <el-button size="small" class="action-btn icon-only-btn more-dropdown-btn" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="copy" :icon="CopyDocument">复制图片</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </template>
                  </template>

                  <!-- 3. 已取消或其他 -->
                  <span v-else-if="row.status === 'CANCELLED'" class="empty-action"><i class="empty-dot"></i>已取消</span>
                  <span v-else class="empty-action"><i class="empty-dot"></i>暂不可用</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 移动端卡片视图 -->
        <div v-if="filteredInvoices.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in paginatedInvoices" :key="row.id" class="admin-record-card">
            <div class="record-card-header">
              <div class="company-cell">
                <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                <strong class="company-name">{{ row.companyName }}</strong>
              </div>
              <el-tag class="status-tag" :class="getStatusClass(row)">
                <i class="status-dot"></i>
                {{ getStatusLabel(row) }}
              </el-tag>
              <el-tag
                v-if="row.isProcessed"
                class="processed-tag"
                type="success"
                size="small"
                effect="plain"
              >
                <el-icon><Check /></el-icon>
                已处理
              </el-tag>
            </div>

            <dl class="record-card-details">
              <div>
                <dt>申请用户</dt>
                <dd>{{ row.username || `用户#${row.userId}` }}</dd>
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
                <dt>税号</dt>
                <dd class="tax-number-cell">{{ row.taxNumber || '-' }}</dd>
              </div>
              <div>
                <dt>金额</dt>
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
                <dt>用户红冲原因</dt>
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

            <div class="mobile-card-actions">
              <template v-if="row.status === 'PENDING'">
                <div
                  :id="`paste-zone-m-${row.id}`"
                  class="paste-zone mobile-paste"
                  tabindex="0"
                  role="button"
                  @click="handlePasteButtonClick(row)"
                  @paste="handlePaste($event, row)"
                >
                  <CopyDocument class="paste-icon" />
                  <span>粘贴图片</span>
                </div>
                <el-upload
                  :show-file-list="false"
                  :before-upload="createUploadHandler(row)"
                  :disabled="uploadingId !== null"
                  accept=".jpg,.jpeg,.png"
                  class="mobile-upload"
                >
                  <el-button type="primary" :icon="UploadFilled" :loading="uploadingId === row.id">
                    选择文件
                  </el-button>
                </el-upload>
              </template>
              <template v-else-if="row.downloadable && row.fileExists">
                <el-button type="primary" :icon="ZoomIn" :loading="previewingId === row.id" @click="handlePreview(row)">
                  查看发票
                </el-button>
                <el-button type="primary" plain :icon="Download" @click="handleDownload(row)">
                  下载文件
                </el-button>
                <el-button type="primary" plain :icon="CopyDocument" :loading="copyingId === row.id" @click="handleCopyImage(row)">
                  复制图片
                </el-button>
                <template v-if="row.redFlushStatus === 'PENDING'">
                  <el-button type="danger" :icon="Finished" @click="handleOpenConfirmRedFlush(row)">
                    标记红冲
                  </el-button>
                  <el-button plain :icon="Close" @click="handleOpenRejectRedFlush(row)">
                    驳回
                  </el-button>
                </template>
                <template v-else-if="row.status === 'COMPLETED' && row.redFlushStatus !== 'COMPLETED'">
                  <el-button size="small" :icon="CopyDocument" :loading="uploadingId === row.id" @click="handlePasteButtonClick(row)">
                    重新粘贴
                  </el-button>
                  <el-button type="danger" plain :icon="DocumentDelete" @click="handleOpenConfirmRedFlush(row)">
                    主动冲红
                  </el-button>
                </template>
              </template>
              <!-- 移动端修改按钮 -->
              <el-button
                :icon="EditPen"
                :disabled="row.status === 'CANCELLED' || row.redFlushStatus === 'PENDING' || row.redFlushStatus === 'COMPLETED'"
                @click="handleEditInvoice(row)"
              >
                修改信息
              </el-button>
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

    <!-- 发票图片预览弹窗（管理员） -->
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
            v-if="previewingRow?.status === 'COMPLETED' && previewingRow?.redFlushStatus !== 'COMPLETED'"
            type="danger"
            plain
            :icon="Finished"
            @click="handlePreviewRedFlush"
          >
            {{ previewingRow?.redFlushStatus === 'PENDING' ? '标记红冲' : '主动冲红' }}
          </el-button>
          <el-button type="primary" plain :icon="CopyDocument" @click="handleCopyPreviewImage">复制图片</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 修改发票信息弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="修改发票信息"
      width="520px"
      class="edit-invoice-dialog"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="editingRow = null"
    >
      <div v-if="editingRow" class="edit-dialog-meta">
        <span class="edit-meta-label">申请用户：</span>
        <span class="edit-meta-value">{{ editingRow.username || `用户#${editingRow.userId}` }}</span>
        <el-divider direction="vertical" />
        <span class="edit-meta-label">提交方式：</span>
        <el-tag :type="editingRow.submissionType === 'API' ? 'warning' : editingRow.submissionType === 'MANUAL' ? 'info' : 'danger'" size="small">
          {{ editingRow.submissionType === 'API' ? 'API提交' : editingRow.submissionType === 'MANUAL' ? '手动提交' : '来源未知' }}
        </el-tag>
        <el-divider direction="vertical" />
        <span class="edit-meta-label">状态：</span>
        <el-tag class="status-tag" :class="getStatusClass(editingRow.status)" size="small">
          <i class="status-dot"></i>
          {{ getStatusLabel(editingRow.status) }}
        </el-tag>
        <template v-if="editingRow.status === 'COMPLETED'">
          <el-divider direction="vertical" />
          <el-tag type="info" size="small" effect="plain">已开票（修改金额/票种自动多退少补对应用户额度）</el-tag>
        </template>
      </div>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="editForm.companyName" :prefix-icon="OfficeBuilding" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input
            v-model="editForm.taxNumber"
            :prefix-icon="Postcard"
            placeholder="选填，例如：91110108...（个人/无税号可留空）"
            maxlength="100"
            @input="normalizeEditTaxNumber"
          />
        </el-form-item>
        <el-form-item label="开票金额" prop="amount">
          <el-input-number
            v-model="editForm.amount"
            :min="0.01"
            :max="9999999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            class="amount-input"
          />
        </el-form-item>
        <el-form-item label="开票类型" prop="invoiceType">
          <el-select v-model="editForm.invoiceType" placeholder="请选择开票类型" class="type-select">
            <el-option label="技术服务费" value="技术服务费" />
            <el-option label="AI订阅服务费" value="AI订阅服务费" />
            <el-option label="计算服务费" value="计算服务费" />
            <el-option label="研发和技术服务" value="研发和技术服务" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票票种" prop="invoiceCategory">
          <el-radio-group
            v-model="editForm.invoiceCategory"
            class="invoice-category-group"
          >
            <el-radio value="NORMAL">
              <span class="normal-badge" style="margin-right:4px"><el-icon class="normal-ico"><Document /></el-icon><span>普票</span></span>
            </el-radio>
            <el-radio value="VAT_SPECIAL">
              <span class="vat-special-badge" style="margin-right:4px"><el-icon class="vat-special-ico"><Tickets /></el-icon><span>专票</span></span>（额度按 3 倍扣除）
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="editingRow" label="发票图片附件">
          <div class="edit-dialog-file-card">
            <div class="edit-file-info">
              <el-icon class="file-icon"><PictureRounded /></el-icon>
              <div class="file-text">
                <span class="file-title">{{ editingRow.fileName || (editingRow.status === 'COMPLETED' ? '已粘贴图片' : '未上传发票图片') }}</span>
                <small v-if="editingRow.status === 'COMPLETED'" class="file-tip">若发票粘贴错误，可直接粘贴或选择新图片替换</small>
                <small v-else class="file-tip">可在此提前上传或直接粘贴发票图片</small>
              </div>
            </div>
            <div class="edit-file-btns">
              <el-button
                size="small"
                :icon="CopyDocument"
                :loading="uploadingId === editingRow.id"
                @click="handleEditDialogPaste"
              >
                {{ editingRow.status === 'COMPLETED' ? '粘贴替换图片' : '粘贴图片' }}
              </el-button>
              <el-upload
                :show-file-list="false"
                :before-upload="handleEditDialogUpload"
                :disabled="uploadingId !== null"
                accept=".jpg,.jpeg,.png"
                class="upload-inline"
              >
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :icon="UploadFilled"
                  :loading="uploadingId === editingRow.id"
                >
                  {{ editingRow.status === 'COMPLETED' ? '选择新图替换' : '选择图片上传' }}
                </el-button>
              </el-upload>
              <el-button
                v-if="editingRow.downloadable && editingRow.fileExists"
                size="small"
                text
                type="primary"
                :icon="ZoomIn"
                @click="handlePreview(editingRow)"
              >
                预览当前图片
              </el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :icon="Check"
            :loading="editSubmitting"
            @click="handleEditSubmit"
          >
            {{ editSubmitting ? '保存中' : '保存修改' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 确认标记红冲弹窗 -->
    <el-dialog
      v-model="confirmRedFlushVisible"
      :title="targetRedFlushRow?.redFlushStatus === 'PENDING' ? '发票红冲标记确认' : '主动标记发票红冲'"
      width="540px"
      destroy-on-close
      class="red-flush-admin-dialog"
    >
      <div v-if="targetRedFlushRow" class="admin-red-flush-content">
        <el-alert
          type="error"
          :closable="false"
          show-icon
          class="red-flush-alert"
          :title="targetRedFlushRow.redFlushStatus === 'PENDING' ? '红冲标记确认' : '主动冲红作废确认'"
          :description="targetRedFlushRow.invoiceCategory === 'VAT_SPECIAL'
            ? '该发票为【专票】，确认标记红冲后将正式作废，系统将自动按 3 倍额度退还 ¥' + (Number(targetRedFlushRow.amount) * 3).toFixed(2) + ' 至用户额度账户！'
            : (targetRedFlushRow.redFlushStatus === 'PENDING' ? '确认标记红冲后，该张发票将正式作废冲红，系统将自动把该发票金额全额退还至用户的开票额度账户中！' : '该操作将直接作废冲红该张已开票发票，系统将自动把发票金额全额退还至用户的开票额度账户中！')"
        />
        <div class="target-invoice-summary">
          <div class="summary-item">
            <span class="summary-label">申请用户：</span>
            <strong>{{ targetRedFlushRow.username || `用户#${targetRedFlushRow.userId}` }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">发票票种：</span>
            <span v-if="targetRedFlushRow.invoiceCategory === 'VAT_SPECIAL'" class="vat-special-badge" title="增值税专用发票（专票）">
              <el-icon class="vat-special-ico"><Tickets /></el-icon>
              <span>专票 (3倍退额: ¥{{ (Number(targetRedFlushRow.amount) * 3).toFixed(2) }})</span>
            </span>
            <span v-else class="normal-badge" title="增值税普通发票（普票）">
              <el-icon class="normal-ico"><Document /></el-icon>
              <span>普票</span>
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">提交方式：</span>
            <el-tag :type="targetRedFlushRow.submissionType === 'API' ? 'warning' : targetRedFlushRow.submissionType === 'MANUAL' ? 'info' : 'danger'" size="small">
              {{ targetRedFlushRow.submissionType === 'API' ? 'API提交' : targetRedFlushRow.submissionType === 'MANUAL' ? '手动提交' : '来源未知' }}
            </el-tag>
          </div>
          <div class="summary-item">
            <span class="summary-label">公司名称：</span>
            <strong>{{ targetRedFlushRow.companyName }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">企业税号：</span>
            <span>{{ targetRedFlushRow.taxNumber || '-' }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">发票金额：</span>
            <span class="money-cell">{{ formatCurrency(targetRedFlushRow.amount) }}</span>
          </div>
          <div v-if="targetRedFlushRow.redFlushStatus === 'PENDING'" class="summary-item full-width">
            <span class="summary-label">用户红冲原因：</span>
            <span class="user-reason-text">{{ targetRedFlushRow.redFlushReason || '未填写' }}</span>
          </div>
          <div v-if="targetRedFlushRow.redFlushStatus === 'PENDING'" class="summary-item full-width">
            <span class="summary-label">申请时间：</span>
            <span>{{ formatDate(targetRedFlushRow.redFlushApplyTime || targetRedFlushRow.updatedAt) }}</span>
          </div>
        </div>

        <div class="red-flush-form-item">
          <div class="form-item-label">处理备注（选填）</div>
          <el-input
            v-model="confirmRemark"
            type="textarea"
            :rows="3"
            placeholder="可填写红字发票信息、作废冲红单号或处理说明（选填）"
            maxlength="500"
            show-word-limit
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="confirmRedFlushVisible = false">取消</el-button>
          <el-button
            type="danger"
            :icon="Finished"
            :loading="confirmSubmitting"
            @click="handleConfirmRedFlushSubmit"
          >
            确认标记红冲并退还额度
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 驳回红冲申请弹窗 -->
    <el-dialog
      v-model="rejectRedFlushVisible"
      title="驳回发票红冲申请"
      width="500px"
      destroy-on-close
      class="red-flush-admin-dialog"
    >
      <div v-if="targetRedFlushRow" class="admin-red-flush-content">
        <div class="target-invoice-summary">
          <div class="summary-item">
            <span class="summary-label">申请用户：</span>
            <strong>{{ targetRedFlushRow.username || `用户#${targetRedFlushRow.userId}` }}</strong>
          </div>
          <div class="summary-item">
            <span class="summary-label">发票金额：</span>
            <span class="money-cell">{{ formatCurrency(targetRedFlushRow.amount) }}</span>
          </div>
          <div class="summary-item full-width">
            <span class="summary-label">用户红冲原因：</span>
            <span>{{ targetRedFlushRow.redFlushReason || '-' }}</span>
          </div>
        </div>

        <div class="red-flush-form-item">
          <div class="form-item-label">驳回原因 <span class="required-star">*</span></div>
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            placeholder="请填写驳回该红冲申请的具体原因（如：发票已跨期入账无法冲红、已完成报销等）"
            maxlength="500"
            show-word-limit
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="rejectRedFlushVisible = false">取消</el-button>
          <el-button
            type="primary"
            :icon="Close"
            :loading="rejectSubmitting"
            :disabled="!rejectReason.trim()"
            @click="handleRejectRedFlushSubmit"
          >
            确认驳回
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 隐藏的文件选择框，用于操作列快捷触发替换上传 -->
    <input
      ref="replaceFileInputRef"
      type="file"
      style="display: none"
      accept=".jpg,.jpeg,.png"
      @change="handleReplaceFileChange"
    />

    <!-- 批量导入发票弹窗（管理员端） -->
    <InvoiceBatchImportDialog
      v-model="showBatchDialog"
      @success="handleBatchSuccess"
    />

    <!-- 管理员提交发票申请弹窗 -->
    <el-dialog
      v-model="submitDialogVisible"
      title="提交发票申请（管理员）"
      width="540px"
      class="submit-invoice-dialog"
      destroy-on-close
      @closed="resetAiParseState"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="admin-submit-alert"
      >
        <template #title>
          <span>管理员开票将默认扣除系统普通用户 <strong>[user]</strong> 的额度</span>
        </template>
        <template #default>
          <div class="admin-alert-subtext">
            <span>当前 [user] 可用额度：</span>
            <strong class="user-quota-highlight">¥{{ defaultUserQuota != null ? defaultUserQuota.toFixed(2) : '--' }}</strong>
          </div>
        </template>
      </el-alert>

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

          <!-- 操作按钮（空闲时） -->
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

      <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-position="top">
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="submitForm.companyName" :prefix-icon="OfficeBuilding" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input
            v-model="submitForm.taxNumber"
            :prefix-icon="Postcard"
            placeholder="选填，例如：91110108...（个人/无税号可留空）"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="开票金额" prop="amount">
          <el-input-number
            v-model="submitForm.amount"
            :min="0.01"
            :max="9999999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            class="amount-input"
          />
        </el-form-item>
        <el-form-item label="开票数量" prop="count">
          <el-input-number
            v-model="submitForm.count"
            :min="1"
            :max="100"
            :step="1"
            step-strictly
            controls-position="right"
            class="count-input"
          />
          <div class="count-hint">
            <span>针对相同抬头和金额开具多张发票（1 ~ 100 张）</span>
          </div>
        </el-form-item>
        <el-form-item label="开票类型" prop="invoiceType">
          <el-select v-model="submitForm.invoiceType" placeholder="请选择开票类型" class="type-select">
            <el-option label="技术服务费" value="技术服务费" />
            <el-option label="AI订阅服务费" value="AI订阅服务费" />
            <el-option label="计算服务费" value="计算服务费" />
            <el-option label="研发和技术服务" value="研发和技术服务" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票票种" prop="invoiceCategory">
          <el-radio-group v-model="submitForm.invoiceCategory" class="invoice-category-group">
            <el-radio value="NORMAL">
              <span class="normal-badge" style="margin-right:4px"><el-icon class="normal-ico"><Document /></el-icon><span>普票</span></span>
            </el-radio>
            <el-radio value="VAT_SPECIAL">
              <span class="vat-special-badge" style="margin-right:4px"><el-icon class="vat-special-ico"><Tickets /></el-icon><span>专票</span></span>（额度按 3 倍扣除）
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="submitForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="submitDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :icon="Plus"
            :loading="submitSubmitting"
            @click="handleSubmitInvoice"
          >
            {{ submitSubmitting ? '提交中…' : '立即提交' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import {
  Check,
  CircleCheck,
  Clock,
  Close,
  Connection,
  CopyDocument,
  Document,
  DocumentDelete,
  Download,
  EditPen,
  Files,
  Filter,
  Finished,
  List,
  Loading,
  MagicStick,
  MoreFilled,
  OfficeBuilding,
  PictureRounded,
  Plus,
  Postcard,
  RefreshRight,
  Search,
  Tickets,
  UploadFilled,
  User,
  Wallet,
  Warning,
  ZoomIn
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice, type InvoiceRequest, type BatchInvoiceItemRequest } from '@/api/invoice'
import { userApi } from '@/api/user'
import AppHeader from '@/components/AppHeader.vue'
import InvoiceBatchImportDialog from '@/components/InvoiceBatchImportDialog.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { useUserStore } from '@/stores/user'
import { saveBlobResponse } from '@/utils/download'
import { copyImageToClipboard } from '@/utils/clipboard'
import { generateIdempotencyKey } from '@/utils/idempotency'
import { ApiRequestError } from '@/utils/request'

const VALID_STATUSES = ['ALL', 'PENDING', 'COMPLETED', 'COMPLETED_PROCESSED', 'COMPLETED_UNPROCESSED', 'RED_FLUSH_PENDING', 'RED_FLUSH_COMPLETED', 'CANCELLED']
const route = useRoute()

function getInitialStatusFilter(): string {
  try {
    const queryStatus = route?.query?.status
    if (typeof queryStatus === 'string' && VALID_STATUSES.includes(queryStatus)) {
      return queryStatus
    }
    if (typeof window !== 'undefined' && window.location?.search) {
      const urlParams = new URLSearchParams(window.location.search)
      const status = urlParams.get('status')
      if (status && VALID_STATUSES.includes(status)) return status
    }
  } catch {
    // fallback
  }
  return 'ALL'
}

const userStore = useUserStore()
const loading = ref(false)
const uploadingId = ref<number | null>(null)
const copyingId = ref<number | null>(null)
const pasteActiveId = ref<number | null>(null)
const invoices = ref<Invoice[]>([])
const statusFilter = ref(getInitialStatusFilter())
const userFilter = ref('')
const submissionTypeFilter = ref<'ALL' | 'API' | 'MANUAL' | 'UNKNOWN'>('ALL')
const categoryFilter = ref<'ALL' | 'NORMAL' | 'VAT_SPECIAL'>('ALL')
const searchKeyword = ref('')

watch(() => route?.query?.status, (newStatus) => {
  if (typeof newStatus === 'string' && VALID_STATUSES.includes(newStatus)) {
    statusFilter.value = newStatus
  }
})

// 预览状态
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
    ElMessage.success(targetVal ? '已标记为已处理' : '已取消处理标记')
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

const getCompanyInitial = (companyName: string) => companyName.trim().charAt(0) || '企'

const userOptions = computed(() => {
  const seen = new Set<string>()
  const opts: { label: string; value: string }[] = []
  for (const inv of invoices.value) {
    const name = inv.username || `用户#${inv.userId}`
    if (!seen.has(name)) {
      seen.add(name)
      opts.push({ label: name, value: name })
    }
  }
  return opts.sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))
})

const filteredInvoices = computed(() => {
  return invoices.value.filter(invoice => {
    let matchesStatus = false
    if (statusFilter.value === 'ALL') {
      matchesStatus = true
    } else if (statusFilter.value === 'COMPLETED') {
      matchesStatus = invoice.status === 'COMPLETED'
    } else if (statusFilter.value === 'COMPLETED_PROCESSED') {
      matchesStatus = invoice.status === 'COMPLETED' && Boolean(invoice.isProcessed) && invoice.redFlushStatus !== 'COMPLETED'
    } else if (statusFilter.value === 'COMPLETED_UNPROCESSED') {
      matchesStatus = invoice.status === 'COMPLETED' && !invoice.isProcessed && invoice.redFlushStatus !== 'COMPLETED'
    } else if (statusFilter.value === 'RED_FLUSH_PENDING') {
      matchesStatus = invoice.status === 'COMPLETED' && invoice.redFlushStatus === 'PENDING'
    } else if (statusFilter.value === 'RED_FLUSH_COMPLETED') {
      matchesStatus = invoice.status === 'COMPLETED' && invoice.redFlushStatus === 'COMPLETED'
    } else {
      matchesStatus = invoice.status === statusFilter.value
    }
    const invoiceUser = invoice.username || `用户#${invoice.userId}`
    const matchesUser = !userFilter.value || invoiceUser === userFilter.value
    const matchesSubmissionType = submissionTypeFilter.value === 'ALL' ||
      (invoice.submissionType || 'UNKNOWN') === submissionTypeFilter.value
    const matchesCategory = categoryFilter.value === 'ALL' ||
      (invoice.invoiceCategory || 'NORMAL') === categoryFilter.value
    const kw = searchKeyword.value.trim().toLowerCase()
    const matchesKeyword = !kw ||
      (invoice.companyName && invoice.companyName.toLowerCase().includes(kw)) ||
      (invoice.taxNumber && invoice.taxNumber.toLowerCase().includes(kw)) ||
      (invoice.username && invoice.username.toLowerCase().includes(kw)) ||
      (kw === 'api' && invoice.submissionType === 'API') ||
      (kw.includes('手动') && invoice.submissionType === 'MANUAL') ||
      (kw.includes('专票') && invoice.invoiceCategory === 'VAT_SPECIAL') ||
      (kw.includes('普票') && (invoice.invoiceCategory || 'NORMAL') === 'NORMAL')
    return matchesStatus && matchesUser && matchesSubmissionType && matchesCategory && matchesKeyword
  })
})

const page = ref(1)
const pageSize = ref(10)

const paginatedInvoices = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredInvoices.value.slice(start, start + pageSize.value)
})

watch([searchKeyword, statusFilter, userFilter, submissionTypeFilter, categoryFilter], () => {
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

const emptyText = computed(() => {
  if (searchKeyword.value.trim()) return '未找到匹配的发票申请记录'
  if (userFilter.value) return `用户「${userFilter.value}」暂无发票申请`
  if (statusFilter.value !== 'ALL') return '当前状态下暂无发票申请'
  if (submissionTypeFilter.value !== 'ALL') return '当前提交方式下暂无发票申请'
  if (categoryFilter.value !== 'ALL') return '当前发票票种下暂无发票申请'
  return '暂无发票申请'
})

const pendingCount = computed(() => invoices.value.filter(invoice => invoice.status === 'PENDING').length)
const redFlushPendingCount = computed(() => invoices.value.filter(invoice => invoice.status === 'COMPLETED' && invoice.redFlushStatus === 'PENDING').length)
const completedCount = computed(() => invoices.value.filter(invoice => invoice.status === 'COMPLETED' && invoice.redFlushStatus !== 'COMPLETED').length)
const totalAmount = computed(() => invoices.value
  .filter(invoice => invoice.status !== 'CANCELLED' && invoice.redFlushStatus !== 'COMPLETED')
  .reduce((total, invoice) => total + Number(invoice.amount), 0))

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

const loadInvoices = async () => {
  loading.value = true
  try {
    invoices.value = await invoiceApi.getAllInvoices()
    await migrateLegacyProcessedData()
  } catch (error) {
    console.error('加载发票列表失败', error)
  } finally {
    loading.value = false
  }
}

const ALLOWED_MIME = ['image/jpeg', 'image/jpg', 'image/png']
const MIME_TO_EXT: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/png': 'png'
}

const handleUpload = async (row: Invoice, file: File) => {
  if (uploadingId.value !== null || (row.status !== 'PENDING' && row.status !== 'COMPLETED')) return false

  if (row.redFlushStatus === 'PENDING' || row.redFlushStatus === 'COMPLETED') {
    ElMessage.warning('待红冲或已红冲的发票不能上传或替换发票文件')
    return false
  }

  const isValidType = ALLOWED_MIME.includes(file.type)
  const isLt10M = file.size <= 10 * 1024 * 1024

  if (!isValidType) {
    ElMessage.error('只能上传 JPG、JPEG 或 PNG 图片')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }

  const isReplacing = row.status === 'COMPLETED'
  uploadingId.value = row.id
  try {
    await invoiceApi.uploadInvoice(row.id, file)
    ElMessage.success(isReplacing ? '发票文件已成功替换更新' : '上传成功')
    await loadInvoices()
    if (editingRow.value && editingRow.value.id === row.id) {
      const refreshed = invoices.value.find(item => item.id === row.id)
      if (refreshed) {
        editingRow.value = refreshed
      }
    }
  } catch (error) {
    throw error
  } finally {
    uploadingId.value = null
  }
  return false
}

// 快捷替换上传发票文件
const replaceFileInputRef = ref<HTMLInputElement | null>(null)
const targetReplaceRow = ref<Invoice | null>(null)

const triggerReplaceUpload = (row: Invoice) => {
  if (row.redFlushStatus === 'PENDING' || row.redFlushStatus === 'COMPLETED') {
    ElMessage.warning('待红冲或已红冲的发票不能替换文件')
    return
  }
  targetReplaceRow.value = row
  if (replaceFileInputRef.value) {
    replaceFileInputRef.value.value = ''
    replaceFileInputRef.value.click()
  }
}

const handleReplaceFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file && targetReplaceRow.value) {
    await handleUpload(targetReplaceRow.value, file)
  }
}

// 编辑弹窗内的文件替换处理
const handleEditDialogUpload = (file: UploadRawFile) => {
  if (!editingRow.value) return false
  handleUpload(editingRow.value, file)
  return false
}

const handleEditDialogPaste = async () => {
  if (!editingRow.value) return
  await handlePasteButtonClick(editingRow.value)
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

const createUploadHandler = (row: Invoice) => (file: UploadRawFile) => handleUpload(row, file)

// 聚焦粘贴区，并记录当前激活行 ID
const focusPasteZone = (id: number) => {
  pasteActiveId.value = id
  const el = document.getElementById(`paste-zone-${id}`)
  el?.focus()
}

// 点击"粘贴图片"按钮逻辑：直接从系统剪贴板读取图片并上传
const handlePasteButtonClick = async (row: Invoice) => {
  if (uploadingId.value !== null) return

  focusPasteZone(row.id)

  // 检查浏览器是否支持 Clipboard API 并且能读取剪贴板内容
  if (navigator.clipboard && typeof navigator.clipboard.read === 'function') {
    try {
      const items = await navigator.clipboard.read()
      let clipboardImageBlob: Blob | null = null
      let matchedType = ''

      for (const item of items) {
        for (const type of item.types) {
          if (ALLOWED_MIME.includes(type)) {
            clipboardImageBlob = await item.getType(type)
            matchedType = type
            break
          } else if (type.startsWith('image/')) {
            clipboardImageBlob = await item.getType(type)
            matchedType = type
            break
          }
        }
        if (clipboardImageBlob) break
      }

      if (clipboardImageBlob) {
        const ext = MIME_TO_EXT[matchedType] || 'png'
        const imageFile = new File(
          [clipboardImageBlob],
          `clipboard_invoice_${row.id}.${ext}`,
          { type: matchedType || 'image/png' }
        )
        await handleUpload(row, imageFile)
        return
      } else {
        ElMessage.warning('剪贴板中没有可用的 JPG 或 PNG 图片，请先截图或复制图片')
        return
      }
    } catch (error: any) {
      console.warn('自动读取剪贴板失败，回退到快捷键粘贴模式:', error)
      ElMessage.info('未获得剪贴板权限或自动粘贴受限，请按 Ctrl+V 粘贴图片')
      return
    }
  } else {
    ElMessage.info('已聚焦粘贴区域，请按 Ctrl+V 粘贴图片')
  }
}

// 处理键盘 Ctrl+V 粘贴事件
const handlePaste = async (event: ClipboardEvent, row: Invoice) => {
  if (uploadingId.value !== null) return

  const clipboardData = event.clipboardData
  if (!clipboardData) {
    ElMessage.warning('无法读取剪贴板内容，请使用选择文件上传')
    return
  }

  let clipboardImage: File | null = null
  for (const item of Array.from(clipboardData.items)) {
    if (item.type.startsWith('image/') && ALLOWED_MIME.includes(item.type)) {
      const blob = item.getAsFile()
      if (blob) {
        clipboardImage = blob
        break
      }
    }
  }

  if (!clipboardImage) {
    clipboardImage = Array.from(clipboardData.files)
      .find(file => ALLOWED_MIME.includes(file.type)) || null
  }

  if (!clipboardImage) {
    ElMessage.warning('剪贴板中没有可用的 JPG 或 PNG 图片，请先截图或复制图片')
    return
  }

  event.preventDefault()
  const ext = MIME_TO_EXT[clipboardImage.type] || 'png'
  const imageFile = new File(
    [clipboardImage],
    `clipboard_invoice_${row.id}.${ext}`,
    { type: clipboardImage.type }
  )
  await handleUpload(row, imageFile)
}

// 修改发票功能
const editDialogVisible = ref(false)
const editingRow = ref<Invoice | null>(null)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()

const editForm = reactive<InvoiceRequest>({
  companyName: '',
  taxNumber: '',
  amount: 0.01,
  invoiceType: '技术服务费',
  invoiceCategory: 'NORMAL',
  remark: ''
})

const editRules = {
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

const normalizeEditTaxNumber = (value: string) => {
  editForm.taxNumber = value
}

const handleEditInvoice = (row: Invoice) => {
  editingRow.value = row
  editForm.companyName = row.companyName
  editForm.taxNumber = row.taxNumber || ''
  editForm.amount = Number(row.amount)
  editForm.invoiceType = row.invoiceType || '技术服务费'
  editForm.invoiceCategory = row.invoiceCategory || 'NORMAL'
  editForm.remark = row.remark || ''
  editDialogVisible.value = true
}

const handleEditSubmit = async () => {
  if (editSubmitting.value) return
  editSubmitting.value = true
  try {
    const valid = await editFormRef.value?.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请检查并完善发票信息')
      return
    }
    const row = editingRow.value!
    await invoiceApi.updateInvoice(row.id, {
      companyName: editForm.companyName.trim(),
      taxNumber: editForm.taxNumber?.trim() || undefined,
      amount: editForm.amount,
      invoiceType: editForm.invoiceType,
      invoiceCategory: editForm.invoiceCategory || 'NORMAL',
      remark: editForm.remark?.trim() || undefined
    })
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    await loadInvoices()
  } catch (error) {
    console.error('修改发票信息失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error('修改失败，请稍后重试')
    }
  } finally {
    editSubmitting.value = false
  }
}

// 预览功能
const releasePreviewUrl = () => {
  if (previewSrc.value) {
    URL.revokeObjectURL(previewSrc.value)
    previewSrc.value = null
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

// 红冲处理相关状态与方法
const confirmRedFlushVisible = ref(false)
const rejectRedFlushVisible = ref(false)
const targetRedFlushRow = ref<Invoice | null>(null)
const confirmRemark = ref('')
const rejectReason = ref('')
const confirmSubmitting = ref(false)
const rejectSubmitting = ref(false)

const handleOpenConfirmRedFlush = (row: Invoice) => {
  targetRedFlushRow.value = row
  confirmRemark.value = ''
  confirmRedFlushVisible.value = true
}

const handleOpenRejectRedFlush = (row: Invoice) => {
  targetRedFlushRow.value = row
  rejectReason.value = ''
  rejectRedFlushVisible.value = true
}

const handleConfirmRedFlushSubmit = async () => {
  if (!targetRedFlushRow.value) return
  confirmSubmitting.value = true
  const invoiceId = targetRedFlushRow.value.id
  try {
    const updated = await invoiceApi.confirmRedFlush(invoiceId, confirmRemark.value.trim() || undefined)
    confirmRedFlushVisible.value = false
    ElMessage.success('已完成发票红冲标记，额度已自动退还至用户账户')

    // 更新本地行
    const idx = invoices.value.findIndex(i => i.id === invoiceId)
    if (idx !== -1) {
      invoices.value[idx] = updated
    }

    // 广播跨标签页通知
    if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
      try {
        const bc = new BroadcastChannel('bobapi-invoice-events')
        bc.postMessage({ type: 'invoice-red-flush-processed', invoiceId, status: 'COMPLETED' })
        bc.close()
      } catch (_) {}
    }
    window.dispatchEvent(new CustomEvent('invoice-red-flush-processed', { detail: { invoiceId, status: 'COMPLETED' } }))

    await loadInvoices()
  } catch (error: any) {
    console.error('标记红冲失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error(error?.message || '标记红冲失败，请重试')
    }
  } finally {
    confirmSubmitting.value = false
  }
}

const handleRejectRedFlushSubmit = async () => {
  if (!targetRedFlushRow.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因')
    return
  }

  rejectSubmitting.value = true
  const invoiceId = targetRedFlushRow.value.id
  try {
    const updated = await invoiceApi.rejectRedFlush(invoiceId, reason)
    rejectRedFlushVisible.value = false
    ElMessage.success('已驳回该红冲申请')

    // 更新本地行
    const idx = invoices.value.findIndex(i => i.id === invoiceId)
    if (idx !== -1) {
      invoices.value[idx] = updated
    }

    // 广播跨标签页通知
    if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
      try {
        const bc = new BroadcastChannel('bobapi-invoice-events')
        bc.postMessage({ type: 'invoice-red-flush-processed', invoiceId, status: 'REJECTED' })
        bc.close()
      } catch (_) {}
    }
    window.dispatchEvent(new CustomEvent('invoice-red-flush-processed', { detail: { invoiceId, status: 'REJECTED' } }))

    await loadInvoices()
  } catch (error: any) {
    console.error('驳回红冲失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error(error?.message || '驳回操作失败，请重试')
    }
  } finally {
    rejectSubmitting.value = false
  }
}

const handlePreviewRedFlush = () => {
  if (!previewingRow.value) return
  handleOpenConfirmRedFlush(previewingRow.value)
}

const handleDropdownCommand = (command: string, row: Invoice) => {
  if (command === 'download') {
    handleDownload(row)
  } else if (command === 'copy') {
    handleCopyImage(row)
  } else if (command === 'directRedFlush') {
    handleOpenConfirmRedFlush(row)
  } else if (command === 'edit') {
    handleEditInvoice(row)
  } else if (command === 'replacePaste') {
    handlePasteButtonClick(row)
  } else if (command === 'replaceUpload') {
    triggerReplaceUpload(row)
  }
}

const handleRealtimeRefresh = () => {
  loadInvoices()
}

let adminInvoiceBroadcastChannel: BroadcastChannel | null = null

onMounted(() => {
  loadInvoices()

  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      adminInvoiceBroadcastChannel = new BroadcastChannel('bobapi-invoice-events')
      adminInvoiceBroadcastChannel.onmessage = (event) => {
        if (event.data?.type === 'invoice-red-flush-applied' || event.data?.type === 'invoice-red-flush-processed') {
          handleRealtimeRefresh()
        }
      }
    } catch (_) {}
  }

  window.addEventListener('invoice-red-flush-applied', handleRealtimeRefresh)
  window.addEventListener('invoice-red-flush-processed', handleRealtimeRefresh)
})

const onPreviewClose = () => {
  previewRequestId += 1
  previewController?.abort()
  previewController = null
  previewingId.value = null
  releasePreviewUrl()
}

const onPreviewClosed = () => {
  previewError.value = false
  if (!previewVisible.value) {
    previewingRow.value = null
  }
}

onBeforeUnmount(() => {
  onPreviewClose()
  if (adminInvoiceBroadcastChannel) {
    adminInvoiceBroadcastChannel.close()
    adminInvoiceBroadcastChannel = null
  }
  window.removeEventListener('invoice-red-flush-applied', handleRealtimeRefresh)
  window.removeEventListener('invoice-red-flush-processed', handleRealtimeRefresh)
})

// ==================== 管理员提交发票申请功能 ====================
const submitDialogVisible = ref(false)
const showBatchDialog = ref(false)
const defaultUserQuota = ref<number | null>(null)
const submitSubmitting = ref(false)
const submitPendingIdempotencyKey = ref<string | null>(null)
const submitFormRef = ref<FormInstance>()

const submitForm = reactive({
  companyName: '',
  taxNumber: '',
  amount: 100,
  count: 1,
  invoiceType: '技术服务费',
  invoiceCategory: 'NORMAL',
  remark: ''
})

const submitRules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  taxNumber: [
    { max: 100, message: '税号不能超过 100 个字符', trigger: 'blur' }
  ],
  amount: [
    { required: true, message: '请输入开票金额', trigger: 'blur' },
    { type: 'number', min: 0.01, max: 9999999999.99, message: '开票金额必须在有效范围内', trigger: 'change' }
  ],
  count: [
    { required: true, message: '请输入开票数量', trigger: 'blur' },
    { type: 'number', min: 1, max: 100, message: '开票数量必须在 1 ~ 100 之间', trigger: 'change' }
  ],
  invoiceType: [{ required: true, message: '请选择开票类型', trigger: 'change' }]
}

// AI 智能识别自动填单状态
const aiParseExpanded = ref(false)
const aiRawText = ref('')
const aiStep = ref<'idle' | 'extracting' | 'verifying'>('idle')
const aiProgress = ref(0)
let aiParseSessionId = 0
let rafId: number | null = null
let currentAnimationResolver: (() => void) | null = null

const resetAiParseState = () => {
  aiParseSessionId++
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
  if (currentAnimationResolver) { currentAnimationResolver(); currentAnimationResolver = null }
  aiStep.value = 'idle'
  aiProgress.value = 0
  aiRawText.value = ''
  aiParseExpanded.value = false
}

const animateProgress = (from: number, to: number, durationMs: number): Promise<void> => {
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

  const sessionId = aiParseSessionId
  aiStep.value = 'extracting'
  aiProgress.value = 0

  try {
    const [res1] = await Promise.all([
      invoiceApi.parseInvoiceText(text),
      animateProgress(0, 45, 1200)
    ])
    if (aiParseSessionId !== sessionId) return

    if (!res1.companyName && !res1.taxNumber && res1.amount === null) {
      aiStep.value = 'idle'
      aiProgress.value = 0
      ElMessage.warning(res1.hint || '未能从文本中识别出发票相关信息，请手动填写')
      return
    }

    aiStep.value = 'verifying'
    aiProgress.value = 50

    const [res2] = await Promise.all([
      invoiceApi.verifyInvoiceText(text, {
        companyName: res1.companyName,
        taxNumber: res1.taxNumber,
        amount: res1.amount !== null ? Number(res1.amount) : null,
        invoiceType: res1.invoiceType ?? null
      }),
      animateProgress(50, 90, 1200)
    ])
    if (aiParseSessionId !== sessionId) return

    await animateProgress(aiProgress.value, 100, 200)
    if (aiParseSessionId !== sessionId) return

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

    if (companyName) submitForm.companyName = companyName
    if (taxNumber)   submitForm.taxNumber = taxNumber
    if (amount !== null) submitForm.amount = amount
    if (res2.invoiceType) submitForm.invoiceType = res2.invoiceType
    aiParseExpanded.value = false
    ElMessage.success('AI 识别结果已回填')
  } catch (err) {
    aiStep.value = 'idle'
    aiProgress.value = 0
    ElMessage.error('AI 识别服务暂时不可用，请手动填写')
  }
}

const loadDefaultUserQuota = async () => {
  try {
    const res = await userApi.getUsers({ keyword: 'user', page: 1, pageSize: 10 })
    const userItem = res.users.find(u => u.username === 'user')
    if (userItem && userItem.quota) {
      defaultUserQuota.value = userItem.quota.balance
    }
  } catch (e) {
    console.warn('获取默认用户额度失败', e)
  }
}

const showSubmitDialog = () => {
  submitFormRef.value?.resetFields()
  submitForm.companyName = ''
  submitForm.taxNumber = ''
  submitForm.amount = 100
  submitForm.count = 1
  submitForm.invoiceType = '技术服务费'
  submitForm.invoiceCategory = 'NORMAL'
  submitForm.remark = ''
  submitPendingIdempotencyKey.value = null
  resetAiParseState()
  loadDefaultUserQuota()
  submitDialogVisible.value = true
}

const handleSubmitInvoice = async () => {
  if (submitSubmitting.value) return
  submitSubmitting.value = true
  try {
    const valid = await submitFormRef.value?.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请检查并完善申请信息')
      return
    }

    const count = Number(submitForm.count) || 1
    const singleQuota = submitForm.invoiceCategory === 'VAT_SPECIAL' ? submitForm.amount * 3 : submitForm.amount
    const requiredQuota = singleQuota * count
    if (defaultUserQuota.value !== null && requiredQuota > defaultUserQuota.value) {
      ElMessage.error(
        submitForm.invoiceCategory === 'VAT_SPECIAL'
          ? `用户 [user] 额度不足，当前余额 ¥${defaultUserQuota.value.toFixed(2)}，${count > 1 ? `共 ${count} 张专票` : '专票'}按 3 倍扣除需 ¥${requiredQuota.toFixed(2)}`
          : `用户 [user] 额度不足，当前余额 ¥${defaultUserQuota.value.toFixed(2)}，${count > 1 ? `共 ${count} 张发票` : ''}需要 ¥${requiredQuota.toFixed(2)}`
      )
      return
    }

    const idempotencyKey = submitPendingIdempotencyKey.value || generateIdempotencyKey()
    submitPendingIdempotencyKey.value = idempotencyKey

    if (count > 1) {
      const items: BatchInvoiceItemRequest[] = Array.from({ length: count }, (_, idx) => ({
        rowNumber: idx + 1,
        companyName: submitForm.companyName.trim(),
        taxNumber: submitForm.taxNumber?.trim() || undefined,
        amount: submitForm.amount.toFixed(2),
        invoiceType: submitForm.invoiceType,
        invoiceCategory: submitForm.invoiceCategory || 'NORMAL',
        remark: submitForm.remark?.trim() || undefined
      }))
      await invoiceApi.createInvoicesBatch(items, idempotencyKey, true)
      ElMessage.success(`成功以管理员身份提交 ${count} 笔发票申请，已从用户 [user] 扣除额度`)
    } else {
      await invoiceApi.createInvoice({
        companyName: submitForm.companyName.trim(),
        taxNumber: submitForm.taxNumber?.trim() || undefined,
        amount: submitForm.amount,
        invoiceType: submitForm.invoiceType,
        invoiceCategory: submitForm.invoiceCategory || 'NORMAL',
        remark: submitForm.remark?.trim() || undefined
      }, idempotencyKey)
      ElMessage.success('成功以管理员身份提交发票申请，已从用户 [user] 扣除额度')
    }

    submitPendingIdempotencyKey.value = null
    submitDialogVisible.value = false
    await loadInvoices()
    await loadDefaultUserQuota()
  } catch (error) {
    console.error('管理员提交发票申请失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error('提交失败，请稍后重试')
    }
  } finally {
    submitSubmitting.value = false
  }
}

const handleBatchSuccess = async () => {
  ElMessage.success('批量导入成功，额度已从用户 [user] 扣除')
  await loadInvoices()
}
</script>

<style scoped>
.records-panel {
  overflow: hidden;
}

.table-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.result-count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  color: var(--color-text-secondary);
  background: var(--color-primary-soft);
  border: 1px solid #d6e8e2;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.result-count i {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(18, 113, 91, 0.12);
}

.search-input {
  width: 230px;
}

.status-select {
  width: 130px;
}

.submission-type-select {
  width: 130px;
}

.user-select {
  width: 150px;
}

.refresh-button {
  width: 38px;
  height: 38px;
  padding: 0;

}

.records-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
}

.records-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.company-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
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
  color: var(--color-text);
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.applicant-user-cell {
  color: var(--color-primary);
  font-weight: 600;
}

.amount-stat {
  font-size: 22px;
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

.status-dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 14%, transparent);
}

.action-cell-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  white-space: nowrap;
}

.action-cell-wrapper :deep(.el-button),
.action-cell-wrapper .el-button {
  margin-left: 0 !important;
}

.action-cell-wrapper :deep(.action-btn),
.action-cell-wrapper .action-btn {
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: 6px;
}

.action-cell-wrapper :deep(.icon-only-btn),
.action-cell-wrapper .icon-only-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-cell-wrapper :deep(.el-dropdown) {
  display: inline-flex;
  vertical-align: middle;
}

.action-cell-wrapper :deep(.more-dropdown-btn) {
  color: var(--color-text-secondary);
  border-color: var(--color-border);
}

.action-cell-wrapper :deep(.more-dropdown-btn:hover) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--color-primary-soft);
}

.paste-zone {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border: 1.5px dashed var(--color-border-strong);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: #fafcfb;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
  user-select: none;
  white-space: nowrap;
}

.paste-zone:hover,
.paste-zone:focus-visible,
.paste-zone--active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--color-primary-soft);
}

.paste-zone--uploading {
  opacity: 0.6;
  pointer-events: none;
}

.paste-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.upload-btn-wrapper {
  display: inline-flex;
}

.empty-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.empty-dot {
  width: 6px;
  height: 6px;
  background: #aab4b0;
  border-radius: 50%;
}

.mobile-records {
  display: none;
}

@media (max-width: 768px) {
  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 14px;
    background: #f7f9f8;
  }

  .admin-record-card {
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
    margin-bottom: 10px;
    border-bottom: 1px dashed var(--color-border);
    gap: 8px;
  }

  .record-card-details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 12px;
    margin: 0 0 12px 0;
  }

  .record-card-details dt {
    color: var(--color-text-muted);
    font-size: 11px;
    font-weight: 600;
  }

  .record-card-details dd {
    margin: 2px 0 0 0;
    color: var(--color-text);
    font-size: 12.5px;
    font-weight: 550;
    word-break: break-all;
  }

  .mobile-card-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .mobile-card-actions .el-button,
  .mobile-card-actions .mobile-upload {
    width: 100%;
    margin-left: 0 !important;
  }

  .mobile-card-actions .mobile-paste {
    width: 100%;
    height: 36px;
    margin-left: 0 !important;
  }
}

@media (max-width: 720px) {
  .records-panel .panel-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 14px 16px;
  }

  .table-tools {
    display: flex;
    flex-direction: column;
    width: 100%;
    gap: 10px;
  }

  .search-input {
    width: 100%;
  }

  .table-tools .filter-control {
    width: 100%;
  }

  .table-tools .filter-control .status-select,
  .table-tools .filter-control .submission-type-select,
  .table-tools .filter-control .user-select {
    flex: 1;
    width: 100% !important;
  }

  .table-tools .result-count {
    align-self: flex-start;
  }

  .target-invoice-summary {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 12px 14px;
  }

  .dialog-footer {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    width: 100%;
  }

  .dialog-footer .el-button {
    width: 100%;
    margin-left: 0 !important;
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

/* 修改发票弹窗 */
:global(.edit-invoice-dialog .el-dialog__body) {
  padding: 8px 20px 16px;
}

.edit-dialog-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 16px;
  background: var(--color-primary-soft);
  border: 1px solid #d6e8e2;
  border-radius: 8px;
  font-size: 13px;
  flex-wrap: wrap;
}

.edit-meta-label {
  color: var(--color-text-secondary);
  font-weight: 500;
}

.edit-meta-value {
  color: var(--color-primary);
  font-weight: 600;
}

.amount-input {
  width: 100%;
}

.type-select {
  width: 100%;
}

:global(.invoice-preview-dialog .el-dialog__footer .preview-dialog-footer) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

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

.processed-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}

/* 红冲状态与操作样式 */
.stat-card--alert {
  border-color: #fca5a5 !important;
  background: linear-gradient(180deg, #fff 0%, #fff5f5 100%) !important;
}

.stat-icon.danger {
  color: #dc2626 !important;
  background: #fee2e2 !important;
}

.warning-stat {
  color: #dc2626 !important;
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

.red-flush-btn {
  margin-left: 2px;
}

.reject-btn {
  margin-left: 2px;
}

.admin-red-flush-content {
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

.summary-item.full-width {
  grid-column: 1 / -1;
}

.summary-label {
  color: var(--color-text-muted, #6b7280);
}

.user-reason-text {
  color: #d97706;
  font-weight: 600;
}

.red-flush-form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text, #111827);
}

.required-star {
  color: #ef4444;
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

/* 管理员开票操作按钮组 */
.admin-create-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 4px;
}

.admin-submit-alert {
  margin-bottom: 16px;
  border-radius: 8px;
}

.admin-alert-subtext {
  margin-top: 4px;
  font-size: 13px;
  color: #4b5563;
}

.user-quota-highlight {
  color: #059669;
  font-size: 15px;
  font-weight: 700;
  margin-left: 2px;
}

/* 编辑弹窗中的发票文件附件卡片 */
.edit-dialog-file-card {
  width: 100%;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.edit-file-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.edit-file-info .file-icon {
  font-size: 22px;
  color: var(--color-primary, #059669);
}

.edit-file-info .file-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.edit-file-info .file-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edit-file-info .file-tip {
  font-size: 11px;
  color: #6b7280;
  margin-top: 2px;
}

.edit-file-btns {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.upload-inline {
  display: inline-block;
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
  color: #6b7280;
}

.ai-parse-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.ai-progress-container {
  padding: 8px 0 4px;
}

.ai-progress-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 8px;
}

.ai-step-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.ai-step-item.is-active {
  color: #059669;
  font-weight: 600;
}

.ai-step-item.is-done {
  color: #059669;
}

.ai-step-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  background: #e5e7eb;
}

.ai-step-item.is-active .ai-step-dot {
  background: #059669;
  color: #fff;
}

.ai-step-item.is-done .ai-step-dot {
  background: #d1fae5;
  color: #059669;
}

.ai-step-line {
  width: 32px;
  height: 2px;
  background: #e5e7eb;
}

.ai-step-line.is-active {
  background: #059669;
}

.count-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}
</style>

