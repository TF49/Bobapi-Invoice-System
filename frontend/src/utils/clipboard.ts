/**
 * 将文本复制到系统剪贴板（支持现代 API 与 document.execCommand 降级）
 */
export async function copyTextToClipboard(text: string): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch (err: any) {
      // 若因非安全上下文（如 HTTP）或权限拒绝，尝试降级
    }
  }

  // 降级兼容：用于非安全上下文（HTTP）或不支持 Clipboard API 的环境
  if (typeof document !== 'undefined' && typeof document.execCommand === 'function') {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      textarea.style.top = '-9999px'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      const successful = document.execCommand('copy')
      document.body.removeChild(textarea)
      if (successful) {
        return
      }
    } catch {
      // 忽略错误并抛出统一步骤异常
    }
  }

  throw new Error('当前浏览器环境不支持复制到剪贴板')
}

/**
 * 将图片 Blob 复制到系统剪贴板
 */
export async function copyImageToClipboard(blob: Blob): Promise<void> {
  if (typeof navigator === 'undefined' || !navigator.clipboard || typeof ClipboardItem === 'undefined') {
    throw new Error('当前浏览器环境不支持复制图片到剪贴板')
  }

  let pngBlob: Blob = blob

  // 如果不是 image/png 格式，在浏览器环境中转换成 PNG Blob 保证 ClipboardItem 兼容性
  if (blob.type !== 'image/png') {
    try {
      pngBlob = await convertBlobToPng(blob)
    } catch {
      // 如果转换失败（例如非图片），保留原始 blob 尝试写入或抛出异常
      pngBlob = blob
    }
  }

  try {
    await navigator.clipboard.write([
      new ClipboardItem({
        'image/png': pngBlob
      })
    ])
  } catch (err: any) {
    throw new Error(err?.message || '写入剪贴板失败，请检查浏览器权限')
  }
}

function convertBlobToPng(blob: Blob): Promise<Blob> {
  return new Promise((resolve, reject) => {
    if (typeof HTMLCanvasElement === 'undefined' || typeof Image === 'undefined') {
      reject(new Error('环境不支持 Canvas 图片转换'))
      return
    }

    const img = new Image()
    const url = URL.createObjectURL(blob)
    img.onload = () => {
      URL.revokeObjectURL(url)
      const canvas = document.createElement('canvas')
      canvas.width = img.naturalWidth || img.width || 100
      canvas.height = img.naturalHeight || img.height || 100
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('无法创建 Canvas 上下文'))
        return
      }
      ctx.drawImage(img, 0, 0)
      canvas.toBlob((b) => {
        if (b) {
          resolve(b)
        } else {
          reject(new Error('图片转换 PNG 失败'))
        }
      }, 'image/png')
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片加载失败，无法复制'))
    }
    img.src = url
  })
}
