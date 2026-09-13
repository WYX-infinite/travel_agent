<template>
  <main class="page-shell">
    <header class="page-heading">
      <div><p class="eyebrow">ADMIN KNOWLEDGE</p><h1>知识库管理</h1><p>上传带来源元数据的文档，检查切片，并控制正式检索索引中的启用状态。</p></div>
      <el-button :loading="reindexing" :disabled="reindexing" @click="reindex">重建正式索引</el-button>
    </header>

    <section class="upload-card">
      <h2>上传知识文档</h2>
      <el-form label-position="top" @submit.prevent="upload">
        <el-form-item label="文档文件" :error="errors.file">
          <el-upload ref="uploadControl" :auto-upload="false" :limit="1" accept=".pdf,.docx,.md,.txt" :on-change="fileChanged" :on-remove="fileRemoved"><el-button>选择文件</el-button><template #tip><span class="upload-tip">PDF、DOCX、Markdown 或 TXT</span></template></el-upload>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="标题" :error="errors.title"><el-input v-model="uploadForm.title" maxlength="200" /></el-form-item>
          <el-form-item label="适用城市" :error="errors.city"><el-input v-model="uploadForm.city" maxlength="100" /></el-form-item>
          <el-form-item label="发布机构" :error="errors.publisher"><el-input v-model="uploadForm.publisher" maxlength="200" /></el-form-item>
          <el-form-item label="发布日期" :error="errors.publishedAt"><el-date-picker v-model="uploadForm.publishedAt" type="date" value-format="YYYY-MM-DD" class="full" /></el-form-item>
          <el-form-item label="来源地址" :error="errors.sourceUrl"><el-input v-model="uploadForm.sourceUrl" placeholder="https://..." /></el-form-item>
          <el-form-item label="文档类型" :error="errors.documentType"><el-select v-model="uploadForm.documentType" class="full"><el-option v-for="item in documentTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        </div>
        <el-progress v-if="uploading" :percentage="uploadProgress" />
        <el-alert v-if="uploadSummary" type="success" :closable="false" show-icon class="upload-summary">
          <template #title>最近上传：{{ uploadSummary.title }}（文档 #{{ uploadSummary.id }}）</template>
          <p>{{ uploadSummary.originalFilename }} · {{ uploadSummary.contentType }} · {{ formatBytes(uploadSummary.fileSize) }} · {{ statusView(uploadSummary.status).label }}</p>
        </el-alert>
        <el-button native-type="submit" type="primary" :loading="uploading" :disabled="uploading">上传并入库</el-button>
      </el-form>
    </section>

    <section class="list-card">
      <div class="section-heading"><h2>文档列表</h2><el-button :loading="loading" @click="loadDocuments">刷新</el-button></div>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-alert v-else-if="loadError" type="error" :title="loadError.title" :description="loadError.message" :closable="false" show-icon />
      <el-empty v-else-if="!documents.length" description="知识库中还没有文档" />
      <div v-else class="table-wrap">
        <el-table :data="documents" row-key="id">
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="city" label="城市" width="110" />
          <el-table-column prop="publisher" label="发布机构" min-width="140" />
          <el-table-column label="来源" width="90"><template #default="scope"><a v-if="safeSource(scope.row.sourceUrl)" :href="safeSource(scope.row.sourceUrl)" target="_blank" rel="noopener noreferrer">查看</a><span v-else>暂无</span></template></el-table-column>
          <el-table-column prop="publishedAt" label="发布日期" width="120" />
          <el-table-column label="状态" width="105"><template #default="scope"><el-tag :type="statusView(scope.row.status).type">{{ statusView(scope.row.status).label }}</el-tag></template></el-table-column>
          <el-table-column prop="chunkCount" label="切片" width="80" />
          <el-table-column label="操作" width="285" fixed="right">
            <template #default="scope">
              <el-button size="small" @click="preview(scope.row)">预览切片</el-button>
              <el-button v-if="nextStatus(scope.row.status)" size="small" :loading="busy(scope.row.id)" :disabled="busy(scope.row.id)" @click="toggleStatus(scope.row)">{{ scope.row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
              <el-button size="small" type="danger" plain :loading="busy(scope.row.id)" :disabled="!canDelete(scope.row.status) || busy(scope.row.id)" @click="remove(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-drawer v-model="previewOpen" :title="previewDocument ? `${previewDocument.title} · 切片` : '切片预览'" size="min(720px, 94vw)">
      <section class="detail-panel" aria-label="文档详情">
        <el-skeleton v-if="detailLoading" :rows="3" animated />
        <el-alert v-else-if="detailError" type="error" :title="detailError" :closable="false" />
        <dl v-else-if="documentDetail">
          <div><dt>原始文件</dt><dd>{{ documentDetail.originalFilename }}</dd></div>
          <div><dt>内容类型</dt><dd>{{ documentDetail.contentType }}</dd></div>
          <div><dt>文件大小</dt><dd>{{ formatBytes(documentDetail.fileSize) }}</dd></div>
          <div><dt>SHA-256</dt><dd class="digest">{{ documentDetail.sha256 }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(documentDetail.createdAt) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatTime(documentDetail.updatedAt) }}</dd></div>
        </dl>
        <p v-if="documentDetail?.failureReason" class="failure-reason">失败原因：{{ documentDetail.failureReason }}</p>
      </section>
      <el-skeleton v-if="chunksLoading" :rows="7" animated />
      <el-alert v-else-if="chunksError" type="error" :title="chunksError" :closable="false" />
      <el-empty v-else-if="!chunks.items.length" description="没有可预览切片" />
      <ol v-else class="chunk-list"><li v-for="chunk in chunks.items" :key="chunk.id"><strong>切片 {{ chunk.chunkIndex }}</strong><p>{{ chunk.content }}</p></li></ol>
      <el-pagination v-if="chunks.totalElements" layout="prev, pager, next" :page-size="chunks.size" :current-page="chunks.page + 1" :total="chunks.totalElements" @current-change="changeChunkPage" />
    </el-drawer>
  </main>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeDocuments, getKnowledgeDocument, getKnowledgeChunks, uploadKnowledgeDocument, updateKnowledgeDocumentStatus, deleteKnowledgeDocument, reindexKnowledgeDocuments } from '../api/knowledge'
