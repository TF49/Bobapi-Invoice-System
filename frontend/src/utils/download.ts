import type { AxiosResponse } from 'axios'

export function getDownloadFilename(contentDisposition: string | undefined, fallback: string): string {
  if (!contentDisposition) {
    return fallback
  }

  const encodedMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1].trim())
    } catch {
      return fallback
    }
  }

  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1]?.trim() || fallback
}

export function saveBlobResponse(response: AxiosResponse<Blob>, fallbackName: string): void {
  const fileName = getDownloadFilename(response.headers['content-disposition'], fallbackName)
  const url = window.URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.URL.revokeObjectURL(url)
}
