import request from '@/utils/request'
import type { AxiosResponse } from 'axios'

export interface Invoice {
  id: number
  companyName: string
  taxNumber: string
  amount: number
  status: string
  userId: number
  createdAt: string
  updatedAt: string
  downloadable: boolean
  fileName?: string
}

export interface InvoiceRequest {
  companyName: string
  taxNumber: string
  amount: number
}

export const invoiceApi = {
  // 用户创建发票申请
  createInvoice(data: InvoiceRequest, idempotencyKey: string) {
    return request.post<any, Invoice>('/invoices', data, {
      headers: { 'Idempotency-Key': idempotencyKey }
    })
  },
  
  // 用户查看自己的发票列表
  getMyInvoices() {
    return request.get<any, Invoice[]>('/invoices/my')
  },
  
  // 管理员查看所有发票
  getAllInvoices() {
    return request.get<any, Invoice[]>('/invoices/admin/all')
  },
  
  // 管理员上传发票文件
  uploadInvoice(id: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<any, Invoice>(`/invoices/admin/${id}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  // 下载发票文件
  downloadInvoice(id: number) {
    return request.get<Blob, AxiosResponse<Blob>>(`/invoices/${id}/download`, { responseType: 'blob' })
  }
}
