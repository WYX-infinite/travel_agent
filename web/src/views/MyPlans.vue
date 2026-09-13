<template>
  <main class="page-shell">
    <header class="page-heading">
      <div><p class="eyebrow">PLAN HISTORY</p><h1>我的行程</h1><p>查看已保存行程、进入详情，或基于原约束重新规划。</p></div>
      <el-button type="primary" @click="$router.push('/planner')">创建新规划</el-button>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated class="card" />
    <el-alert v-else-if="error" type="error" :title="error.title" :description="error.message" :closable="false" show-icon class="card" />
    <el-empty v-else-if="!plans.length" description="还没有保存的行程"><el-button type="primary" @click="$router.push('/planner')">开始规划</el-button></el-empty>
    <section v-else class="plan-grid" aria-label="历史行程列表">
      <article v-for="plan in plans" :key="plan.planId" class="plan-card">
        <div class="plan-title"><div><h2>{{ plan.destinationCity }}</h2><p>{{ plan.startDate }} 至 {{ plan.endDate }}</p></div><el-tag>{{ statusLabel(plan.status) }}</el-tag></div>
        <dl>
          <div><dt>当前版本</dt><dd>v{{ plan.currentVersion }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(plan.createdAt) }}</dd></div>
          <div><dt>最近更新</dt><dd>{{ formatTime(plan.updatedAt) }}</dd></div>
        </dl>
        <div class="actions">
          <el-button @click="$router.push(`/plans/${plan.planId}`)">查看详情</el-button>
          <el-button @click="openComparison(plan)">版本比较</el-button>
          <el-button type="primary" :loading="replanningId === plan.planId" :disabled="replanningId !== null" @click="replan(plan)">重新规划</el-button>
        </div>
      </article>
    </section>

    <el-dialog v-model="compareOpen" title="版本比较" width="min(760px, 94vw)">
      <el-skeleton v-if="versionsLoading" :rows="5" animated />
      <el-empty v-else-if="versions.length < 2" description="至少需要两个版本才能比较" />
      <template v-else>
        <div class="version-controls">
          <el-select v-model="beforeNumber" aria-label="较早版本"><el-option v-for="item in versions" :key="`b-${item.versionNumber}`" :label="versionLabel(item)" :value="item.versionNumber" /></el-select>
          <span>对比</span>
          <el-select v-model="afterNumber" aria-label="较新版本"><el-option v-for="item in versions" :key="`a-${item.versionNumber}`" :label="versionLabel(item)" :value="item.versionNumber" /></el-select>
        </div>
        <div v-if="comparison" class="comparison-grid">
          <article><span>预计费用变化</span><strong>{{ deltaText(comparison.cost.delta, '¥') }}</strong><small>{{ valueText(comparison.cost.before, '¥') }} → {{ valueText(comparison.cost.after, '¥') }}</small></article>
          <article><span>来源覆盖率变化</span><strong>{{ deltaText(comparison.metrics.sourceCoverage.delta, '%') }}</strong><small>{{ valueText(comparison.metrics.sourceCoverage.before, '%') }} → {{ valueText(comparison.metrics.sourceCoverage.after, '%') }}</small></article>
          <article><span>路线覆盖率变化</span><strong>{{ deltaText(comparison.metrics.routeCoverage.delta, '%') }}</strong><small>{{ valueText(comparison.metrics.routeCoverage.before, '%') }} → {{ valueText(comparison.metrics.routeCoverage.after, '%') }}</small></article>
          <article><span>预算完整率变化</span><strong>{{ deltaText(comparison.metrics.budgetCompleteness.delta, '%') }}</strong><small>{{ valueText(comparison.metrics.budgetCompleteness.before, '%') }} → {{ valueText(comparison.metrics.budgetCompleteness.after, '%') }}</small></article>
          <article><span>规则通过率变化</span><strong>{{ deltaText(comparison.metrics.rulePassRate.delta, '%') }}</strong><small>{{ valueText(comparison.metrics.rulePassRate.before, '%') }} → {{ valueText(comparison.metrics.rulePassRate.after, '%') }}</small></article>
        </div>
        <div v-if="comparison" class="warning-change">
          <h3>警告变化</h3>
          <p>警告数量：{{ comparison.warnings.beforeCount }} → {{ comparison.warnings.afterCount }}</p>
          <p v-if="!comparison.warnings.added.length && !comparison.warnings.removed.length">警告集合没有变化。</p>
          <p v-for="(item, index) in comparison.warnings.added" :key="`add-${index}-${item.code}-${item.severity}`">新增：{{ warningText(item) }}</p>
          <p v-for="(item, index) in comparison.warnings.removed" :key="`remove-${index}-${item.code}-${item.severity}`">移除：{{ warningText(item) }}</p>
        </div>
      </template>
    </el-dialog>
  </main>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPlans, getPlanVersions, replanPlan } from '../api/planning'
import historyState from '../planning/historyState.cjs'

