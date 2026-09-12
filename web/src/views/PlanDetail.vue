<template>
  <main class="detail-page">
    <el-skeleton v-if="loading" :rows="8" animated class="content-shell" />
    <el-alert
      v-else-if="loadError"
      type="error"
      :closable="false"
      :title="loadError.title"
      :description="loadError.message"
      show-icon
      class="content-shell"
    />

    <template v-else-if="detail">
      <header class="summary-card content-shell">
        <div>
          <p class="eyebrow">有来源边界的行程详情</p>
          <h1>{{ detail.destinationCity || '目的城市未提供' }}</h1>
          <p>{{ detail.startDate || '开始日期未提供' }} 至 {{ detail.endDate || '结束日期未提供' }}</p>
        </div>
        <dl class="summary-facts">
          <div><dt>状态</dt><dd>{{ detail.status || '未知' }}</dd></div>
          <div><dt>当前版本</dt><dd>v{{ detail.currentVersion ?? '未知' }}</dd></div>
          <div><dt>预计总费用</dt><dd>{{ totalCostText }}</dd></div>
        </dl>
        <p class="summary-text">{{ detail.summary || '后端未提供行程摘要' }}</p>
      </header>

      <section class="content-shell version-panel" aria-label="行程版本">
        <div>
          <h2>查看版本</h2>
          <p>版本内容来自显式 DTO，不展示内部 JSON。</p>
        </div>
        <el-select v-model="selectedVersionNumber" class="version-select" aria-label="选择行程版本">
          <el-option
            v-for="version in versionOptions"
            :key="version.versionId || version.versionNumber"
            :label="versionLabel(version)"
            :value="version.versionNumber"
          />
        </el-select>
      </section>

      <section class="content-shell preference-panel">
        <h2>实际采用的已保存偏好</h2>
        <p v-if="!detail.appliedPreferences?.enabled">本次规划未启用已保存偏好。</p>
        <p v-else-if="!adoptedPreferences.length">已启用偏好读取，但后端未声明实际采用字段。</p>
        <dl v-else class="preference-grid">
          <div v-for="item in adoptedPreferences" :key="item.field">
            <dt>{{ item.label }}</dt><dd>{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <section class="content-shell weather-panel">
        <h2>天气事实</h2>
        <p v-if="!detail.weather">暂无可靠天气事实</p>
        <dl v-else-if="weatherFacts.length" class="weather-grid">
          <div v-for="fact in weatherFacts" :key="fact.label"><dt>{{ fact.label }}</dt><dd>{{ fact.value }}</dd></div>
        </dl>
        <p v-else>天气事实已返回，但当前版本未提供可展示字段。</p>
      </section>

      <section class="content-shell itinerary-panel">
        <div class="section-heading">
          <h2>天级行程</h2>
          <el-tag v-if="selectedVersion?.isCurrent" type="success">当前版本</el-tag>
          <el-tag v-else type="info">历史版本</el-tag>
        </div>
        <DayTimeline :days="selectedVersion?.days || []" />
      </section>

      <section class="content-shell warnings-panel">
        <h2>校验警告与冲突</h2>
        <p v-if="!selectedWarnings.length">当前版本没有后端返回的警告。</p>
        <div v-else class="warning-list">
          <article v-for="warning in selectedWarnings" :key="warningKey(warning)" :class="['warning-item', warningView(warning.severity).className]">
            <div class="warning-heading">
              <strong>{{ warningView(warning.severity).label }} · {{ warning.code || 'UNKNOWN' }}</strong>
              <span v-if="warning.date">{{ warning.date }}</span>
            </div>
            <p>{{ warning.message || '未提供安全问题说明' }}</p>
            <p v-if="warning.knownValue"><b>已知值：</b>{{ warning.knownValue }}</p>
            <p v-if="warning.requiredAdjustment"><b>需要调整：</b>{{ warning.requiredAdjustment }}</p>
          </article>
        </div>
      </section>

      <section class="content-shell metrics-panel">
        <h2>客观指标</h2>
        <p v-if="!selectedVersion?.metrics">暂无可展示的客观指标</p>
        <div v-else class="metric-grid">
          <article v-for="metric in metrics" :key="metric.key">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.view.percentage }}</strong>
            <small>{{ metric.view.count }}</small>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>

