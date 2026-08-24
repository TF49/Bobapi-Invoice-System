// @vitest-environment happy-dom

import { describe, expect, it, vi } from 'vitest'
import { copyImageToClipboard } from './clipboard'

describe('copyImageToClipboard', () => {
  it('throws error when navigator.clipboard or ClipboardItem is not supported', async () => {
    const originalClipboard = navigator.clipboard
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true
    })

    const blob = new Blob(['test'], { type: 'image/png' })
    await expect(copyImageToClipboard(blob)).rejects.toThrow('当前浏览器环境不支持复制图片到剪贴板')

    // Restore
    Object.defineProperty(navigator, 'clipboard', {
      value: originalClipboard,
      configurable: true
    })
  })

  it('calls navigator.clipboard.write when supported', async () => {
    const mockWrite = vi.fn().mockResolvedValue(undefined)
    
    // Mock ClipboardItem
    class MockClipboardItem {
      types: string[]
      constructor(public items: Record<string, Blob>) {
        this.types = Object.keys(items)
      }
    }

    Object.defineProperty(window, 'ClipboardItem', {
      value: MockClipboardItem,
      configurable: true,
      writable: true
    })

    Object.defineProperty(navigator, 'clipboard', {
      value: { write: mockWrite },
      configurable: true,
      writable: true
    })

    const blob = new Blob(['png data'], { type: 'image/png' })
    await copyImageToClipboard(blob)

    expect(mockWrite).toHaveBeenCalledTimes(1)
  })
})
