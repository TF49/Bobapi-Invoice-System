const fallbackRandomPart = () => {
  const cryptoApi = globalThis.crypto

  if (typeof cryptoApi?.getRandomValues === 'function') {
    const values = new Uint32Array(4)
    cryptoApi.getRandomValues(values)
    return Array.from(values, value => value.toString(36).padStart(7, '0')).join('')
  }

  return `${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`
}

/**
 * 生成符合后端格式要求的幂等键。
 * randomUUID 在局域网 HTTP 等非安全上下文中可能不可用，因此必须保留兼容路径。
 */
export const generateIdempotencyKey = (prefix = 'invoice') => {
  const cryptoApi = globalThis.crypto

  if (typeof cryptoApi?.randomUUID === 'function') {
    try {
      return `${prefix}-${cryptoApi.randomUUID()}`
    } catch {
      // 某些浏览器暴露了方法，但会在非安全上下文调用时抛错。
    }
  }

  return `${prefix}-${Date.now().toString(36)}-${fallbackRandomPart()}`.slice(0, 64)
}
