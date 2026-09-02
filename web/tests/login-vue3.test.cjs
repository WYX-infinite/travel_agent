const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const login = fs.readFileSync(
  path.resolve(__dirname, '../src/views/Login.vue'),
  'utf8'
)

test('Login.vue uses Vue 3 and Element Plus template APIs', () => {
  assert.match(login, /<el-dialog[\s\S]*v-model="dialogVisible"/)
  assert.match(login, /<template #suffix>/)
  assert.match(login, /<template #footer>/)
  assert.match(login, /show-password/)
  assert.match(login, /<el-radio value="男">/)
  assert.match(login, /<el-radio value="女">/)
  assert.doesNotMatch(login, /\.sync|\sslot=|el-icon-/)
})

test('Login.vue sends requests through the installed Axios plugin', () => {
  const pluginCalls = login.match(/this\.\$axios\.post\(/g) || []

  assert.equal(pluginCalls.length, 2)
  assert.doesNotMatch(login, /(^|[^.$\w])axios\.post\(/m)
})
