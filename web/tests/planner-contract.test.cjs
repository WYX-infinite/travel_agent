const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const {
  INTERESTS,
  PACES,
  COMPANION_TYPES,
  TRAVEL_MODES,
  applySavedPreferences,
  buildPlanningPayload,
  classifyTaskStatus,
  createPlannerState,
  disableSavedPreferences,
  formatCost,
  formatMetric,
  markFieldDirty,
  mergeTaskEvent,
  preferenceFailureNotice,
  safeHttpUrl,
  selectPlanVersion,
  shouldEmitTerminal,
  terminalConfirmationForUpdate,
  warningPresentation,
  validatePlanningPayload
} = require('../src/planning/plannerState.cjs')
const {
  createSseParser,
  createPlanningStreamController
} = require('../src/planning/sseClient.cjs')

const projectRoot = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

function validForm() {
  return {
    destinationCity: '杭州',
    startDate: '2026-10-20',
    endDate: '2026-10-21',
    travelers: 2,
    totalBudget: 3000,
    companionType: 'FRIENDS',
    interests: ['HISTORY', 'FOOD'],
    pace: 'MODERATE',
    accommodationAddress: '杭州市上城区湖滨街道',
    travelMode: 'TRANSIT',
    specialRequirements: '每天最晚20:00结束',
    useSavedPreferences: true
  }
}

test('planner payload preserves every frozen request field and enum set', () => {
  assert.deepEqual(buildPlanningPayload(validForm()), validForm())
  assert.deepEqual(INTERESTS, ['FOOD', 'HISTORY', 'NATURE', 'PHOTOGRAPHY', 'SHOPPING'])
  assert.deepEqual(PACES, ['RELAXED', 'MODERATE', 'COMPACT'])
  assert.deepEqual(COMPANION_TYPES, ['SOLO', 'COUPLE', 'FAMILY', 'FRIENDS', 'SENIORS'])
  assert.deepEqual(TRAVEL_MODES, ['WALKING', 'TRANSIT', 'TAXI', 'DRIVING'])
  assert.deepEqual(validatePlanningPayload(validForm()), {})
})

test('planner validation rejects inverted dates, non-positive values, and unknown enums', () => {
  const errors = validatePlanningPayload({
    ...validForm(),
    startDate: '2026-10-22',
    endDate: '2026-10-20',
    travelers: 0,
    totalBudget: -1,
    companionType: 'TEAM',
    interests: ['BEACH'],
    pace: 'FAST',
    travelMode: 'FLYING'
  })

  assert.deepEqual(Object.keys(errors).sort(), [
    'companionType', 'dateRange', 'interests', 'pace', 'totalBudget', 'travelMode', 'travelers'
  ])
})

test('saved preferences fill only empty untouched fields', () => {
  const state = createPlannerState({ ...validForm(), interests: [], pace: '', travelMode: '', accommodationAddress: '' })
  applySavedPreferences(state, {
    interests: ['NATURE'], pace: 'RELAXED', travelMode: 'WALKING', accommodationPreference: '地铁站附近'
  })

  assert.deepEqual(state.form.interests, ['NATURE'])
  assert.equal(state.form.pace, 'RELAXED')
  assert.equal(state.form.travelMode, 'WALKING')
  assert.equal(state.form.accommodationAddress, '地铁站附近')
  for (const field of ['interests', 'pace', 'travelMode', 'accommodationAddress']) {
    assert.equal(state.fields[field].source, 'preference')
    assert.equal(state.fields[field].dirty, false)
  }
})

test('late preference response never overwrites user input', () => {
  const state = createPlannerState({ ...validForm(), interests: [], pace: '', travelMode: '', accommodationAddress: '' })
  state.form.pace = 'COMPACT'
  markFieldDirty(state, 'pace')
  state.form.accommodationAddress = '西湖边'
  markFieldDirty(state, 'accommodationAddress')

  applySavedPreferences(state, { pace: 'RELAXED', accommodationPreference: '地铁站附近' })

  assert.equal(state.form.pace, 'COMPACT')
  assert.equal(state.form.accommodationAddress, '西湖边')
  assert.equal(state.fields.pace.source, 'user')
  assert.equal(state.fields.accommodationAddress.source, 'user')
})

test('disabling saved preferences rejects late responses and clears only untouched prefills', () => {
  const state = createPlannerState({ ...validForm(), interests: [], pace: '', travelMode: '', accommodationAddress: '' })
  applySavedPreferences(state, {
    interests: ['FOOD'], pace: 'RELAXED', travelMode: 'TRANSIT', accommodationPreference: '湖边'
  })
  state.form.travelMode = 'TAXI'
  markFieldDirty(state, 'travelMode')
  disableSavedPreferences(state)
  applySavedPreferences(state, {
    interests: ['HISTORY'], pace: 'COMPACT', travelMode: 'DRIVING', accommodationPreference: '市中心'
  })

  assert.deepEqual(state.form.interests, [])
  assert.equal(state.form.pace, '')
  assert.equal(state.form.accommodationAddress, '')
  assert.equal(state.form.travelMode, 'TAXI')
  assert.equal(state.form.useSavedPreferences, false)
})

