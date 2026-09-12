import { http } from '../plugins/axios'
import { PlanningApiError, getAccessToken } from './planning'

export async function getTravelPreferences() {
  const accessToken = getAccessToken()
  if (!accessToken) {
    throw new PlanningApiError('AUTHENTICATION_REQUIRED', '登录状态已失效，请重新登录。', {}, 401)
  }
  try {
    const response = await http.get('/api/user/travel-preferences', {
      headers: { Authorization: `Bearer ${accessToken}` }
    })
    if (response?.data?.success === true) return response.data.data
    const apiError = response?.data?.error || {}
    throw new PlanningApiError(
      apiError.code || 'PREFERENCE_REQUEST_FAILED',
      apiError.message || '已保存偏好暂时无法读取。',
      apiError.fields || {},
      response?.status || null
    )
  } catch (error) {
    if (error instanceof PlanningApiError) throw error
    if (error?.response?.status === 401) {
      throw new PlanningApiError('AUTHENTICATION_REQUIRED', '登录状态已失效，请重新登录。', {}, 401)
    }
    const apiError = error?.response?.data?.error || {}
    throw new PlanningApiError(
      apiError.code || 'PREFERENCE_REQUEST_FAILED',
      apiError.message || '已保存偏好暂时无法读取。',
      apiError.fields || {},
      error?.response?.status || null
    )
  }
}
