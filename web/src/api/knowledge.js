import { http } from '../plugins/axios'

const endpoint = '/api/admin/knowledge-documents'
export function getKnowledgeDocuments() { return http.get(endpoint) }
export function getKnowledgeDocument(id) { return http.get(`${endpoint}/${encodeURIComponent(id)}`) }
export function getKnowledgeChunks(id, page = 0, size = 20) {
  return http.get(`${endpoint}/${encodeURIComponent(id)}/chunks`, { params: { page, size } })
}
export function uploadKnowledgeDocument(formData, onUploadProgress) {
  return http.post(endpoint, formData, { onUploadProgress })
}
export function updateKnowledgeDocumentStatus(id, status) {
  return http.patch(`${endpoint}/${encodeURIComponent(id)}/status`, { status })
}
export function deleteKnowledgeDocument(id) { return http.delete(`${endpoint}/${encodeURIComponent(id)}`) }
export function reindexKnowledgeDocuments() { return http.post(`${endpoint}/reindex`) }
