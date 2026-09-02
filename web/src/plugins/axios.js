import axios from 'axios'

const http = axios.create({})

http.interceptors.request.use(
  config => config,
  error => Promise.reject(error)
)

http.interceptors.response.use(
  response => response,
  error => Promise.reject(error)
)

export { http }

export default {
  install(app) {
    app.config.globalProperties.axios = http
    app.config.globalProperties.$axios = http
  }
}