test('disabling preferences removes untouched prefilled choices but preserves user-added choices', () => {
  const state = createPlannerState({ ...validForm(), interests: [], pace: '', travelMode: '', accommodationAddress: '' })
  applySavedPreferences(state, { interests: ['FOOD'] })
  state.form.interests = ['FOOD', 'HISTORY']
  markFieldDirty(state, 'interests')
  disableSavedPreferences(state)

  assert.deepEqual(state.form.interests, ['HISTORY'])
  assert.equal(state.fields.interests.source, 'user')
})

test('preference failure is non-blocking and leaves a valid manual payload intact', () => {
  const notice = preferenceFailureNotice(new Error('database details must stay hidden'))
  assert.equal(notice.type, 'warning')
  assert.equal(notice.blocking, false)
  assert.doesNotMatch(notice.message, /database details/)
  assert.deepEqual(validatePlanningPayload(validForm()), {})
})

test('task statuses only navigate on COMPLETED with a plan id', () => {
  assert.deepEqual(classifyTaskStatus({ status: 'PENDING' }), { kind: 'active', navigate: false })
  assert.deepEqual(classifyTaskStatus({ status: 'RUNNING' }), { kind: 'active', navigate: false })
  assert.deepEqual(classifyTaskStatus({ status: 'COMPLETED', planId: 91 }), { kind: 'completed', navigate: true, planId: 91 })
  assert.deepEqual(classifyTaskStatus({ status: 'COMPLETED', planId: null }), { kind: 'invalid-completion', navigate: false })
  assert.deepEqual(classifyTaskStatus({ status: 'NEEDS_ADJUSTMENT' }), { kind: 'needs-adjustment', navigate: false })
  assert.deepEqual(classifyTaskStatus({ status: 'FAILED', errorCode: 'SAFE', errorMessage: 'safe message' }), {
    kind: 'failed', navigate: false, errorCode: 'SAFE', errorMessage: 'safe message'
  })
  assert.deepEqual(classifyTaskStatus({ status: 'ALIEN' }), { kind: 'unknown', navigate: false })
})

test('progress presentation emits terminal only after a complete task snapshot confirms it', () => {
  assert.equal(shouldEmitTerminal({ status: 'COMPLETED' }, false), false)
  assert.equal(shouldEmitTerminal({ status: 'FAILED' }, false), false)
  assert.equal(shouldEmitTerminal({ status: 'COMPLETED', planId: 91 }, true), true)
  assert.equal(shouldEmitTerminal({ status: 'NEEDS_ADJUSTMENT' }, true), true)
  assert.equal(shouldEmitTerminal({ status: 'RUNNING' }, true), false)
})

test('a non-terminal snapshot never confirms a later terminal progress trace', () => {
  const confirmedRunning = terminalConfirmationForUpdate({ status: 'RUNNING' }, 'task-status')
  assert.equal(confirmedRunning, false)
  assert.equal(
    shouldEmitTerminal(
      { status: 'COMPLETED' },
      terminalConfirmationForUpdate({ status: 'COMPLETED' }, 'planning-progress')
    ),
    false
  )
  assert.equal(
    shouldEmitTerminal(
      { status: 'COMPLETED', planId: 91 },
      terminalConfirmationForUpdate({ status: 'COMPLETED', planId: 91 }, 'planning-status')
    ),
    true
  )
})

test('progress events deduplicate trace ids without inventing ids for planning-status', () => {
  let timeline = []
  timeline = mergeTaskEvent(timeline, { event: 'planning-progress', id: '18', data: { eventId: 18, status: 'RUNNING' } })
  timeline = mergeTaskEvent(timeline, { event: 'planning-progress', id: '18', data: { eventId: 18, status: 'RUNNING' } })
  timeline = mergeTaskEvent(timeline, { event: 'planning-status', id: '', data: { status: 'FAILED' } })

  assert.equal(timeline.length, 2)
  assert.equal(timeline[0].eventId, 18)
  assert.equal(timeline[1].eventId, undefined)
  assert.match(timeline[1].key, /^status-/)
})

test('SSE parser supports chunk boundaries, CRLF, comments, ids, events, and multiline data', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))
  parser.push('id: 17\r\nevent: planning-pro')
  parser.push('gress\r\ndata: {"status":\r\ndata: "RUNNING"}\r\n: heartbeat\r\n\r\n')
  parser.end()

  assert.deepEqual(events, [{
    id: '17',
    event: 'planning-progress',
    data: '{"status":\n"RUNNING"}'
  }])
})

