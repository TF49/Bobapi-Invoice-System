/**
 * 统一处理图片/截图 URL，确保在开发环境代理和生产环境下均能正常加载
 */
export function getImageUrl(url?: string | null): string {
  if (!url || typeof url !== 'string') return ''
  const trimmed = url.trim()
  if (!trimmed) return ''

  // 外部链接、Blob 临时 URL 或 Base64 Data URL 直接返回
  if (
    trimmed.startsWith('http://') ||
    trimmed.startsWith('https://') ||
    trimmed.startsWith('blob:') ||
    trimmed.startsWith('data:')
  ) {
    return trimmed
  }

  // 相对接口路径补全 /api
  if (trimmed.startsWith('/api/')) {
    return trimmed
  }
  if (trimmed.startsWith('/')) {
    return `/api${trimmed}`
  }
  return `/api/${trimmed}`
}
