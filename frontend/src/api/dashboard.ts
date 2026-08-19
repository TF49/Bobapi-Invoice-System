import request from '@/utils/request'

export interface TimelineData {
  date: string
  count: number
  amount: number
}

export interface UserInvoiceStats {
  userId: number
  username: string
  completedCount: number
  pendingCount: number
  totalAmount: number
  timeline: TimelineData[]
}

export interface DashboardStats {
  totalInvoices: number
  pendingInvoices: number
  completedInvoices: number
  totalAmount: number
  userStats: UserInvoiceStats[]
}

export const dashboardApi = {
  getStats() {
    return request.get<any, DashboardStats>('/invoices/admin/dashboard')
  }
}