test('stream authenticates with headers, replays exclusively, queries before reconnect, and stops at terminal status', async () => {
  const calls = []
  const scheduled = []
  let fetchCount = 0
  const fetchImpl = async (url, options) => {
    calls.push({ url, options })
    fetchCount += 1
    return new Response(fetchCount === 1
      ? 'id: 3\nevent: planning-progress\ndata: {"eventId":3,"status":"RUNNING"}\n\n'
      : 'event: planning-status\ndata: {"status":"COMPLETED","planId":44}\n\n', {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }
  const statuses = [{ status: 'RUNNING' }]
  const received = []
  const controller = createPlanningStreamController({
    taskId: 9,
    accessToken: 'secret-token',
    fetchImpl,
    getTask: async () => statuses.shift(),
    schedule: (fn, delay) => { scheduled.push({ fn, delay }); return scheduled.length },
    cancelSchedule: () => {},
    onEvent: event => received.push(event)
  })

  await controller.start()
  assert.equal(calls.length, 1)
  assert.equal(calls[0].options.headers.Authorization, 'Bearer secret-token')
  assert.equal(calls[0].options.headers['Last-Event-ID'], undefined)
  assert.equal(received[0].data.eventId, 3)
  assert.equal(scheduled[0].delay, 500)
  await scheduled[0].fn()
  assert.equal(calls[1].options.headers['Last-Event-ID'], '3')
  assert.equal(received.at(-1).data.status, 'COMPLETED')
  assert.equal(scheduled.length, 1)
})

test('COMPLETED trace is reconciled through task status before the stream stops', async () => {
  const taskStatuses = []
  const events = []
  const scheduled = []
  const controller = createPlanningStreamController({
    taskId: 12,
    accessToken: 'token',
    fetchImpl: async () => new Response(
      'id: 8\nevent: planning-progress\ndata: {"eventId":8,"status":"COMPLETED","progressPercent":100}\n\n',
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } }
    ),
    getTask: async () => ({ status: 'COMPLETED', planId: 77, progressPercent: 100 }),
    schedule: (fn, delay) => { scheduled.push({ fn, delay }); return scheduled.length },
    cancelSchedule: () => {},
    onEvent: event => events.push(event),
    onTaskStatus: status => taskStatuses.push(status)
  })

  await controller.start()

  assert.equal(events[0].data.planId, undefined)
  assert.deepEqual(taskStatuses, [{ status: 'COMPLETED', planId: 77, progressPercent: 100 }])
  assert.equal(controller.isStopped(), true)
  assert.equal(scheduled.length, 0)
})

