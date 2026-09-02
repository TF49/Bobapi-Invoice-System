import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { rechargeRequestApi } from './rechargeRequest'

vi.mock('@/utils/request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn()
  }
}))

const mockedRequest = vi.mocked(request)

describe('rechargeRequestApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends the idempotency key when creating a recharge request', () => {
    const payload = {
      feeAmount: 300,
      screenshotUrl: '/recharge-requests/screenshot/proof.png',
      remark: 'test'
    }
    const key = 'recharge-1234567890123456'

    rechargeRequestApi.createRequest(payload, key)

    expect(mockedRequest.post).toHaveBeenCalledWith(
      '/recharge-requests',
      payload,
      { headers: { 'Idempotency-Key': key } }
    )
  })
})
