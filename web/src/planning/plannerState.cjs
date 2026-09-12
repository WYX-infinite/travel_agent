'use strict'

const INTERESTS = Object.freeze(['FOOD', 'HISTORY', 'NATURE', 'PHOTOGRAPHY', 'SHOPPING'])
const PACES = Object.freeze(['RELAXED', 'MODERATE', 'COMPACT'])
const COMPANION_TYPES = Object.freeze(['SOLO', 'COUPLE', 'FAMILY', 'FRIENDS', 'SENIORS'])
const TRAVEL_MODES = Object.freeze(['WALKING', 'TRANSIT', 'TAXI', 'DRIVING'])
const PREFERENCE_FIELDS = Object.freeze(['interests', 'pace', 'travelMode', 'accommodationAddress'])

let localEventSequence = 0

function cloneValue(value) {
  return Array.isArray(value) ? [...value] : value
}

function isEmpty(value) {
  return value == null || value === '' || (Array.isArray(value) && value.length === 0)
}

function createPlannerState(initialForm = {}) {
  const form = {
    destinationCity: '',
    startDate: '',
    endDate: '',
    travelers: 1,
    totalBudget: null,
    companionType: '',
    interests: [],
    pace: '',
    accommodationAddress: '',
    travelMode: '',
    specialRequirements: '',
    useSavedPreferences: true,
    ...initialForm
  }
  form.interests = cloneValue(form.interests || [])
  const fields = Object.fromEntries(PREFERENCE_FIELDS.map(field => [field, {
    dirty: false,
    source: isEmpty(form[field]) ? 'empty' : 'initial',
    prefillValue: null
  }]))
  return { form, fields }
}

function markFieldDirty(state, field) {
  if (!state.fields[field]) return
  state.fields[field].dirty = true
  state.fields[field].source = 'user'
}

function applySavedPreferences(state, preference) {
  if (!state.form.useSavedPreferences || !preference) return state
  const mapped = {
    interests: preference.interests,
    pace: preference.pace,
    travelMode: preference.travelMode,
    accommodationAddress: preference.accommodationPreference
  }
  for (const field of PREFERENCE_FIELDS) {
    const metadata = state.fields[field]
    const value = mapped[field]
    if (!metadata.dirty && isEmpty(state.form[field]) && !isEmpty(value)) {
      state.form[field] = cloneValue(value)
      metadata.source = 'preference'
      metadata.prefillValue = cloneValue(value)
    }
  }
  return state
}

function emptyValue(field) {
  return field === 'interests' ? [] : ''
}

function disableSavedPreferences(state) {
  state.form.useSavedPreferences = false
  for (const field of PREFERENCE_FIELDS) {
    const metadata = state.fields[field]
    if (metadata.source === 'preference' && !metadata.dirty) {
      state.form[field] = emptyValue(field)
      metadata.source = 'empty'
      metadata.prefillValue = null
    } else if (metadata.source === 'user' && Array.isArray(state.form[field])
        && Array.isArray(metadata.prefillValue)) {
      const prefilled = new Set(metadata.prefillValue)
      state.form[field] = state.form[field].filter(value => !prefilled.has(value))
      metadata.source = state.form[field].length ? 'user' : 'empty'
      metadata.prefillValue = null
    }
  }
  return state
}

function enableSavedPreferences(state) {
  state.form.useSavedPreferences = true
  return state
}

function buildPlanningPayload(form, fields = null) {
  const payload = {
    destinationCity: stringValue(form.destinationCity),
    startDate: form.startDate || '',
    endDate: form.endDate || '',
    travelers: numberValue(form.travelers),
    totalBudget: numberValue(form.totalBudget),
    companionType: form.companionType || '',
    interests: cloneValue(form.interests || []),
    pace: form.pace || '',
    accommodationAddress: stringValue(form.accommodationAddress),
    travelMode: form.travelMode || '',
    specialRequirements: stringValue(form.specialRequirements),
    useSavedPreferences: form.useSavedPreferences !== false
  }
  if (fields && payload.useSavedPreferences) {
    for (const field of PREFERENCE_FIELDS) {
      if (isEmpty(payload[field]) && fields[field] && !fields[field].dirty) payload[field] = null
    }
  }
  return payload
}

function stringValue(value) {
  return typeof value === 'string' ? value.trim() : (value == null ? '' : String(value))
}

function numberValue(value) {
  if (value === '' || value == null) return value
  return Number(value)
}

function validatePlanningPayload(form) {
  const errors = {}
  if (!stringValue(form.destinationCity)) errors.destinationCity = '请输入目的城市'
  if (!form.startDate) errors.startDate = '请选择开始日期'
  if (!form.endDate) errors.endDate = '请选择结束日期'
  if (form.startDate && form.endDate) {
    const start = new Date(`${form.startDate}T00:00:00Z`)
    const end = new Date(`${form.endDate}T00:00:00Z`)
    if (end < start) errors.dateRange = '结束日期不能早于开始日期'
    else if ((end - start) / 86400000 >= 5) errors.dateRange = '行程最多为 5 天'
  }
  if (!Number.isInteger(Number(form.travelers)) || Number(form.travelers) <= 0) errors.travelers = '人数必须为正整数'
  if (!Number.isFinite(Number(form.totalBudget)) || Number(form.totalBudget) <= 0) errors.totalBudget = '总预算必须大于 0'
  if (!COMPANION_TYPES.includes(form.companionType)) errors.companionType = '请选择合法同行类型'
  if (!Array.isArray(form.interests) || form.interests.some(value => !INTERESTS.includes(value))) errors.interests = '包含不支持的兴趣类型'
  if (!PACES.includes(form.pace)) errors.pace = '请选择合法旅行节奏'
  if (!TRAVEL_MODES.includes(form.travelMode)) errors.travelMode = '请选择合法交通方式'
  return errors
}

