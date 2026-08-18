import { describe, expect, it } from 'vitest'
import type { AxiosResponse } from 'axios'
import { ApiRequestError, normalizeResponse, parseErrorPayload, type ApiEnvelope } from './request'

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

describe('parseErrorPayload', () => {
  it('decodes an API error returned as a blob', async () => {
    const blob = new Blob([
      JSON.stringify({ code: 40402, message: '发票文件不存在', data: null, traceId: 'trace-1' })
    ], { type: 'application/json' })

    await expect(parseErrorPayload(blob)).resolves.toMatchObject({
      code: 40402,
      message: '发票文件不存在',
      traceId: 'trace-1'
    })
  })
})
