const DOCUMENT_TYPES = new Set(['OFFICIAL_NOTICE', 'ATTRACTION_GUIDE', 'TRANSPORT_GUIDE', 'MANUAL_CURATION'])
const PRESENTATIONS = {
  ACTIVE: { label: '已启用', type: 'success' },
  DISABLED: { label: '已停用', type: 'info' },
  UPLOADED: { label: '已上传', type: 'info' },
  INDEXING: { label: '索引中', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' }
}

function validateKnowledgeUpload(form = {}) {
  const errors = {}
  if (!form.file) errors.file = '请选择知识文档。'
  for (const field of ['title', 'city', 'publisher', 'publishedAt']) {
    if (typeof form[field] !== 'string' || !form[field].trim()) errors[field] = '此项为必填项。'
  }
  try {
    const url = new URL(form.sourceUrl)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) errors.sourceUrl = '来源地址必须是无用户信息的 HTTP 或 HTTPS 链接。'
  } catch (_) {
    errors.sourceUrl = '请输入有效的来源地址。'
  }
  if (!DOCUMENT_TYPES.has(form.documentType)) errors.documentType = '请选择文档类型。'
  return errors
}

function buildKnowledgeMetadata(form = {}) {
  return {
    title: form.title.trim(),
    city: form.city.trim(),
    publisher: form.publisher.trim(),
    sourceUrl: form.sourceUrl.trim(),
    publishedAt: form.publishedAt,
    documentType: form.documentType
  }
}

function statusPresentation(status) {
  return PRESENTATIONS[status] || { label: '未知状态', type: 'info' }
}

function nextDocumentStatus(status) {
  if (status === 'ACTIVE') return 'DISABLED'
  if (status === 'DISABLED') return 'ACTIVE'
  return null
}

function canDeleteDocument(status) {
  return status === 'DISABLED' || status === 'FAILED'
}

module.exports = { validateKnowledgeUpload, buildKnowledgeMetadata, statusPresentation, nextDocumentStatus, canDeleteDocument }
