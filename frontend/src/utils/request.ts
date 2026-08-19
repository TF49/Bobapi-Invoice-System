import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { AxiosInstance, AxiosResponse } from 'axios'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export class ApiRequestError extends Error {
  code?: number
  traceId?: string
  data?: unknown

  constructor(message: string, code?: number, traceId?: string, data?: unknown) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.traceId = traceId
    this.data = data
  }
}

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function normalizeResponse<T>(response: AxiosResponse<ApiEnvelope<T> | Blob>): T | AxiosResponse<Blob> {
  if (response.config.responseType === 'blob') {
    return response as AxiosResponse<Blob>
  }

  const payload = response.data as ApiEnvelope<T>
  if (!payload || typeof payload.code !== 'number') {
    throw new ApiRequestError('服务器响应格式不正确')
  }
  if (payload.code !== 200) {
    throw new ApiRequestError(payload.message || '请求失败', payload.code, payload.traceId, payload.data)
  }
  return payload.data
}

export async function parseErrorPayload(data: unknown): Promise<Partial<ApiEnvelope<unknown>> | undefined> {
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      return JSON.parse(text) as Partial<ApiEnvelope<unknown>>
    } catch {
      return undefined
    }
  }
  if (data && typeof data === 'object') {
    return data as Partial<ApiEnvelope<unknown>>
  }
  return undefined
}

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    try {
      return normalizeResponse(response)
    } catch (error) {
      const message = error instanceof Error ? error.message : '请求失败'
      ElMessage.error(message)
      return Promise.reject(error)
    }
  },
  async (error) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    const status = error.response?.status
    const payload = await parseErrorPayload(error.response?.data)
    let message = payload?.message || '请求失败'

    if (!error.response) {
      message = '网络错误，请检查服务是否启动'
    } else if (status === 401 && localStorage.getItem('token')) {
      message = '登录已过期，请重新登录'
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    ElMessage.error(message)
    return Promise.reject(new ApiRequestError(message, payload?.code, payload?.traceId, payload?.data))
  }
)

export default instance
