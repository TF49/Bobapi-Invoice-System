import request from '@/utils/request'

export type SettlementSellerCompany = 'DEFAULT' | 'TECH'
export type SettlementInvoiceCategory = 'NORMAL' | 'VAT_SPECIAL'

export interface SupplierSettlementRequest {
  amount: number
  /** 结算通道-出票主体 */
  sellerCompany: SettlementSellerCompany
  /** 结算通道-票种 */
  invoiceCategory: SettlementInvoiceCategory
  remark?: string
}

/** 结算通道展示名，例如「普通公司·普票」 */
export function settlementChannelLabel(sellerCompany?: string | null, invoiceCategory?: string | null): string {
  if (!sellerCompany || !invoiceCategory) return '-'
  const company = sellerCompany === 'TECH' ? '科技公司' : '普通公司'
  const category = invoiceCategory === 'VAT_SPECIAL' ? '专票' : '普票'
  return `${company}·${category}`
}

export interface SupplierSettlementResponse {
  id: number
  settlementAmount: number
  sellerCompany: SettlementSellerCompany
  invoiceCategory: SettlementInvoiceCategory
  remark: string | null
  operatorName: string
  createdAt: string
  /** 是否已作废；作废记录不计入已结算汇总 */
  voided: boolean
  voidedAt: string | null
  voidedByName: string | null
  voidReason: string | null
}

/**
 * 创建供应商结算记录
 */
export function createSettlement(data: SupplierSettlementRequest, idempotencyKey?: string) {
  return request.post<any, SupplierSettlementResponse>('/admin/supplier-settlement', data, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
  })
}

/**
 * 获取供应商结算历史记录
 */
export function getSettlementHistory() {
  return request.get<any, SupplierSettlementResponse[]>('/admin/supplier-settlement')
}

/**
 * 作废供应商结算记录（选错通道 / 填错金额时纠错，作废后重新登记）
 */
export function voidSettlement(id: number, reason: string) {
  return request.post<any, SupplierSettlementResponse>(`/admin/supplier-settlement/${id}/void`, { reason })
}
