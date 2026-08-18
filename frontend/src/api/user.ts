import request from '@/utils/request'

export type UserRole = 'USER' | 'ADMIN'

export interface ManagedUser {
  id: number
  username: string
  role: UserRole
  enabled: boolean
  createdAt: string
  updatedAt: string
  self: boolean
}

export interface UserStats {
  totalUsers: number
  enabledUsers: number
  disabledUsers: number
  adminUsers: number
}

export interface UserPage {
  users: ManagedUser[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  stats: UserStats
}

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  role?: UserRole
  enabled?: boolean
}

export interface CreateUserRequest {
  username: string
  password: string
  role: UserRole
}

export const userApi = {
  getUsers(params: UserQuery) {
    return request.get<any, UserPage>('/users/admin', { params })
  },

  createUser(data: CreateUserRequest) {
    return request.post<any, ManagedUser>('/users/admin', data)
  },

  updateRole(id: number, role: UserRole) {
    return request.put<any, ManagedUser>(`/users/admin/${id}/role`, { role })
  },

  updateStatus(id: number, enabled: boolean) {
    return request.put<any, ManagedUser>(`/users/admin/${id}/status`, { enabled })
  },

  resetPassword(id: number, password: string) {
    return request.put<any, ManagedUser>(`/users/admin/${id}/password`, { password })
  }
}
