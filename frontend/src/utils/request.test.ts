import { describe, expect, it } from 'vitest'
import type { AxiosResponse } from 'axios'
import { ApiRequestError, normalizeResponse, type ApiEnvelope } from './request'

function response<T>(data: ApiEnvelope<T> | Blob, responseType?: string): AxiosResponse<ApiEnvelope<T> | Blob> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: {}, responseType } as AxiosResponse['config']
  }
}

describe('normalizeResponse', () => {
  it('unwraps successful API data', () => {
    const result = normalizeResponse(response({ code: 200, message: 'success', data: { id: 1 } }))
    expect(result).toEqual({ id: 1 })
  })

  it('rejects application-level errors', () => {
    expect(() => normalizeResponse(response({ code: 40902, message: '幂等键冲突', data: null })))
      .toThrow(ApiRequestError)
  })

  it('keeps the complete Axios response for blob downloads', () => {
    const blob = new Blob(['invoice'], { type: 'application/pdf' })
    const axiosResponse = response(blob, 'blob')
    expect(normalizeResponse(axiosResponse)).toBe(axiosResponse)
  })
})