test('SSE 401 is exposed as a stable fatal authentication error without reconnecting', async () => {
  const errors = []
  const scheduled = []
  const controller = createPlanningStreamController({
    taskId: 13,
    accessToken: 'expired-token',
    fetchImpl: async () => new Response(JSON.stringify({
      success: false,
      data: null,
      error: { code: 'AUTHENTICATION_REQUIRED', message: 'authentication required', fields: {} }
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }),
    getTask: async () => { throw new Error('must not query after fatal 401') },
    schedule: (fn, delay) => { scheduled.push({ fn, delay }); return scheduled.length },
    cancelSchedule: () => {},
    onError: error => errors.push(error)
  })

  await controller.start()

  assert.equal(errors.length, 1)
  assert.equal(errors[0].status, 401)
  assert.equal(errors[0].code, 'AUTHENTICATION_REQUIRED')
  assert.equal(errors[0].message, '登录状态已失效，请重新登录。')
  assert.equal(controller.isStopped(), true)
  assert.equal(scheduled.length, 0)
})

test('stream has bounded reconnects, reports malformed JSON, and stop releases active resources', async () => {
  const scheduled = []
  const cancelled = []
  const errors = []
  const states = []
  const fetchImpl = async () => new Response('event: planning-progress\ndata: not-json\n\n', {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' }
  })
  const controller = createPlanningStreamController({
    taskId: 7,
    accessToken: 'token',
    fetchImpl,
    getTask: async () => ({ status: 'RUNNING' }),
    maxReconnects: 2,
    schedule: (fn, delay) => { const id = scheduled.length + 1; scheduled.push({ id, fn, delay }); return id },
    cancelSchedule: id => cancelled.push(id),
    onMalformedEvent: error => errors.push(error),
    onConnectionState: state => states.push(state)
  })

  await controller.start()
  await scheduled[0].fn()
  await scheduled[1].fn()
  assert.equal(errors.length, 3)
  assert.deepEqual(scheduled.map(item => item.delay), [500, 1000])
  assert.equal(states.at(-1), 'disconnected')
  controller.stop()
  assert.ok(cancelled.length >= 1)
  assert.equal(controller.isStopped(), true)
})

test('detail helpers preserve evidence truthfulness, honest nulls, safe links, and backend percentages', () => {
  assert.deepEqual(formatCost({ min: 20, max: 40, evidenceType: 'API_FACT', estimated: false }), {
    amount: '¥20 - ¥40', label: 'API/工具事实', estimated: false
  })
  assert.deepEqual(formatCost({ min: 80, max: 80, evidenceType: 'DOCUMENT', estimated: false }), {
    amount: '¥80', label: '文档事实', estimated: false
  })
  assert.equal(formatCost({ min: null, max: null, evidenceType: 'AI_ESTIMATE', estimated: true }).amount, '费用暂无')
  assert.equal(formatCost({ min: 10, max: 20, evidenceType: 'AI_ESTIMATE', estimated: true }).label, 'AI 估算')
  assert.equal(safeHttpUrl('https://example.org/source'), 'https://example.org/source')
  assert.equal(safeHttpUrl('javascript:alert(1)'), null)
  assert.equal(safeHttpUrl('/chunks/88'), null)
  assert.equal(formatMetric({ numerator: 0, denominator: 0, percentage: 37.5 }).percentage, '37.5%')
  assert.equal(formatMetric({ numerator: 0, denominator: 0, percentage: null }).percentage, '暂无')
})

test('version selection uses explicit version DTOs and identifies current version', () => {
  const detail = { currentVersion: 2, days: [{ date: 'current' }], warnings: [], metrics: null, estimatedTotalCost: 90 }
  const versions = [
    { versionNumber: 1, days: [{ date: 'old' }], warnings: [{ code: 'OLD' }], metrics: {}, estimatedTotalCost: 70 },
    { versionNumber: 2, days: [{ date: 'new' }], warnings: [], metrics: {}, estimatedTotalCost: 90 }
  ]
  assert.deepEqual(selectPlanVersion(detail, versions, 1), { ...versions[0], isCurrent: false })
  assert.deepEqual(selectPlanVersion(detail, versions, 2), { ...versions[1], isCurrent: true })
  assert.equal(selectPlanVersion(detail, versions, 99), null)
})

test('unknown warning severity is not presented as WARNING', () => {
  assert.deepEqual(warningPresentation('ERROR'), { label: '错误', className: 'is-error' })
  assert.deepEqual(warningPresentation('WARNING'), { label: '警告', className: 'is-warning' })
  assert.deepEqual(warningPresentation('NOTICE'), { label: '未知级别', className: 'is-unknown' })
  assert.deepEqual(warningPresentation(null), { label: '未知级别', className: 'is-unknown' })
})

test('API wrappers, routes, and Vue views expose the required safe integration surface', () => {
  const planningApi = read('src/api/planning.js')
  const preferencesApi = read('src/api/preferences.js')
  const router = read('src/router/index.js')
  const planner = read('src/views/Planner.vue')
  const detail = read('src/views/PlanDetail.vue')
  const progress = read('src/components/planning/PlanningProgress.vue')
  const timeline = read('src/components/planning/DayTimeline.vue')
  const evidence = read('src/components/planning/EvidenceList.vue')
  const allNewVue = `${planner}\n${detail}\n${progress}\n${timeline}\n${evidence}`

  for (const endpoint of [
    '/api/planning-tasks', '/events', '/api/plans/', '/versions'
  ]) assert.match(planningApi, new RegExp(endpoint.replaceAll('/', '\\/')))
  assert.match(preferencesApi, /\/api\/user\/travel-preferences/)
  assert.match(planningApi, /Authorization/)
  assert.match(planningApi, /Last-Event-ID/)
  assert.doesNotMatch(planningApi, /EventSource|[?&]token=/)
  assert.match(router, /path:\s*['"]\/planner['"]/)
  assert.match(router, /path:\s*['"]\/plans\/:id['"]/)
  assert.match(router, /path:\s*['"]\/suggest['"][\s\S]*redirect:[\s\S]*planner/)
  assert.match(router, /path:\s*['"]\/routePlan['"]/)
  assert.match(progress, /aria-live/)
  assert.match(progress, /terminalConfirmed/)
  assert.match(planner, /:terminal-confirmed="task\.terminalConfirmed === true"/)
  assert.match(planner, /@submit\.prevent="submitPlan"/)
  assert.match(detail, /watch:\s*\{[\s\S]*planId/)
  assert.match(detail, /loadSequence/)
  assert.match(detail, /暂无可靠天气事实/)
  assert.match(timeline, /暂无路线事实/)
  assert.match(evidence, /无可展示证据/)
  assert.doesNotMatch(allNewVue, /\.sync|\sslot=|v-html|全天开放/)
})
