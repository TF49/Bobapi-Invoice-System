import type { InvoiceEventType, TaxFlowEvent, TaxFlowInvoice } from '@/api/invoiceFlow'

export type TaxFlowTone = 'primary' | 'danger' | 'success' | 'warning' | 'info'

export const TAX_FLOW_EVENT_META: Record<InvoiceEventType, { label: string; tone: TaxFlowTone }> = {
  SUBMITTED: { label: '提交申请', tone: 'primary' },
  QUOTA_DEDUCTED: { label: '扣除额度', tone: 'danger' },
  ADMIN_UPDATED: { label: '管理员修改', tone: 'info' },
  ISSUED: { label: '开票完成', tone: 'success' },
  FILE_REPLACED: { label: '替换发票文件', tone: 'info' },
  CANCELLED: { label: '取消申请', tone: 'info' },
  RED_FLUSH_APPLIED: { label: '申请红冲', tone: 'warning' },
  RED_FLUSH_CONFIRMED: { label: '红冲完成', tone: 'warning' },
  RED_FLUSH_REJECTED: { label: '红冲驳回', tone: 'warning' }
}

export const TAX_FLOW_TONE_COLOR: Record<TaxFlowTone, string> = {
  primary: '#12715b',
  danger: '#c9463d',
  success: '#2f9e6f',
  warning: '#b66d0b',
  info: '#74808b'
}

const OPERATOR_LABELS: Record<string, string> = {
  USER: '用户',
  ADMIN: '管理员',
  API: 'OpenAPI',
  SYSTEM: '系统'
}

export function eventMeta(type: string) {
  return TAX_FLOW_EVENT_META[type as InvoiceEventType] ?? { label: type, tone: 'info' as TaxFlowTone }
}

/** 时间线节点颜色：额度变化优先（扣除红色，退还绿色），否则按事件类型 */
export function eventColor(event: Pick<TaxFlowEvent, 'eventType' | 'quotaDelta'>) {
  const delta = Number(event.quotaDelta ?? 0)
  if (event.eventType !== 'QUOTA_DEDUCTED' && delta > 0) return TAX_FLOW_TONE_COLOR.success
  if (delta < 0) return TAX_FLOW_TONE_COLOR.danger
  return TAX_FLOW_TONE_COLOR[eventMeta(event.eventType).tone]
}

export function operatorLabel(type: string | null | undefined) {
  return type ? OPERATOR_LABELS[type] ?? type : '系统'
}

export function invoiceStatusLabel(invoice: Pick<TaxFlowInvoice, 'status' | 'redFlushStatus'>) {
  if (invoice.redFlushStatus === 'COMPLETED') return { label: '已红冲', tone: 'warning' as TaxFlowTone }
  if (invoice.redFlushStatus === 'PENDING') return { label: '待红冲', tone: 'warning' as TaxFlowTone }
  if (invoice.status === 'COMPLETED') return { label: '已开票', tone: 'success' as TaxFlowTone }
  if (invoice.status === 'CANCELLED') return { label: '已取消', tone: 'info' as TaxFlowTone }
  return { label: '待开票', tone: 'primary' as TaxFlowTone }
}

export function submissionLabel(invoice: Pick<TaxFlowInvoice, 'submissionType' | 'batchId'>) {
  const source = invoice.submissionType === 'API' ? 'API' : '网页'
  return invoice.batchId ? `${source}批量` : source
}

export function formatMoney(value: number | string | null | undefined) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2
  }).format(Number(value ?? 0))
}

export function formatSignedMoney(value: number | string | null | undefined) {
  const amount = Number(value ?? 0)
  const formatted = formatMoney(Math.abs(amount))
  if (amount > 0) return `+${formatted}`
  if (amount < 0) return `−${formatted}`
  return formatted
}

export function formatFlowDateTime(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}