import knowledgeState from '../knowledge/knowledgeState.cjs'

function emptyUpload() { return { file: null, title: '', city: '', publisher: '', sourceUrl: '', publishedAt: '', documentType: '' } }
function emptyChunks() { return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }

export default {
  name: 'KnowledgeAdminPage',
  data() {
    return {
      loading: true, loadError: null, documents: [], busyIds: {}, reindexing: false, listSequence: 0,
      uploadForm: emptyUpload(), errors: {}, uploading: false, uploadProgress: 0, uploadSummary: null,
      previewOpen: false, previewDocument: null, documentDetail: null, detailLoading: false, detailError: '', previewSequence: 0,
      chunks: emptyChunks(), chunksLoading: false, chunksError: '', chunksSequence: 0,
      documentTypes: [{ value: 'OFFICIAL_NOTICE', label: '官方通知' }, { value: 'ATTRACTION_GUIDE', label: '景点指南' }, { value: 'TRANSPORT_GUIDE', label: '交通指南' }, { value: 'MANUAL_CURATION', label: '人工整理' }]
    }
  },
  mounted() { this.loadDocuments() },
  methods: {
    statusView: knowledgeState.statusPresentation,
    nextStatus: knowledgeState.nextDocumentStatus,
    canDelete: knowledgeState.canDeleteDocument,
    busy(id) { return Boolean(this.busyIds[id]) },
    setBusy(id, value) { this.busyIds = { ...this.busyIds, [id]: value } },
    safeSource(value) { try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : null } catch (_) { return null } },
    formatBytes(value) { if (!Number.isFinite(Number(value))) return '大小未知'; const bytes = Number(value); return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB` },
    formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '暂无' },
    fileChanged(file) { this.uploadForm.file = file.raw || null },
    fileRemoved() { this.uploadForm.file = null },
    async loadDocuments() {
      const sequence = ++this.listSequence
      this.loading = true; this.loadError = null
      try { const data = await getKnowledgeDocuments(); if (sequence === this.listSequence) this.documents = Array.isArray(data) ? data : [] }
      catch (error) { if (sequence === this.listSequence) this.loadError = { title: error?.code || '知识库加载失败', message: error?.message || '暂时无法读取知识文档。' } }
      finally { if (sequence === this.listSequence) this.loading = false }
    },
    async upload() {
      if (this.uploading) return
      this.errors = knowledgeState.validateKnowledgeUpload(this.uploadForm)
      if (Object.keys(this.errors).length) return
      const formData = new FormData(); formData.append('file', this.uploadForm.file)
      const metadata = knowledgeState.buildKnowledgeMetadata(this.uploadForm)
      Object.entries(metadata).forEach(([key, value]) => formData.append(key, value))
      this.uploading = true; this.uploadProgress = 0
      try {
        this.uploadSummary = await uploadKnowledgeDocument(formData, event => { if (event.total) this.uploadProgress = Math.round((event.loaded / event.total) * 100) })
        ElMessage.success('文档已上传并完成入库。'); this.uploadForm = emptyUpload(); this.$refs.uploadControl?.clearFiles(); await this.loadDocuments()
      } catch (error) { ElMessage.error(error?.message || '文档上传失败。') }
      finally { this.uploading = false }
    },
    async toggleStatus(document) {
      const status = this.nextStatus(document.status); if (!status || this.busy(document.id)) return
      this.setBusy(document.id, true)
      try {
        const confirmed = await ElMessageBox.confirm(`确定${status === 'ACTIVE' ? '启用' : '停用'}“${document.title}”吗？`, '变更文档状态', { confirmButtonText: '确定', cancelButtonText: '取消' }).then(() => true).catch(() => false)
        if (!confirmed) return
        await updateKnowledgeDocumentStatus(document.id, status); ElMessage.success('文档状态已更新。'); await this.loadDocuments()
      }
      catch (error) { ElMessage.error(error?.message || '状态更新失败。') }
      finally { this.setBusy(document.id, false) }
    },
    async remove(document) {
      if (!this.canDelete(document.status) || this.busy(document.id)) return
      this.setBusy(document.id, true)
      try {
        const confirmed = await ElMessageBox.confirm(`删除“${document.title}”及其全部切片？该操作不可撤销。`, '删除知识文档', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }).then(() => true).catch(() => false)
        if (!confirmed) return
        await deleteKnowledgeDocument(document.id); ElMessage.success('文档及切片已删除。'); await this.loadDocuments()
      }
      catch (error) { ElMessage.error(error?.message || '文档删除失败。') }
      finally { this.setBusy(document.id, false) }
    },
    async reindex() {
      if (this.reindexing) return
      this.reindexing = true
      try {
        const confirmed = await ElMessageBox.confirm('将使用当前所有已启用文档重建正式索引，是否继续？', '重建索引', { confirmButtonText: '重建', cancelButtonText: '取消' }).then(() => true).catch(() => false)
        if (!confirmed) return
        const result = await reindexKnowledgeDocuments(); ElMessage.success(`索引已重建：${result.activeDocumentCount} 份文档，${result.chunkCount} 个切片。`)
      }
      catch (error) { ElMessage.error(error?.message || '索引重建失败。') }
      finally { this.reindexing = false }
    },
    async preview(document) {
      const sequence = ++this.previewSequence
      this.previewDocument = document; this.previewOpen = true; this.documentDetail = null; this.chunks = emptyChunks()
      await Promise.all([this.loadDetail(document.id, sequence), this.loadChunks(0, document.id, sequence)])
    },
    async loadDetail(documentId, previewSequence) {
      this.detailLoading = true; this.detailError = ''
      try { const detail = await getKnowledgeDocument(documentId); if (previewSequence === this.previewSequence && documentId === this.previewDocument?.id) this.documentDetail = detail }
      catch (error) { if (previewSequence === this.previewSequence) this.detailError = error?.message || '文档详情加载失败。' }
      finally { if (previewSequence === this.previewSequence) this.detailLoading = false }
    },
    async loadChunks(page, documentId = this.previewDocument?.id, previewSequence = this.previewSequence) {
      if (!documentId) return
      const chunksSequence = ++this.chunksSequence
      this.chunksLoading = true; this.chunksError = ''
      try { const chunks = await getKnowledgeChunks(documentId, page, 20); if (chunksSequence === this.chunksSequence && previewSequence === this.previewSequence && documentId === this.previewDocument?.id) this.chunks = chunks }
      catch (error) { if (chunksSequence === this.chunksSequence && previewSequence === this.previewSequence) { this.chunksError = error?.message || '切片加载失败。'; this.chunks = emptyChunks() } }
      finally { if (chunksSequence === this.chunksSequence && previewSequence === this.previewSequence) this.chunksLoading = false }
    },
    changeChunkPage(page) { this.loadChunks(page - 1) }
  }
}
</script>

<style scoped>
.page-shell { min-height: 100vh; padding: 92px 24px 48px; background: #f3f6f8; color: #263746; text-align: left; }.page-heading, .upload-card, .list-card { max-width: 1180px; margin: 0 auto 22px; }.page-heading { display: flex; justify-content: space-between; gap: 20px; align-items: end; }.page-heading h1 { margin: 6px 0; font-size: clamp(32px, 5vw, 50px); }.page-heading p { color: #5d717d; }.eyebrow { font-size: 12px; font-weight: 800; color: #2768a9 !important; letter-spacing: .1em; }.upload-card, .list-card { padding: 24px; border: 1px solid #dde5e9; border-radius: 14px; background: #fff; box-sizing: border-box; }.upload-card h2 { margin-top: 0; }.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }.full { width: 100%; }.upload-tip { margin-left: 10px; color: #748690; }.upload-summary { margin: 14px 0; }.upload-summary p { margin: 8px 0 0; }.section-heading { display: flex; align-items: center; justify-content: space-between; }.table-wrap { overflow-x: auto; }.detail-panel { margin-bottom: 18px; }.detail-panel dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }.detail-panel dl div { padding: 10px; border-radius: 8px; background: #f2f6f8; }.detail-panel dt { color: #6b7e89; font-size: 12px; }.detail-panel dd { margin: 5px 0 0; }.digest { overflow-wrap: anywhere; font-family: monospace; }.failure-reason { padding: 10px; border-radius: 8px; background: #fff2f2; white-space: pre-wrap; overflow-wrap: anywhere; }.chunk-list { display: grid; gap: 12px; padding-left: 24px; }.chunk-list li { padding: 14px; border-radius: 9px; background: #f2f6f8; }.chunk-list p { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
@media (max-width: 700px) { .page-shell { padding: 82px 12px 32px; }.page-heading { align-items: flex-start; flex-direction: column; }.form-grid { grid-template-columns: 1fr; }.upload-card, .list-card { padding: 16px; } }
</style>
