const { defineConfig } = require('@vue/cli-service')
const apiTarget = process.env.VUE_APP_API_TARGET || 'http://localhost:8181'

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true
      }
    }
  }
})
