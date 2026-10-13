import request from '@/utils/request'

export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
  captchaToken?: string
}

export interface RegisterRequest {
  username: string
  password: string
  captchaToken: string
}

export interface LoginResponse {
  token: string
  username: string
  role: string
}

export interface CaptchaChallenge {
  captchaId: string
  masterImage: string
  tileImage: string
  tileWidth: number
  tileHeight: number
  masterWidth: number
  masterHeight: number
  thumbDisplayX: number
  thumbDisplayY: number
}

export interface CaptchaVerifyResponse {
  captchaToken: string
}

export const authApi = {
  login(data: LoginRequest) {
    return request.post<any, LoginResponse>('/auth/login', data)
  },
  register(data: RegisterRequest) {
    return request.post<any, LoginResponse>('/auth/register', data)
  },
  generateCaptcha() {
    return request.get<any, CaptchaChallenge>('/auth/captcha/generate')
  },
  verifyCaptcha(data: { captchaId: string; x: number; y: number }) {
    return request.post<any, CaptchaVerifyResponse>('/auth/captcha/verify', data)
  },
  captchaRequired(username: string) {
    return request.get<any, { required: boolean }>('/auth/captcha/required', {
      params: { username }
    })
  }
}