function preferenceFailureNotice() {
  return {
    type: 'warning',
    blocking: false,
    message: '已保存偏好暂时无法读取，你仍可手动填写并提交规划。'
  }
}

function classifyTaskStatus(task = {}) {
  switch (task.status) {
    case 'PENDING':
    case 'RUNNING':
      return { kind: 'active', navigate: false }
    case 'COMPLETED':
      return task.planId == null
        ? { kind: 'invalid-completion', navigate: false }
        : { kind: 'completed', navigate: true, planId: task.planId }
    case 'NEEDS_ADJUSTMENT':
      return { kind: 'needs-adjustment', navigate: false }
    case 'FAILED':
      return {
        kind: 'failed',
        navigate: false,
        errorCode: task.errorCode || null,
        errorMessage: task.errorMessage || null
      }
    default:
      return { kind: 'unknown', navigate: false }
  }
}

function shouldEmitTerminal(task = {}, terminalConfirmed = false) {
  return terminalConfirmed === true
    && ['COMPLETED', 'NEEDS_ADJUSTMENT', 'FAILED'].includes(task.status)
}

function terminalConfirmationForUpdate(task = {}, source = '') {
  return ['task-status', 'planning-status'].includes(source)
    && ['COMPLETED', 'NEEDS_ADJUSTMENT', 'FAILED'].includes(task.status)
}

function mergeTaskEvent(events, event) {
  const data = event && event.data ? event.data : {}
  const rawEventId = data.eventId != null ? data.eventId : (event && event.event === 'planning-progress' ? event.id : null)
  const eventId = rawEventId == null || rawEventId === '' || !Number.isFinite(Number(rawEventId))
    ? undefined
    : Number(rawEventId)
  if (eventId !== undefined && events.some(existing => existing.eventId === eventId)) return events
  if (eventId === undefined && event && event.event === 'planning-status') {
    const duplicate = events.some(existing => existing.event === 'planning-status'
      && existing.status === data.status
      && existing.occurredAt === data.occurredAt)
    if (duplicate) return events
  }
  localEventSequence += 1
  const normalized = {
    ...data,
    event: event.event || 'message',
    key: eventId === undefined ? `status-${localEventSequence}` : `trace-${eventId}`
  }
  if (eventId !== undefined) normalized.eventId = eventId
  return [...events, normalized]
}

function money(value) {
  return `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

function formatCost(cost) {
  const value = cost || {}
  let amount = '费用暂无'
  if (value.min != null && value.max != null) {
    amount = Number(value.min) === Number(value.max)
      ? money(value.min)
      : `${money(value.min)} - ${money(value.max)}`
  } else if (value.min != null) amount = `${money(value.min)} 起`
  else if (value.max != null) amount = `不高于 ${money(value.max)}`

  const isEstimate = value.estimated === true || value.evidenceType === 'AI_ESTIMATE'
  const label = isEstimate
    ? 'AI 估算'
    : value.evidenceType === 'API_FACT'
      ? 'API/工具事实'
      : value.evidenceType === 'DOCUMENT'
        ? '文档事实'
        : '来源未说明'
  return { amount, label, estimated: isEstimate }
}

function safeHttpUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null
  } catch (_) {
    return null
  }
}

function formatMetric(metric) {
  if (!metric) return { count: '暂无', percentage: '暂无' }
  const count = `${metric.numerator ?? 0} / ${metric.denominator ?? 0}`
  let percentage = metric.percentage
  if (percentage == null && Number(metric.denominator) > 0) {
    percentage = Number(metric.numerator || 0) / Number(metric.denominator) * 100
  }
  return {
    count,
    percentage: percentage == null ? '暂无' : `${Number(percentage).toLocaleString('zh-CN', { maximumFractionDigits: 1 })}%`
  }
}

function selectPlanVersion(detail, versions, versionNumber) {
  const numericVersion = Number(versionNumber)
  const version = (versions || []).find(candidate => Number(candidate.versionNumber) === numericVersion)
  if (!version) return null
  return { ...version, isCurrent: Number(detail.currentVersion) === numericVersion }
}

function warningPresentation(severity) {
  if (severity === 'ERROR') return { label: '错误', className: 'is-error' }
  if (severity === 'WARNING') return { label: '警告', className: 'is-warning' }
  return { label: '未知级别', className: 'is-unknown' }
}

module.exports = {
  INTERESTS,
  PACES,
  COMPANION_TYPES,
  TRAVEL_MODES,
  applySavedPreferences,
  buildPlanningPayload,
  classifyTaskStatus,
  createPlannerState,
  disableSavedPreferences,
  enableSavedPreferences,
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
}
