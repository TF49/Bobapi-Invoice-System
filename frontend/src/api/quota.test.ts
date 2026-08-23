import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { quotaApi } from './quota'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn()
  }
}))

const mockedRequest = vi.mocked(request)

describe('quotaApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends an idempotency key for recharge and adjustment writes', () => {
    const key = 'quota-1234567890123456'
    const recharge = { amount: 100, remark: 'monthly' }
    const adjustment = { amount: -20, remark: 'correction' }

    quotaApi.rechargeQuota(2, recharge, key)
    quotaApi.adjustQuota(2, adjustment, key)

    expect(mockedRequest.post).toHaveBeenCalledWith(
      '/users/admin/2/quota/recharge',
      recharge,
      { headers: { 'Idempotency-Key': key } }
    )
    expect(mockedRequest.put).toHaveBeenCalledWith(
      '/users/admin/2/quota/adjust',
      adjustment,
      { headers: { 'Idempotency-Key': key } }
    )
  })
})
