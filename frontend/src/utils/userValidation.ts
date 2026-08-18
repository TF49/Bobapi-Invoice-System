const USERNAME_PATTERN = /^[A-Za-z0-9_]+$/
const PASSWORD_PATTERN = /^(?=.*[0-9])(?=.*[a-zA-Z]).{6,20}$/

export function validateUsername(value: string): string | null {
  if (!value) return '用户名不能为空'
  if (value.length < 3 || value.length > 20) return '用户名长度必须在 3-20 位之间'
  if (!USERNAME_PATTERN.test(value)) return '用户名只能包含字母、数字和下划线'
  return null
}

export function validatePassword(value: string): string | null {
  if (!value) return '密码不能为空'
  if (value.length < 6 || value.length > 20) return '密码长度必须在 6-20 位之间'
  if (!PASSWORD_PATTERN.test(value)) return '密码必须包含字母和数字'
  return null
}
