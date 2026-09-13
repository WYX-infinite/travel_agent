import { http } from '../plugins/axios'
import session from '../auth/session.cjs'
import sseClient from '../planning/sseClient.cjs'

const { createPlanningStreamController } = sseClient

export function getAccessToken() { return session.getAccessToken(sessionStorage) }
export function createPlanningTask(payload) { return http.post('/api/planning-tasks', payload) }
export function getPlanningTask(taskId) { return http.get(`/api/planning-tasks/${encodeURIComponent(taskId)}`) }
export function getPlans() { return http.get('/api/plans') }
export function getPlan(planId) { return http.get(`/api/plans/${encodeURIComponent(planId)}`) }
export function getPlanVersions(planId) { return http.get(`/api/plans/${encodeURIComponent(planId)}/versions`) }
export function replanPlan(planId) { return http.post(`/api/plans/${encodeURIComponent(planId)}/replan`) }

export function subscribePlanningEvents(taskId, callbacks = {}) {
  if (!getAccessToken()) {
    const error = new Error('登录状态已失效，请重新登录。')
    error.status = 401
    error.code = 'AUTHENTICATION_REQUIRED'
    throw error
  }
  // Authorization and Last-Event-ID are headers; the token never enters the URL.
  return createPlanningStreamController({
    taskId,
    url: `/api/planning-tasks/${encodeURIComponent(taskId)}/events`,
    getAccessToken,
    getTask: getPlanningTask,
    ...callbacks
  })
}

export function isAuthenticationError(error) {
  return error?.status === 401 || error?.code === 'AUTHENTICATION_REQUIRED'
}
