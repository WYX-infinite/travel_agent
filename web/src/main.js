import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import axiosPlugin, { configureHttpAuth } from './plugins/axios'
import elementPlugin from './plugins/element'

const app = createApp(App)

configureHttpAuth({
  currentPath: () => router.currentRoute.value.fullPath,
  onUnauthorized: target => router.replace(target)
})

app.use(store)
app.use(router)
app.use(axiosPlugin)
app.use(elementPlugin)
app.mount('#app')
