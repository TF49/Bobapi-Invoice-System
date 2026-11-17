import { describe, expect, it } from 'vitest'
import {
  eventColor,
  eventMeta,
  formatSignedMoney,
  invoiceStatusLabel,
  operatorLabel,
  submissionLabel,
  TAX_FLOW_TONE_COLOR
} from './taxFlow'

describe('taxFlow utils', () => {
  it('labels known events and falls back for unknown types', () => {
    expect(eventMeta('RED_FLUSH_CONFIRMED').label).toBe('红冲完成')
    expect(eventMeta('SOMETHING_NEW').label).toBe('SOMETHING_NEW')
  })

  it('colors deductions red and refunds green', () => {
    expect(eventColor({ eventType: 'QUOTA_DEDUCTED', quotaDelta: -100 })).toBe(TAX_FLOW_TONE_COLOR.danger)
    expect(eventColor({ eventType: 'RED_FLUSH_CONFIRMED', quotaDelta: 100 })).toBe(TAX_FLOW_TONE_COLOR.success)
    expect(eventColor({ eventType: 'RED_FLUSH_APPLIED', quotaDelta: null })).toBe(TAX_FLOW_TONE_COLOR.warning)
  })

  it('formats signed amounts', () => {
    expect(formatSignedMoney(-640)).toBe('−¥640.00')
    expect(formatSignedMoney(30)).toBe('+¥30.00')
  })

  it('derives invoice status with red flush taking precedence', () => {
    expect(invoiceStatusLabel({ status: 'COMPLETED', redFlushStatus: 'COMPLETED' }).label).toBe('已红冲')
    expect(invoiceStatusLabel({ status: 'COMPLETED', redFlushStatus: 'REJECTED' }).label).toBe('已开票')
    expect(invoiceStatusLabel({ status: 'PENDING', redFlushStatus: null }).label).toBe('待开票')
  })

  it('labels operators and submission sources', () => {
    expect(operatorLabel('API')).toBe('OpenAPI')
    expect(operatorLabel(null)).toBe('系统')
    expect(submissionLabel({ submissionType: 'API', batchId: 3 })).toBe('API批量')
    expect(submissionLabel({ submissionType: 'MANUAL', batchId: null })).toBe('网页')
  })
})
