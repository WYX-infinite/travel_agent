const session = require('./session.cjs')

class FrontendApiError extends Error {
  constructor(message, { status = null, code = 'REQUEST_FAILED', fields = null } = {}) {
    super(message)
    this.name = 'FrontendApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}

function withSessionAuthorization(config = {}, storage = globalThis.sessionStorage) {
  const headers = { ...(config.headers || {}) }
  const token = session.getAccessToken(storage)
  if (token) headers.Authorization = `Bearer ${token}`
  else delete headers.Authorization
  return { ...config, headers }
}

function normalizeFailure(input = {}, options = {}) {
  const status = Number(input.status) || null
  const code = input.code || (status === 401 ? 'AUTHENTICATION_REQUIRED' : status === 403 ? 'FORBIDDEN' : 'REQUEST_FAILED')
  if (status === 401 || code === 'AUTHENTICATION_REQUIRED' || code === 'UNAUTHORIZED') {
    const currentPath = options.currentPath || '/index'
    const target = session.authenticationRedirect(options.storage, currentPath)
    if (typeof options.onUnauthorized === 'function' && !currentPath.startsWith('/login')) {
      options.onUnauthorized(target)
    }
    return new FrontendApiError('登录状态已失效，请重新登录。', { status: 401, code: 'AUTHENTICATION_REQUIRED', fields: input.fields })
  }
  if (status === 403 || code === 'FORBIDDEN') {
    return new FrontendApiError('没有权限执行此操作。', { status: 403, code: 'FORBIDDEN', fields: input.fields })
  }
  return new FrontendApiError(input.message || '请求失败，请稍后重试。', { status, code, fields: input.fields })
}

module.exports = { FrontendApiError, withSessionAuthorization, normalizeFailure }
