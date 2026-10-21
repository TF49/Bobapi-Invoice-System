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

export interface InvoiceTypeStat {
  invoiceType: string
  count: number
  amount: number
}

export interface CompanyStat {
  companyName: string
  count: number
  amount: number
}

export interface AmountRangeStat {
  rangeLabel: string
  minAmount: number
  maxAmount: number | null
  count: number
  amount: number
}

export interface HourStat {
  hour: number
  count: number
}

export interface DailyTrendStat {
  date: string
  completedCount: number
  completedAmount: number
  createdCount: number
  pendingCount?: number
  cancelledCount?: number
}

export interface QuotaPoolStat {
  totalBalance: number
  totalRecharged: number
  totalDeducted: number
  pendingRechargeCount: number
}

export interface ChannelStat {
  channel: string
  count: number
  amount: number
}

export interface CategoryStat {
  category: string
  count: number
  amount: number
}

export interface RedFlushStat {
  redFlushedCount: number
  redFlushedAmount: number
  pendingRedFlushCount: number
  redFlushRate: number
}

export interface FulfillmentStat {
  under30Min: number
  under2Hour: number
  under6Hour: number
  under24Hour: number
  over24Hour: number
  avgMinutes: number
  fastCompletionRate: number
}

export interface DailyFundFlow {
  date: string
  rechargeAmount: number
  deductAmount: number
  netAmount: number
}

export interface FundFlowStat {
  dailyFlows: DailyFundFlow[]
  approvedRecharges: number
  rejectedRecharges: number
  pendingRecharges: number
  approvedRechargeAmount: number
  approvalRate: number
}

export interface SettlementTrendStat {
  month: string
  invoicedAmount: number
  settledAmount: number
  unsettledBalance: number
}

export interface DashboardStats {
  totalInvoices: number
  pendingInvoices: number
  completedInvoices: number
  totalAmount: number
  pendingAmount?: number
  totalSettledAmount?: number
  unsettledAmount?: number
  userStats: UserInvoiceStats[]
  typeStats?: InvoiceTypeStat[]
  companyTopStats?: CompanyStat[]
  amountRangeStats?: AmountRangeStat[]
  hourDistribution?: HourStat[]
  dailyTrend?: DailyTrendStat[]
  quotaPoolStats?: QuotaPoolStat
  channelStats?: ChannelStat[]
  categoryStats?: CategoryStat[]
  redFlushStats?: RedFlushStat
  fulfillmentStats?: FulfillmentStat
  fundFlowStats?: FundFlowStat
  settlementTrends?: SettlementTrendStat[]
}

export const dashboardApi = {
  getStats() {
    return request.get<any, DashboardStats>('/invoices/admin/dashboard')
  }
}