<script>
import DayTimeline from '../components/planning/DayTimeline.vue'
import { getPlan, getPlanVersions, isAuthenticationError } from '../api/planning'
import plannerState from '../planning/plannerState.cjs'

const { formatMetric, selectPlanVersion, warningPresentation } = plannerState

const PREFERENCE_LABELS = {
  interests: '兴趣',
  pace: '旅行节奏',
  travelMode: '交通方式',
  accommodationPreference: '住宿位置偏好'
}

export default {
  name: 'PlanDetailPage',
  components: { DayTimeline },
  props: {
    id: { type: [String, Number], required: false }
  },
  data() {
    return {
      loading: true,
      loadError: null,
      detail: null,
      versions: [],
      selectedVersionNumber: null,
      loadSequence: 0
    }
  },
  computed: {
    planId() {
      return this.id || this.$route.params.id
    },
    versionOptions() {
      if (this.versions.length) return this.versions
      return Array.isArray(this.detail?.versions) ? this.detail.versions : []
    },
    selectedVersion() {
      const selected = selectPlanVersion(this.detail, this.versions, this.selectedVersionNumber)
      if (selected) return selected
      if (Number(this.detail.currentVersion) === Number(this.selectedVersionNumber)) {
        return {
          versionNumber: this.detail.currentVersion,
          days: this.detail.days || [],
          warnings: this.detail.warnings || [],
          metrics: this.detail.metrics || null,
          estimatedTotalCost: this.detail.estimatedTotalCost,
          isCurrent: true
        }
      }
      return null
    },
    selectedWarnings() {
      return Array.isArray(this.selectedVersion?.warnings) ? this.selectedVersion.warnings : []
    },
    totalCostText() {
      const value = this.selectedVersion?.estimatedTotalCost ?? this.detail?.estimatedTotalCost
      return value == null ? '总费用暂无' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
    },
    adoptedPreferences() {
      const preference = this.detail?.appliedPreferences
      const fields = Array.isArray(preference?.adoptedFields) ? preference.adoptedFields : []
      return fields.map(field => {
        const value = preference[field]
        return {
          field,
          label: PREFERENCE_LABELS[field] || field,
          value: Array.isArray(value) ? value.join('、') : (value ?? '后端未提供采用值')
        }
      })
    },
    weatherFacts() {
      const weather = this.detail?.weather
      if (!weather || typeof weather !== 'object') return []
      const candidates = [
        ['date', '日期'],
        ['condition', '天气'],
        ['temperatureMin', '最低温度'],
        ['temperatureMax', '最高温度'],
        ['advice', '已验证建议']
      ]
      return candidates
        .filter(([key]) => weather[key] != null && weather[key] !== '')
        .map(([key, label]) => ({ label, value: weather[key] }))
    },
    metrics() {
      const source = this.selectedVersion?.metrics || {}
      return [
        { key: 'sourceCoverage', label: '来源覆盖率' },
        { key: 'routeCoverage', label: '路线覆盖率' },
        { key: 'budgetCompleteness', label: '预算完整率' },
        { key: 'rulePassRate', label: '规则通过率' }
      ].map(item => ({ ...item, view: formatMetric(source[item.key]) }))
    }
  },
  async mounted() {
    await this.loadPlan()
  },
  watch: {
    planId(newValue, oldValue) {
      if (oldValue != null && newValue !== oldValue) this.loadPlan()
    }
  },
  methods: {
    warningView: warningPresentation,
    async loadPlan() {
      const requestSequence = ++this.loadSequence
      const requestedPlanId = this.planId
      this.loading = true
      this.loadError = null
      try {
        const [detail, versions] = await Promise.all([
          getPlan(requestedPlanId),
          getPlanVersions(requestedPlanId)
        ])
        if (requestSequence !== this.loadSequence || String(requestedPlanId) !== String(this.planId)) return
        this.detail = detail
        this.versions = Array.isArray(versions) ? versions : []
        this.selectedVersionNumber = detail.currentVersion
      } catch (error) {
        if (requestSequence !== this.loadSequence || String(requestedPlanId) !== String(this.planId)) return
        if (isAuthenticationError(error)) {
          this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
          return
        }
        this.loadError = {
          title: error?.code || '行程加载失败',
          message: error?.message || '无法读取行程详情，请稍后重试。'
        }
      } finally {
        if (requestSequence === this.loadSequence) this.loading = false
      }
    },
    versionLabel(version) {
      const current = Number(version.versionNumber) === Number(this.detail.currentVersion)
      return `版本 ${version.versionNumber}${current ? '（当前）' : ''}`
    },
    warningKey(warning) {
      return `${warning.code || 'unknown'}-${warning.date || 'none'}-${warning.itemIndex ?? 'none'}-${warning.poiId || 'none'}`
    }
  }
}
</script>

