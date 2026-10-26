import request from '@/utils/request'
import type { AxiosResponse } from 'axios'

export interface SealDocument {
  id: number
  userId: number
  username?: string
  sourceFileName: string
  sourceFileSize: number
  sourceFileExt?: string
  sealedFileName?: string
  sealedFileSize?: number
  feeAmount: number
  status: 'PENDING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'
  rejectReason?: string
  operatorId?: number
  operatorName?: string
  isProcessed: boolean
  completedAt?: string
  createdAt: string
  updatedAt: string
}

export interface UserSealQuota {
  id: number
  userId: number
  username?: string
  balance: number
  totalRecharged: number
  totalDeducted: number
  availableCount: number
  updatedAt: string
}

export const sealDocumentApi = {
  // 获取当前用户盖章钱包额度
  getMySealQuota() {
    return request.get<any, UserSealQuota>('/documents/quota')
  },

  // 用户直接上传待盖章文件（极简无表单，扣除10元）
  uploadDocument(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, SealDocument>('/documents/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 用户获取我的文件列表
  getMyDocuments(status?: string) {
    return request.get<any, SealDocument[]>('/documents/my', {
      params: { status }
    })
  },

  // 用户撤销盖章申请（自动退款10元）
  cancelDocument(id: number) {
    return request.post<any, SealDocument>(`/documents/${id}/cancel`)
  },

  // 标记处理状态
  updateProcessed(id: number, isProcessed: boolean) {
    return request.put<any, void>(`/documents/${id}/processed`, null, {
      params: { isProcessed }
    })
  },

  // 管理员/开票员：获取所有文件记录
  getAllDocuments(status?: string, username?: string) {
    return request.get<any, SealDocument[]>('/documents/admin/all', {
      params: { status, username }
    })
  },

  // 管理员/开票员：获取待盖章数量（角标）
  getPendingCount() {
    return request.get<any, number>('/documents/admin/pending-count')
  },

  // 管理员/开票员：上传盖章后交付文件
  uploadSealedDocument(id: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, SealDocument>(`/documents/admin/${id}/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 管理员/开票员：驳回盖章文件（自动退费10元）
  rejectDocument(id: number, rejectReason: string) {
    return request.post<any, SealDocument>(`/documents/admin/${id}/reject`, {
      rejectReason
    })
  },

  // 管理员：调整用户盖章额度
  adjustSealQuota(userId: number, amount: number, remark?: string) {
    return request.post<any, any>('/documents/admin/quota/adjust', {
      amount,
      remark
    }, {
      params: { userId }
    })
  },

  // 安全下载原待盖章文件（带 Token 鉴权与 Blob 转换）
  downloadSourceFile(id: number) {
    return request.get<Blob, AxiosResponse<Blob>>(`/documents/${id}/download-source`, {
      responseType: 'blob'
    })
  },

  // 安全下载盖章后交付成品（带 Token 鉴权与 Blob 转换）
  downloadSealedFile(id: number) {
    return request.get<Blob, AxiosResponse<Blob>>(`/documents/${id}/download`, {
      responseType: 'blob'
    })
  },

  // 安全获取交付文件预览 Blob（用于新窗口展示或弹窗预览）
  previewSealedFile(id: number) {
    return request.get<Blob, AxiosResponse<Blob>>(`/documents/${id}/preview`, {
      responseType: 'blob'
    })
  }
}
