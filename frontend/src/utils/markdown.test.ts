// @vitest-environment jsdom
// 注意：DOMPurify 在 happy-dom 下不会遍历子节点（无法真正过滤），XSS 用例必须跑在 jsdom 中。

import { describe, expect, it } from 'vitest'
import { markdownExcerpt, renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('renders common markdown structures', () => {
    const html = renderMarkdown('## 更新\n- 支持**批量导入**\n- 第二项')

    expect(html).toContain('<h2')
    expect(html).toContain('<li>支持<strong>批量导入</strong></li>')
  })

  it('keeps single line breaks', () => {
    expect(renderMarkdown('第一行\n第二行')).toContain('<br>')
  })

  it('strips scripts, inline event handlers and javascript: links', () => {
    const html = renderMarkdown(
      '<script>alert(1)</script>\n\n<img src="x" onerror="alert(2)">\n\n[点我](javascript:alert(3))\n\n<iframe src="https://evil.example"></iframe>'
    )

    expect(html).not.toContain('<script')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('javascript:')
    expect(html).not.toContain('<iframe')
  })

  it('opens links in a new tab without leaking the opener', () => {
    const html = renderMarkdown('[文档](https://example.com/docs)')

    expect(html).toContain('href="https://example.com/docs"')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })

  it('returns an empty string for empty input', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
  })
})

describe('markdownExcerpt', () => {
  it('strips markdown syntax and html into a single line', () => {
    expect(markdownExcerpt('## 本次更新\n- 支持 **批量导入**\n> 详见 [文档](https://e.com)<br>'))
      .toBe('本次更新 支持 批量导入 详见 文档')
  })

  it('truncates long text with an ellipsis', () => {
    expect(markdownExcerpt('一二三四五六七八九十', 4)).toBe('一二三四…')
    expect(markdownExcerpt('')).toBe('')
  })
})
