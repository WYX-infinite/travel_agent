const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')

const configPath = path.resolve(__dirname, '../vue.config.js')

function loadConfig(apiTarget) {
  const previousTarget = process.env.VUE_APP_API_TARGET

  if (apiTarget === undefined) delete process.env.VUE_APP_API_TARGET
  else process.env.VUE_APP_API_TARGET = apiTarget

  delete require.cache[require.resolve(configPath)]
  const config = require(configPath)

  if (previousTarget === undefined) delete process.env.VUE_APP_API_TARGET
  else process.env.VUE_APP_API_TARGET = previousTarget

  return config
}

test('dev server proxies API requests to the local backend by default', () => {
  const config = loadConfig()

  assert.equal(config.devServer.proxy['/api'].target, 'http://localhost:8181')
  assert.equal(config.devServer.proxy['/api'].changeOrigin, true)
})

test('dev server proxy target can be overridden through VUE_APP_API_TARGET', () => {
  const config = loadConfig('http://localhost:8182')

  assert.equal(config.devServer.proxy['/api'].target, 'http://localhost:8182')
})
