<template>
  <section class="evidence-list" aria-label="来源证据">
    <p v-if="!evidenceItems.length" class="empty-evidence">无可展示证据</p>
    <article v-for="item in evidenceItems" :key="evidenceKey(item)" class="evidence-item">
      <div class="evidence-heading">
        <el-tag size="small" effect="plain">{{ evidenceLabel(item.type) }}</el-tag>
        <strong>{{ item.documentTitle || '未提供文档标题' }}</strong>
      </div>
      <p>发布机构：{{ item.publisher || '未提供' }}</p>
      <blockquote v-if="item.quote">{{ item.quote }}</blockquote>
      <p v-else class="muted">未提供可展示的短引用</p>
      <div class="evidence-meta">
        <span>切片编号：{{ item.chunkId ?? '未提供' }}</span>
        <a
          v-if="safeSource(item.sourceUrl)"
          :href="safeSource(item.sourceUrl)"
          target="_blank"
          rel="noopener noreferrer"
        >查看来源</a>
        <span v-else>无安全来源链接</span>
      </div>
    </article>
  </section>
</template>

<script>
import plannerState from '../../planning/plannerState.cjs'

const { safeHttpUrl } = plannerState

export default {
  name: 'EvidenceList',
  props: {
    evidence: { type: Array, default: () => [] }
  },
  computed: {
    evidenceItems() {
      return Array.isArray(this.evidence) ? this.evidence : []
    }
  },
  methods: {
    safeSource: safeHttpUrl,
    evidenceLabel(type) {
      return {
        API_FACT: 'API/工具事实',
        DOCUMENT: '文档事实',
        AI_ESTIMATE: 'AI 估算'
      }[type] || '来源类型未说明'
    },
    evidenceKey(item) {
      return `${item.type || 'unknown'}-${item.chunkId ?? 'none'}-${item.sourceUrl || 'no-url'}`
    }
  }
}
</script>

<style scoped>
.evidence-list { display: grid; gap: 12px; margin-top: 12px; }
.evidence-item { padding: 14px; border: 1px solid #e4e7ed; border-radius: 10px; background: #fafafa; }
.evidence-heading, .evidence-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.evidence-item p { margin: 8px 0; color: #606266; }
blockquote { margin: 10px 0; padding-left: 12px; border-left: 3px solid #909399; color: #303133; }
.evidence-meta { justify-content: space-between; font-size: 13px; color: #606266; }
.evidence-meta a { color: #2768a9; }
.empty-evidence, .muted { color: #606266; }
</style>
