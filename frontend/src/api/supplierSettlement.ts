import request from '@/utils/request'

export interface SupplierSettlementRequest {
  amount: number
  remark?: string
}

export interface SupplierSettlementResponse {
  id: number
  settlementAmount: number
  remark: string | null
  operatorName: string
  createdAt: string
}

/**
 * 创建供应商结算记录
 */
export function createSettlement(data: SupplierSettlementRequest) {
  return request.post<any, SupplierSettlementResponse>('/api/admin/supplier-settlement', data)
}

/**
 * 获取供应商结算历史记录
 */
export function getSettlementHistory() {
  return request.get<any, SupplierSettlementResponse[]>('/api/admin/supplier-settlement')
}
