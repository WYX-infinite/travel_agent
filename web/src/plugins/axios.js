import axios from 'axios'
import session from '../auth/session.cjs'
import httpPolicy from '../auth/httpPolicy.cjs'

const { withSessionAuthorization, normalizeFailure } = httpPolicy
const http = axios.create({})
let navigation = { onUnauthorized: null, currentPath: () => '/index' }

export function configureHttpAuth(options = {}) {
  navigation = { ...navigation, ...options }
}

http.interceptors.request.use(
  config => withSessionAuthorization(config, sessionStorage),
  error => Promise.reject(error)
)

http.interceptors.response.use(
  response => {
    const envelope = response?.data
    if (envelope?.success === true) return envelope.data
    if (envelope?.success === false) {
      const failure = envelope.error || {}
      return Promise.reject(normalizeFailure({ ...failure, status: response.status }, {
        storage: sessionStorage,
        currentPath: navigation.currentPath(),
        onUnauthorized: navigation.onUnauthorized
      }))
    }
    return response
  },
  error => {
    const failure = error?.response?.data?.error || {}
    return Promise.reject(normalizeFailure({
      ...failure,
      status: error?.response?.status,
      message: failure.message || error?.message
    }, {
      storage: sessionStorage,
      currentPath: navigation.currentPath(),
      onUnauthorized: navigation.onUnauthorized
    }))
  }
)

export { http }

export default {
  install(app) {
    app.config.globalProperties.axios = http
    app.config.globalProperties.$axios = http
  }
}
