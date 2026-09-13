const VALID_PACES = new Set(['RELAXED', 'MODERATE', 'COMPACT'])
const VALID_MODES = new Set(['WALKING', 'TRANSIT', 'TAXI', 'DRIVING'])
const VALID_INTERESTS = new Set(['FOOD', 'HISTORY', 'NATURE', 'PHOTOGRAPHY', 'SHOPPING'])

function emptyPreferenceForm() {
  return { interests: [], pace: '', travelMode: '', accommodationPreference: '' }
}

function validatePreferenceForm(form = {}) {
  const errors = {}
  if (!Array.isArray(form.interests) || form.interests.some(value => !VALID_INTERESTS.has(value))) errors.interests = '兴趣偏好包含未知值。'
  if (form.pace && !VALID_PACES.has(form.pace)) errors.pace = '请选择有效的行程节奏。'
  if (form.travelMode && !VALID_MODES.has(form.travelMode)) errors.travelMode = '请选择有效的出行方式。'
  const original = typeof form.accommodationPreference === 'string' ? form.accommodationPreference : ''
  const accommodation = original.trim()
  if (original.length > 0 && !accommodation) errors.accommodationPreference = '住宿偏好不能只包含空白。'
  else if (accommodation.length > 255) errors.accommodationPreference = '住宿偏好不能超过 255 个字符。'
  return errors
}

function buildPreferencePayload(form = {}) {
  return {
    interests: Array.isArray(form.interests) ? [...form.interests] : [],
    pace: form.pace || null,
    travelMode: form.travelMode || null,
    accommodationPreference: typeof form.accommodationPreference === 'string' && form.accommodationPreference.trim()
      ? form.accommodationPreference.trim()
      : null
  }
}

module.exports = { emptyPreferenceForm, validatePreferenceForm, buildPreferencePayload }
