const ACCESS_TOKEN_KEY = 'accessToken'
const USER_PROFILE_KEY = 'userProfile'
const ROLES = new Set(['USER', 'ADMIN'])

function normalizeUserProfile(profile) {
  if (!profile || typeof profile !== 'object') return null
  const id = Number(profile.id)
  const name = typeof profile.name === 'string' ? profile.name.trim() : ''
  const username = typeof profile.username === 'string' ? profile.username.trim() : ''
  const role = typeof profile.role === 'string' ? profile.role : ''
  if (!Number.isFinite(id) || !name || !username || !ROLES.has(role)) return null
  return { id, name, username, role }
}

function getAccessToken(storage = globalThis.sessionStorage) {
  const value = storage && storage.getItem(ACCESS_TOKEN_KEY)
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function getUserProfile(storage = globalThis.sessionStorage) {
  if (!storage) return null
  try {
    return normalizeUserProfile(JSON.parse(storage.getItem(USER_PROFILE_KEY)))
  } catch (_) {
    return null
  }
}

function saveAuthSession(storage = globalThis.sessionStorage, accessToken, userProfile) {
  const token = typeof accessToken === 'string' ? accessToken.trim() : ''
  const profile = normalizeUserProfile(userProfile)
  if (!storage || !token || !profile) throw new TypeError('登录响应缺少有效的身份信息。')
  storage.setItem(ACCESS_TOKEN_KEY, token)
  storage.setItem(USER_PROFILE_KEY, JSON.stringify(profile))
  return profile
}

function clearAuthSession(storage = globalThis.sessionStorage) {
  if (!storage) return
  storage.removeItem(ACCESS_TOKEN_KEY)
  storage.removeItem(USER_PROFILE_KEY)
}

function isAuthenticated(storage = globalThis.sessionStorage) {
  return Boolean(getAccessToken(storage) && getUserProfile(storage))
}

function isAdmin(storage = globalThis.sessionStorage) {
  return isAuthenticated(storage) && getUserProfile(storage).role === 'ADMIN'
}

function safeRedirect(value, fallback = '/index') {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return fallback
  if (value.includes('\\') || /[\u0000-\u001f]/.test(value)) return fallback
  if (value.split(/[?#]/, 1)[0] === '/login') return fallback
  return value
}

function authenticationRedirect(storage = globalThis.sessionStorage, currentPath = '/index') {
  clearAuthSession(storage)
  return `/login?redirect=${encodeURIComponent(safeRedirect(currentPath))}`
}

function routeDecision(meta = {}, userProfile) {
  if (meta.requiresAuth && !userProfile) return { action: 'login' }
  if (meta.requiresAdmin && userProfile && userProfile.role !== 'ADMIN') return { action: 'forbidden' }
  return { action: 'allow' }
}

module.exports = {
  ACCESS_TOKEN_KEY,
  USER_PROFILE_KEY,
  normalizeUserProfile,
  getAccessToken,
  getUserProfile,
  saveAuthSession,
  clearAuthSession,
  isAuthenticated,
  isAdmin,
  safeRedirect,
  authenticationRedirect,
  routeDecision
}
