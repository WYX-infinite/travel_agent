const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const session = require('../src/auth/session.cjs')
const httpPolicy = require('../src/auth/httpPolicy.cjs')
const history = require('../src/planning/historyState.cjs')
const preferences = require('../src/preferences/preferenceState.cjs')
const knowledge = require('../src/knowledge/knowledgeState.cjs')

const projectRoot = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem(key) { return values.has(key) ? values.get(key) : null },
    setItem(key, value) { values.set(key, String(value)) },
    removeItem(key) { values.delete(key) },
    snapshot() { return Object.fromEntries(values) }
  }
}

const validUser = { id: 7, name: '普通用户', username: 'traveler', role: 'USER' }

test('auth session stores only the token and sanitized UserProfile in sessionStorage', () => {
  const storage = memoryStorage({ activePlanningTaskId: '99' })
  session.saveAuthSession(storage, 'secret-token', {
    ...validUser,
    password: 'must-not-persist',
    captchaCode: '1234',
    captchaId: 'captcha-id',
    interests: ['FOOD']
  })

  assert.deepEqual(storage.snapshot(), {
    activePlanningTaskId: '99',
    accessToken: 'secret-token',
    userProfile: JSON.stringify(validUser)
  })
  assert.equal(session.getAccessToken(storage), 'secret-token')
  assert.deepEqual(session.getUserProfile(storage), validUser)
  assert.equal(session.isAuthenticated(storage), true)
  assert.equal(session.isAdmin(storage), false)
})

test('corrupt or incomplete session data degrades to logged out and logout preserves task recovery', () => {
  for (const profile of ['{broken', JSON.stringify({ id: 7, username: 'u', role: 'USER' }), JSON.stringify({ ...validUser, role: 'ROOT' })]) {
    const storage = memoryStorage({ accessToken: 'token', userProfile: profile, activePlanningTaskId: '41' })
    assert.equal(session.isAuthenticated(storage), false)
    assert.equal(session.getUserProfile(storage), null)
    session.clearAuthSession(storage)
    assert.deepEqual(storage.snapshot(), { activePlanningTaskId: '41' })
  }
})

test('safe redirects accept only local absolute paths and route policy keeps 403 distinct from login', () => {
  assert.equal(session.safeRedirect('/plans/3?tab=versions'), '/plans/3?tab=versions')
  for (const unsafe of ['https://evil.test', '//evil.test', '/\\evil.test', 'javascript:alert(1)', 'plans']) {
    assert.equal(session.safeRedirect(unsafe), '/index')
  }
  assert.equal(session.safeRedirect('/login?redirect=%2Flogin'), '/index')
  assert.deepEqual(session.routeDecision({ requiresAuth: true }, null), { action: 'login' })
  assert.deepEqual(session.routeDecision({ requiresAuth: true, requiresAdmin: true }, validUser), { action: 'forbidden' })
  assert.deepEqual(session.routeDecision({ requiresAuth: true, requiresAdmin: true }, { ...validUser, role: 'ADMIN' }), { action: 'allow' })
})

test('HTTP policy reads the current token for every request and never creates an empty bearer', () => {
  const storage = memoryStorage()
  assert.equal(httpPolicy.withSessionAuthorization({ headers: {} }, storage).headers.Authorization, undefined)
  storage.setItem('accessToken', 'first')
  assert.equal(httpPolicy.withSessionAuthorization({ headers: {} }, storage).headers.Authorization, 'Bearer first')
  storage.setItem('accessToken', 'rotated')
  assert.equal(httpPolicy.withSessionAuthorization({ headers: {} }, storage).headers.Authorization, 'Bearer rotated')
})

