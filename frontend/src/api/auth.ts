import request from '@/utils/request'

export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  role: string
}

export const authApi = {
  login(data: LoginRequest) {
    return request.post<any, LoginResponse>('/auth/login', data)
  },
  register(data: RegisterRequest) {
    return request.post<any, LoginResponse>('/auth/register', data)
  }
}