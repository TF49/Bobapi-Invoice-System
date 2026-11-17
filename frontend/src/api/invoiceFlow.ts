import request from '@/utils/request'

export type InvoiceEventType =
  | 'SUBMITTED'
  | 'QUOTA_DEDUCTED'
  | 'ADMIN_UPDATED'
  | 'ISSUED'
  | 'FILE_REPLACED'
  | 'CANCELLED'
  | 'RED_FLUSH_APPLIED'
  | 'RED_FLUSH_CONFIRMED'
  | 'RED_FLUSH_REJECTED'

export interface TaxFlowEvent {
  id: number
  eventType: InvoiceEventType
  operatorType: 'USER' | 'ADMIN' | 'API' | 'SYSTEM'
  quotaDelta: number | null
  balanceAfter: number | null
  quotaTransactionId: number | null
  detail: string | null
  createdAt: string
}

export interface TaxFlowInvoice {
  id: number
  userId: number | null
  username: string | null
  companyName: string
  amount: number
  invoiceType: string
  invoiceCategory: string | null
  sellerCompany: string | null
  taxRate: string | null
  status: 'PENDING' | 'COMPLETED' | 'CANCELLED'
  redFlushStatus: string | null
  submissionType: string | null
  batchId: number | null
  outTradeNo: string | null
  deductedQuota: number | null
  createdAt: string
  events: TaxFlowEvent[]
}

export interface TaxFlowSummary {
  invoiceCount: number
  totalAmount: number
  pendingCount: number
  completedCount: number
  cancelledCount: number
  redFlushedCount: number
  activeDeducted: number
}

export interface TaxFlowResult {
  taxNumber: string
  companyNames: string[]
  summary: TaxFlowSummary
  page: number
  size: number
  total: number
  invoices: TaxFlowInvoice[]
}

export interface TaxNumberSuggestion {
  taxNumber: string
  companyName: string
  invoiceCount: number
}

/**
 * 税号数据流查询范围：
 * - user：普通用户，只能查询本人发票
 * - admin：管理员 / 开票员，可跨用户查询，传 userId 时限定为该用户
 */
export interface TaxFlowScope {
  mode: 'user' | 'admin'
  userId?: number
}

export const TAX_FLOW_MIN_QUERY_LENGTH = 4

function basePath(scope: TaxFlowScope) {
  return scope.mode === 'admin' ? '/invoices/admin/flow' : '/invoices/flow'
}

function scopeParams(scope: TaxFlowScope) {
  return scope.mode === 'admin' && scope.userId != null ? { userId: scope.userId } : {}
}

export const invoiceFlowApi = {
  suggest(scope: TaxFlowScope, q: string) {
    return request.get<any, TaxNumberSuggestion[]>(`${basePath(scope)}/suggest`, {
      params: { q, ...scopeParams(scope) }
    })
  },

  getFlow(scope: TaxFlowScope, taxNumber: string, page = 1, size = 20) {
    return request.get<any, TaxFlowResult>(basePath(scope), {
      params: { taxNumber, page, size, ...scopeParams(scope) }
    })
  }
}
