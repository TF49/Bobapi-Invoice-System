import { Marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 公告 Markdown 渲染：marked 解析后统一经过 DOMPurify 过滤，防止 XSS。
 * 项目中所有 v-html 公告内容都必须通过此函数产出。
 */
const markdown = new Marked({ gfm: true, breaks: true, async: false })

let hookInstalled = false

function ensureLinkHook() {
  if (hookInstalled) return
  hookInstalled = true
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node instanceof Element && node.tagName === 'A' && node.hasAttribute('href')) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
  })
}

export function renderMarkdown(source: string | null | undefined): string {
  if (!source) return ''
  ensureLinkHook()
  const html = markdown.parse(source) as string
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'form', 'input', 'button', 'textarea', 'select', 'iframe'],
    ADD_ATTR: ['target']
  })
}

/**
 * 生成纯文本摘要（用于列表展示）：去掉 Markdown 标记与 HTML 标签，合并空白后截断
 */
export function markdownExcerpt(source: string | null | undefined, maxLength = 60): string {
  if (!source) return ''
  const text = source
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/<[^>]*>/g, ' ')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}(#{1,6}|>|[-*+]|\d+\.)\s+/gm, '')
    .replace(/^\s*([-*_]\s*){3,}$/gm, ' ')
    .replace(/[*_~`|]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}
