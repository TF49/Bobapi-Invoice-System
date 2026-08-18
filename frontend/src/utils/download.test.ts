import { describe, expect, it } from 'vitest'
import { getDownloadFilename } from './download'

describe('getDownloadFilename', () => {
  it('decodes an RFC 5987 UTF-8 filename', () => {
    expect(getDownloadFilename(
      "attachment; filename*=UTF-8''%E5%8F%91%E7%A5%A8.pdf",
      'fallback.pdf'
    )).toBe('发票.pdf')
  })

  it('uses a plain filename when present', () => {
    expect(getDownloadFilename('attachment; filename="invoice.pdf"', 'fallback.pdf'))
      .toBe('invoice.pdf')
  })

  it('falls back when the header is missing', () => {
    expect(getDownloadFilename(undefined, 'fallback.pdf')).toBe('fallback.pdf')
  })
})
