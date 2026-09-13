const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const login = fs.readFileSync(
  path.resolve(__dirname, '../src/views/Login.vue'),
  'utf8'
)

test('Login.vue uses Vue 3 and Element Plus form APIs without the legacy registration surface', () => {
  assert.match(login, /<el-form[\s\S]*@submit\.prevent="handleLogin"/)
  assert.match(login, /show-password/)
  assert.match(login, /captchaCode/)
  assert.doesNotMatch(login, /dialogVisible|registerForm|注册账号/)
  assert.doesNotMatch(login, /\.sync|\sslot=|el-icon-/)
})

test('Login.vue sends captcha and login requests through the installed Axios plugin', () => {
  const pluginCalls = login.match(/this\.\$axios\.post\(/g) || []

  assert.equal(pluginCalls.length, 1)
  assert.match(login, /this\.\$axios\.get\(['"]\/api\/auth\/captcha['"]\)/)
  assert.match(login, /this\.\$axios\.post\(['"]\/api\/auth\/login['"]/)
  assert.doesNotMatch(login, /(^|[^.$\w])axios\.post\(/m)
})
