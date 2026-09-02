const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const projectRoot = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

test('package dependencies are compatible with Vue 3', () => {
  const packageJson = JSON.parse(read('package.json'))
  const dependencies = packageJson.dependencies || {}
  const devDependencies = packageJson.devDependencies || {}

  assert.match(dependencies.vue, /^\^?3\./)
  assert.ok(dependencies['element-plus'])
  assert.equal(dependencies['element-ui'], undefined)
  assert.equal(devDependencies['vue-cli-plugin-element'], undefined)
  assert.equal(devDependencies['vue-cli-plugin-axios'], undefined)
})

test('main creates and mounts exactly one Vue application', () => {
  const main = read('src/main.js')
  const appCreations = main.match(/createApp\(App\)/g) || []

  assert.equal(appCreations.length, 1)
  assert.match(main, /app\.use\(store\)/)
  assert.match(main, /app\.use\(router\)/)
  assert.match(main, /app\.mount\(['"]#app['"]\)/)
})

test('plugins use Vue 3 installation APIs without Vue 2 globals', () => {
  const elementPlugin = read('src/plugins/element.js')
  const axiosPlugin = read('src/plugins/axios.js')
  const pluginSources = `${elementPlugin}\n${axiosPlugin}`

  assert.match(elementPlugin, /from ['"]element-plus['"]/) 
  assert.match(elementPlugin, /app\.use\(ElementPlus\)/)
  assert.match(axiosPlugin, /app\.config\.globalProperties\.\$axios/)
  assert.doesNotMatch(pluginSources, /Vue\.use|Vue\.prototype|Plugin\.install/)
})
