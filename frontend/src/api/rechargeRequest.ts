import request from '@/utils/request'

export interface RechargeRequest {
  id: number
  userId: number
  username?: string
  amount: number
  screenshotUrl?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  remark?: string
  adminRemark?: string
  reviewedBy?: number
  reviewedByName?: string
  reviewedAt?: string
  createdAt: string
  updatedAt: string
}

export interface CreateRechargeRequest {
  amount: number
  screenshotUrl?: string
  remark?: string
}

export interface ReviewRechargeRequest {
  status: 'APPROVED' | 'REJECTED'
  adminRemark?: string
}

export const rechargeRequestApi = {
  // 用户端：创建充值申请
  createRequest(data: CreateRechargeRequest) {
    return request.post<any, RechargeRequest>('/recharge-requests', data)
  },

  // 用户端：获取自己的充值申请列表
  getMyRequests() {
    return request.get<any, RechargeRequest[]>('/recharge-requests/my')
  },

  // 管理员：获取待审核的申请列表
  getPendingRequests() {
    return request.get<any, RechargeRequest[]>('/recharge-requests/admin/pending')
  },

  // 管理员：获取所有申请列表
  getAllRequests(status?: string) {
    return request.get<any, RechargeRequest[]>('/recharge-requests/admin/all', {
      params: { status }
    })
  },

  // 管理员：审核充值申请
  reviewRequest(id: number, data: ReviewRechargeRequest) {
    return request.put<any, void>(`/recharge-requests/admin/${id}/review`, data)
  },

  // 用户端：上传充值截图
  uploadScreenshot(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, string>('/recharge-requests/upload-screenshot', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 管理员：获取待审核申请数量
  getPendingCount() {
    return request.get<any, number>('/recharge-requests/admin/pending-count')
  }
}