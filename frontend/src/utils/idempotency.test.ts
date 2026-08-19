import { afterEach, describe, expect, it, vi } from 'vitest'
import { generateIdempotencyKey } from './idempotency'

describe('generateIdempotencyKey', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uses randomUUID when it is available', () => {
    vi.stubGlobal('crypto', {
      randomUUID: () => '12345678-1234-1234-1234-123456789012'
    })

    expect(generateIdempotencyKey()).toBe('invoice-12345678-1234-1234-1234-123456789012')
  })

  it('creates a valid key when randomUUID is unavailable', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (values: Uint32Array) => {
        values.set([1, 2, 3, 4])
        return values
      }
    })

    const key = generateIdempotencyKey()

    expect(key).toMatch(/^invoice-[a-z0-9]+-[a-z0-9]+$/)
    expect(key.length).toBeGreaterThanOrEqual(16)
    expect(key.length).toBeLessThanOrEqual(64)
  })
})
