import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import axiosPlugin from './plugins/axios'
import elementPlugin from './plugins/element'

const app = createApp(App)

app.use(store)
app.use(router)
app.use(axiosPlugin)
app.use(elementPlugin)
app.mount('#app')