<style scoped>
.detail-page { min-height: 100vh; padding: 92px 24px 48px; background: #f3f6f8; color: #263746; }
.content-shell { max-width: 1120px; margin: 0 auto 22px; box-sizing: border-box; }
.summary-card, .version-panel, .preference-panel, .weather-panel, .itinerary-panel, .warnings-panel, .metrics-panel { padding: 24px; border: 1px solid #dfe5e9; border-radius: 14px; background: #fff; }
.summary-card { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(360px, 1.2fr); gap: 22px; }
.summary-card h1 { margin: 5px 0 8px; font-size: clamp(32px, 5vw, 54px); }
.eyebrow { margin: 0; color: #2768a9; font-weight: 700; font-size: 13px; letter-spacing: .08em; }
.summary-facts, .preference-grid, .weather-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 0; }
.summary-facts div, .preference-grid div, .weather-grid div { padding: 13px; border-radius: 9px; background: #f2f6f8; }
dt { color: #5c6f7c; font-size: 13px; }
dd { margin: 6px 0 0; font-weight: 650; }
.summary-text { grid-column: 1 / -1; margin: 0; line-height: 1.7; color: #4c606e; }
.version-panel, .section-heading, .warning-heading { display: flex; justify-content: space-between; align-items: center; gap: 18px; }
.version-panel h2, section h2 { margin-top: 0; }
.version-panel p { margin-bottom: 0; color: #5c6f7c; }
.version-select { width: 220px; }
.preference-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.warning-list { display: grid; gap: 12px; }
.warning-item { padding: 16px; border: 1px solid; border-radius: 10px; }
.warning-item p { margin: 8px 0 0; line-height: 1.6; }
.is-error { border-color: #d65b5b; background: #fff2f2; }
.is-warning { border-color: #d69a36; background: #fff8eb; }
.is-unknown { border-color: #7b8790; background: #f3f5f6; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.metric-grid article { display: flex; flex-direction: column; gap: 8px; padding: 16px; border-radius: 10px; background: #edf4f7; }
.metric-grid strong { font-size: 24px; }
.metric-grid small { color: #5c6f7c; }
@media (max-width: 760px) {
  .detail-page { padding: 78px 12px 30px; }
  .summary-card { grid-template-columns: 1fr; }
  .summary-facts, .preference-grid, .weather-grid, .metric-grid { grid-template-columns: 1fr 1fr; }
  .version-panel { align-items: flex-start; flex-direction: column; }
  .version-select { width: 100%; }
}
@media (max-width: 430px) {
  .summary-facts, .preference-grid, .weather-grid, .metric-grid { grid-template-columns: 1fr; }
}
</style>
