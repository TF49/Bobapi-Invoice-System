import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { invoiceFlowApi } from './invoiceFlow'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn()
  }
}))

const mockedRequest = vi.mocked(request)

describe('invoiceFlowApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses user-scoped endpoints without a userId for regular users', () => {
    invoiceFlowApi.suggest({ mode: 'user', userId: 9 }, '9111')
    invoiceFlowApi.getFlow({ mode: 'user' }, '91110000ABCD', 2)

    expect(mockedRequest.get).toHaveBeenCalledWith('/invoices/flow/suggest', { params: { q: '9111' } })
    expect(mockedRequest.get).toHaveBeenCalledWith('/invoices/flow', {
      params: { taxNumber: '91110000ABCD', page: 2, size: 20 }
    })
  })

  it('uses admin endpoints and forwards the optional userId', () => {
    invoiceFlowApi.suggest({ mode: 'admin', userId: 7 }, '9111')
    invoiceFlowApi.getFlow({ mode: 'admin' }, '91110000ABCD')

    expect(mockedRequest.get).toHaveBeenCalledWith('/invoices/admin/flow/suggest', {
      params: { q: '9111', userId: 7 }
    })
    expect(mockedRequest.get).toHaveBeenCalledWith('/invoices/admin/flow', {
      params: { taxNumber: '91110000ABCD', page: 1, size: 20 }
    })
  })
})