test('401 clears auth and redirects safely while 403 preserves the session', () => {
  const storage = memoryStorage({ accessToken: 'token', userProfile: JSON.stringify(validUser), activePlanningTaskId: '8' })
  const redirects = []
  const unauthorized = httpPolicy.normalizeFailure({ status: 401, code: 'AUTHENTICATION_REQUIRED' }, {
    storage,
    currentPath: '/plans/4',
    onUnauthorized: target => redirects.push(target)
  })
  assert.equal(unauthorized.status, 401)
  assert.deepEqual(storage.snapshot(), { activePlanningTaskId: '8' })
  assert.deepEqual(redirects, ['/login?redirect=%2Fplans%2F4'])

  session.saveAuthSession(storage, 'new-token', validUser)
  const forbidden = httpPolicy.normalizeFailure({ status: 403, message: 'secret server detail' }, { storage })
  assert.equal(forbidden.code, 'FORBIDDEN')
  assert.equal(forbidden.status, 403)
  assert.equal(session.isAuthenticated(storage), true)
  assert.doesNotMatch(forbidden.message, /secret server detail/)
})

test('stream authentication redirect clears auth before the login route guard runs', () => {
  const storage = memoryStorage({ accessToken: 'expired', userProfile: JSON.stringify(validUser), activePlanningTaskId: '8' })
  const target = session.authenticationRedirect(storage, '/planner')
  assert.equal(target, '/login?redirect=%2Fplanner')
  assert.equal(session.isAuthenticated(storage), false)
  assert.deepEqual(session.routeDecision({ requiresAuth: false }, session.getUserProfile(storage)), { action: 'allow' })
  assert.deepEqual(storage.snapshot(), { activePlanningTaskId: '8' })
})

test('version comparison preserves unavailable metrics and reports explicit warning changes', () => {
  const comparison = history.comparePlanVersions(
    {
      versionNumber: 1,
      createdAt: '2026-01-01T00:00:00Z',
      estimatedTotalCost: 900,
      metrics: { sourceCoverage: { percentage: 80 }, routeCoverage: null, budgetCompleteness: { percentage: 75 }, rulePassRate: { percentage: 60 } },
      warnings: [{ code: 'RAIN', severity: 'WARNING' }, { code: 'TIME', severity: 'ERROR' }]
    },
    {
      versionNumber: 2,
      createdAt: '2026-01-02T00:00:00Z',
      estimatedTotalCost: 1000,
      metrics: { sourceCoverage: { percentage: 90 }, routeCoverage: null, budgetCompleteness: { percentage: 100 }, rulePassRate: { percentage: 80 } },
      warnings: [{ code: 'RAIN', severity: 'WARNING' }, { code: 'BUDGET', severity: 'WARNING' }]
    },
    2
  )

  assert.equal(comparison.cost.delta, 100)
  assert.equal(comparison.metrics.sourceCoverage.delta, 10)
  assert.equal(comparison.metrics.routeCoverage.before, null)
  assert.equal(comparison.metrics.routeCoverage.delta, null)
  assert.equal(comparison.metrics.budgetCompleteness.delta, 25)
  assert.equal(comparison.metrics.rulePassRate.delta, 20)
  assert.equal(comparison.warnings.beforeCount, 2)
  assert.equal(comparison.warnings.afterCount, 2)
  assert.deepEqual(comparison.warnings.added, [{ code: 'BUDGET', severity: 'WARNING' }])
  assert.deepEqual(comparison.warnings.removed, [{ code: 'TIME', severity: 'ERROR' }])
  assert.equal(comparison.after.isCurrent, true)
})

test('warning comparison uses full fields and multiplicity instead of collapsing by code', () => {
  const duplicate = { code: 'TIME', severity: 'WARNING', date: '2026-01-01', itemIndex: 1, message: '过晚' }
  const changed = { ...duplicate, message: '营业时间冲突' }
  const comparison = history.comparePlanVersions(
    { warnings: [duplicate, duplicate], metrics: {} },
    { warnings: [duplicate, changed], metrics: {} }
  )
  assert.deepEqual(comparison.warnings.added, [changed])
  assert.deepEqual(comparison.warnings.removed, [duplicate])
  assert.notEqual(history.describeWarning(duplicate), history.describeWarning(changed))
  assert.match(history.describeWarning({ ...duplicate, requiredAdjustment: '提前两小时' }), /需要调整：提前两小时/)
  assert.match(history.describeWarning(duplicate), /活动序号：1/)
})

