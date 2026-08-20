import request from '@/utils/request'

export interface UserQuota {
  userId: number
  balance: number
  totalRecharged: number
  totalDeducted: number
}

export interface QuotaTransaction {
  id: number
  userId: number
  transactionType: 'RECHARGE' | 'DEDUCT' | 'ADJUST'
  amount: number
  balanceBefore: number
  balanceAfter: number
  operatorId?: number
  operatorType?: 'ADMIN' | 'SYSTEM'
  invoiceId?: number
  remark?: string
  createdAt: string
}

export interface RechargeQuotaRequest {
  amount: number
  remark?: string
}

export interface AdjustQuotaRequest {
  amount: number
  remark?: string
}

export const quotaApi = {
  // 用户端：获取自己的额度
  getMyQuota() {
    return request.get<any, UserQuota>('/users/quota')
  },

  // 用户端：获取自己的额度变更历史
  getMyTransactions(transactionType?: string) {
    return request.get<any, QuotaTransaction[]>('/users/quota/transactions', {
      params: { transactionType }
    })
  },

  // 管理员：获取用户额度
  getUserQuota(userId: number) {
    return request.get<any, UserQuota>(`/users/admin/${userId}/quota`)
  },

  // 管理员：获取用户额度变更历史
  getUserTransactions(userId: number, transactionType?: string) {
    return request.get<any, QuotaTransaction[]>(`/users/admin/${userId}/quota/transactions`, {
      params: { transactionType }
    })
  },

  // 管理员：充值额度
  rechargeQuota(userId: number, data: RechargeQuotaRequest) {
    return request.post<any, UserQuota>(`/users/admin/${userId}/quota/recharge`, data)
  },

  // 管理员：调整额度
  adjustQuota(userId: number, data: AdjustQuotaRequest) {
    return request.put<any, UserQuota>(`/users/admin/${userId}/quota/adjust`, data)
  }
}