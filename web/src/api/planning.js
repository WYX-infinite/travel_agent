import { http } from '../plugins/axios'
import sseClient from '../planning/sseClient.cjs'

const { createPlanningStreamController } = sseClient

export class PlanningApiError extends Error {
  constructor(code, message, fields = {}, status = null) {
    super(message)
    this.name = 'PlanningApiError'
    this.code = code
    this.fields = fields || {}
    this.status = status
  }
}

export function getAccessToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    return typeof user?.accessToken === 'string' && user.accessToken ? user.accessToken : null
  } catch (_) {
    return null
  }
}

function authConfig(extra = {}) {
  const accessToken = getAccessToken()
  if (!accessToken) {
    throw new PlanningApiError('AUTHENTICATION_REQUIRED', '登录状态已失效，请重新登录。', {}, 401)
  }
  return {
    ...extra,
    headers: {
      ...(extra.headers || {}),
      Authorization: `Bearer ${accessToken}`
    }
  }
}

function unwrap(response) {
  const envelope = response?.data
  if (envelope?.success === true) return envelope.data
  const error = envelope?.error || {}
  throw new PlanningApiError(
    error.code || 'REQUEST_FAILED',
    error.message || '请求未成功，请稍后重试。',
    error.fields || {},
    response?.status || null
  )
}

function rethrow(error) {
  if (error instanceof PlanningApiError) throw error
  const status = error?.response?.status || null
  if (status === 401) {
    throw new PlanningApiError('AUTHENTICATION_REQUIRED', '登录状态已失效，请重新登录。', {}, 401)
  }
  const envelopeError = error?.response?.data?.error || {}
  throw new PlanningApiError(
    envelopeError.code || 'NETWORK_ERROR',
    envelopeError.message || '网络请求失败，请稍后重试。',
    envelopeError.fields || {},
    status
  )
}

async function request(config) {
  try {
    return unwrap(await http(authConfig(config)))
  } catch (error) {
    rethrow(error)
  }
}

export function createPlanningTask(payload) {
  return request({ method: 'post', url: '/api/planning-tasks', data: payload })
}

export function getPlanningTask(taskId) {
  return request({ method: 'get', url: `/api/planning-tasks/${encodeURIComponent(taskId)}` })
}

export function getPlan(planId) {
  return request({ method: 'get', url: `/api/plans/${encodeURIComponent(planId)}` })
}

export function getPlanVersions(planId) {
  return request({ method: 'get', url: `/api/plans/${encodeURIComponent(planId)}/versions` })
}

export function subscribePlanningEvents(taskId, callbacks = {}) {
  const accessToken = getAccessToken()
  if (!accessToken) {
    throw new PlanningApiError('AUTHENTICATION_REQUIRED', '登录状态已失效，请重新登录。', {}, 401)
  }
  // The stream helper sends Authorization and an exclusive Last-Event-ID replay cursor as headers.
  return createPlanningStreamController({
    taskId,
    url: `/api/planning-tasks/${encodeURIComponent(taskId)}/events`,
    accessToken,
    getTask: getPlanningTask,
    ...callbacks
  })
}

export function isAuthenticationError(error) {
  return error?.status === 401 || error?.code === 'AUTHENTICATION_REQUIRED'
}
