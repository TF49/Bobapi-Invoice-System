import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { userApi } from './user'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn()
  }
}))

const mockedRequest = vi.mocked(request)

describe('userApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('passes server pagination and optional filters', () => {
    userApi.getUsers({
      page: 3,
      pageSize: 20,
      keyword: 'alice',
      role: 'ADMIN',
      enabled: true
    })

    expect(mockedRequest.get).toHaveBeenCalledWith('/users/admin', {
      params: {
        page: 3,
        pageSize: 20,
        keyword: 'alice',
        role: 'ADMIN',
        enabled: true
      }
    })
  })

  it('uses the expected write endpoints and request bodies', () => {
    userApi.createUser({ username: 'alice', password: 'pass123', role: 'USER' })
    userApi.updateRole(2, 'ADMIN')
    userApi.updateStatus(2, false)
    userApi.resetPassword(2, 'newpass9')

    expect(mockedRequest.post).toHaveBeenCalledWith('/users/admin', {
      username: 'alice', password: 'pass123', role: 'USER'
    })
    expect(mockedRequest.put).toHaveBeenCalledWith('/users/admin/2/role', { role: 'ADMIN' })
    expect(mockedRequest.put).toHaveBeenCalledWith('/users/admin/2/status', { enabled: false })
    expect(mockedRequest.put).toHaveBeenCalledWith('/users/admin/2/password', { password: 'newpass9' })
  })
})
