import { describe, expect, it } from 'vitest'
import { validatePassword, validateUsername } from './userValidation'

describe('user management form validation', () => {
  it('enforces the shared username rules', () => {
    expect(validateUsername('')).toBe('用户名不能为空')
    expect(validateUsername('a')).toBe('用户名长度必须在 2-20 位之间')
    expect(validateUsername('user-name')).toBe('用户名只能包含汉字、字母、数字和下划线')
    expect(validateUsername('alice_01')).toBeNull()
    // 汉字场景
    expect(validateUsername('张')).toBe('用户名长度必须在 2-20 位之间')
    expect(validateUsername('张三')).toBeNull()
    expect(validateUsername('张三-四')).toBe('用户名只能包含汉字、字母、数字和下划线')
    expect(validateUsername('用户_01')).toBeNull()
  })

  it('requires a bounded password containing letters and numbers', () => {
    expect(validatePassword('')).toBe('密码不能为空')
    expect(validatePassword('12345')).toBe('密码长度必须在 6-20 位之间')
    expect(validatePassword('onlyletters')).toBe('密码必须包含字母和数字')
    expect(validatePassword('secure9')).toBeNull()
  })
})