export default {
  name: 'MyPlansPage',
  data() { return { loading: true, error: null, plans: [], replanningId: null, compareOpen: false, selectedPlan: null, versions: [], versionsLoading: false, beforeNumber: null, afterNumber: null, compareSequence: 0 } },
  computed: {
    comparison() {
      const before = this.versions.find(item => item.versionNumber === this.beforeNumber)
      const after = this.versions.find(item => item.versionNumber === this.afterNumber)
      return before && after ? historyState.comparePlanVersions(before, after, this.selectedPlan?.currentVersion) : null
    }
  },
  mounted() { this.loadPlans() },
  methods: {
    async loadPlans() {
      this.loading = true; this.error = null
      try { const result = await getPlans(); this.plans = Array.isArray(result) ? result : [] }
      catch (error) { this.error = { title: error?.code || '行程加载失败', message: error?.message || '暂时无法读取历史行程。' } }
      finally { this.loading = false }
    },
    async replan(plan) {
      if (this.replanningId !== null) return
      this.replanningId = plan.planId
      try {
        const confirmed = await ElMessageBox.confirm(`将基于“${plan.destinationCity}”原始约束创建新的规划任务，是否继续？`, '重新规划', { confirmButtonText: '继续', cancelButtonText: '取消' }).then(() => true).catch(() => false)
        if (!confirmed) return
        const task = await replanPlan(plan.planId)
        if (!task?.taskId) throw new Error('后端未返回规划任务编号。')
        sessionStorage.setItem('activePlanningTaskId', String(task.taskId))
        ElMessage.success('重新规划任务已创建。')
        this.$router.push('/planner')
      } catch (error) { ElMessage.error(error?.message || '重新规划失败。') }
      finally { this.replanningId = null }
    },
    async openComparison(plan) {
      const sequence = ++this.compareSequence
      this.selectedPlan = plan; this.compareOpen = true; this.versionsLoading = true; this.versions = []
      try {
        const result = await getPlanVersions(plan.planId)
        if (sequence !== this.compareSequence || plan.planId !== this.selectedPlan?.planId) return
        this.versions = Array.isArray(result) ? result.slice().sort((a, b) => a.versionNumber - b.versionNumber) : []
        this.beforeNumber = this.versions.at(-2)?.versionNumber ?? null
        this.afterNumber = this.versions.at(-1)?.versionNumber ?? null
      } catch (error) { if (sequence === this.compareSequence) ElMessage.error(error?.message || '版本加载失败。') }
      finally { if (sequence === this.compareSequence) this.versionsLoading = false }
    },
    statusLabel(status) { return ({ ACTIVE: '可用', COMPLETED: '已完成', NEEDS_ADJUSTMENT: '需调整', FAILED: '失败' })[status] || status || '未知' },
    formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '暂无' },
    versionLabel(item) { return `v${item.versionNumber}${item.versionNumber === this.selectedPlan?.currentVersion ? '（当前）' : ''} · ${this.formatTime(item.createdAt)}` },
    warningText(item) { return historyState.describeWarning(item) },
    valueText(value, unit) { return value == null ? '暂无' : `${unit === '¥' ? unit : ''}${value}${unit === '%' ? unit : ''}` },
    deltaText(value, unit) { if (value == null) return '暂无'; const sign = value > 0 ? '+' : ''; return `${unit === '¥' ? unit : ''}${sign}${value}${unit === '%' ? unit : ''}` }
  }
}
</script>

<style scoped>
.page-shell { min-height: 100vh; padding: 92px 24px 48px; background: #f3f6f8; color: #263746; text-align: left; }
.page-heading, .plan-grid, .card, :deep(.el-empty) { max-width: 1120px; margin: 0 auto 24px; }
.page-heading { display: flex; justify-content: space-between; gap: 20px; align-items: end; }
.page-heading h1 { margin: 6px 0; font-size: clamp(32px, 5vw, 50px); }.page-heading p { color: #5d717d; }.eyebrow { font-size: 12px; font-weight: 800; color: #2768a9 !important; letter-spacing: .1em; }
.plan-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }.plan-card { padding: 22px; border: 1px solid #dde5e9; border-radius: 14px; background: #fff; }.plan-title { display: flex; justify-content: space-between; gap: 14px; }.plan-title h2 { margin: 0 0 5px; }.plan-title p { color: #61747f; }
dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }dl div { padding: 10px; background: #f2f6f8; border-radius: 8px; }dt { color: #6c7d87; font-size: 12px; }dd { margin: 5px 0 0; font-weight: 650; font-size: 13px; }.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.version-controls { display: grid; grid-template-columns: 1fr auto 1fr; gap: 12px; align-items: center; }.comparison-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 20px; }.comparison-grid article { display: flex; flex-direction: column; gap: 7px; padding: 14px; background: #eef4f7; border-radius: 9px; }.comparison-grid strong { font-size: 22px; }.comparison-grid small { color: #657984; }.warning-change { margin-top: 20px; }
@media (max-width: 760px) { .page-shell { padding: 82px 12px 32px; }.page-heading { align-items: flex-start; flex-direction: column; }.plan-grid { grid-template-columns: 1fr; }.comparison-grid { grid-template-columns: 1fr; } }
@media (max-width: 390px) { dl { grid-template-columns: 1fr; }.version-controls { grid-template-columns: 1fr; } }
</style>