test('preference payload is a validated full replacement without user or cache fields', () => {
  const form = preferences.emptyPreferenceForm()
  assert.deepEqual(preferences.validatePreferenceForm(form), {})
  assert.deepEqual(preferences.buildPreferencePayload(form), {
    interests: [], pace: null, travelMode: null, accommodationPreference: null
  })
  form.interests = ['FOOD', 'HISTORY']
  form.pace = 'MODERATE'
  form.travelMode = 'TRANSIT'
  form.accommodationPreference = '  地铁站步行十分钟内  '
  form.userId = 88
  form.updatedAt = 'old'

  assert.deepEqual(preferences.validatePreferenceForm(form), {})
  assert.deepEqual(preferences.buildPreferencePayload(form), {
    interests: ['FOOD', 'HISTORY'],
    pace: 'MODERATE',
    travelMode: 'TRANSIT',
    accommodationPreference: '地铁站步行十分钟内'
  })
  assert.ok(preferences.validatePreferenceForm({ ...form, accommodationPreference: ' '.repeat(3) }).accommodationPreference)
  assert.ok(preferences.validatePreferenceForm({ ...form, accommodationPreference: 'a'.repeat(256) }).accommodationPreference)
  assert.ok(preferences.validatePreferenceForm({ ...form, interests: ['ALIEN'] }).interests)
})

test('knowledge form validates frozen multipart metadata and management state rules', () => {
  const metadata = {
    file: { name: 'guide.pdf' }, title: '西湖指南', city: '杭州', publisher: '文旅局',
    sourceUrl: 'https://example.test/guide', publishedAt: '2026-09-01', documentType: 'ATTRACTION_GUIDE'
  }
  assert.deepEqual(knowledge.validateKnowledgeUpload(metadata), {})
  assert.deepEqual(knowledge.buildKnowledgeMetadata(metadata), {
    title: '西湖指南', city: '杭州', publisher: '文旅局', sourceUrl: 'https://example.test/guide',
    publishedAt: '2026-09-01', documentType: 'ATTRACTION_GUIDE'
  })
  assert.ok(knowledge.validateKnowledgeUpload({ ...metadata, sourceUrl: 'file:///etc/passwd' }).sourceUrl)
  assert.ok(knowledge.validateKnowledgeUpload({ ...metadata, sourceUrl: 'https://user:password@example.test/guide' }).sourceUrl)
  assert.equal(knowledge.statusPresentation('DISABLED').label, '已停用')
  assert.equal(knowledge.statusPresentation('ALIEN').label, '未知状态')
  assert.equal(knowledge.nextDocumentStatus('ACTIVE'), 'DISABLED')
  assert.equal(knowledge.nextDocumentStatus('DISABLED'), 'ACTIVE')
  assert.equal(knowledge.nextDocumentStatus('FAILED'), null)
  assert.equal(knowledge.canDeleteDocument('ACTIVE'), false)
  assert.equal(knowledge.canDeleteDocument('DISABLED'), true)
})

test('task sixteen source surface contains only real frozen endpoints and no legacy auth storage', () => {
  const login = read('src/views/Login.vue')
  const router = read('src/router/index.js')
  const axios = read('src/plugins/axios.js')
  const planningApi = read('src/api/planning.js')
  const preferencesApi = read('src/api/preferences.js')
  const sse = read('src/planning/sseClient.cjs')
  const knowledgeApi = read('src/api/knowledge.js')
  const header = read('src/components/Header.vue')
  const sources = `${login}\n${router}\n${axios}\n${planningApi}\n${preferencesApi}\n${sse}\n${header}`

  for (const endpoint of [
    '/api/auth/captcha', '/api/auth/login', '/api/plans', '/api/user/travel-preferences',
    '/api/admin/knowledge-documents', '/chunks', '/status', '/reindex'
  ]) assert.match(`${sources}\n${knowledgeApi}`, new RegExp(endpoint.replaceAll('/', '\\/')))

  assert.doesNotMatch(sources, /localStorage|localhost:8181\/(captcha|login)|\/user\/add|[?&]token=/)
  assert.match(router, /path:\s*['"]\/plans['"]/)
  assert.match(router, /path:\s*['"]\/preferences['"]/)
  assert.match(router, /path:\s*['"]\/admin\/knowledge['"]/)
  assert.match(header, /ADMIN/)
})
