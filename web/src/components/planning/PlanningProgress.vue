<template>
  <section class="planning-progress" aria-live="polite" aria-atomic="false">
    <header class="progress-header">
      <div>
        <p class="eyebrow">异步规划任务</p>
        <h2>{{ statusText }}</h2>
      </div>
      <el-tag :type="statusTagType">{{ connectionText }}</el-tag>
    </header>

    <el-progress :percentage="safePercentage" :status="progressType" />
    <dl class="status-grid">
      <div><dt>当前步骤</dt><dd>{{ stepText }}</dd></div>
      <div><dt>进度</dt><dd>{{ safePercentage }}%</dd></div>
    </dl>
    <p v-if="task.message" class="message">{{ task.message }}</p>
    <el-alert
      v-if="task.status === 'FAILED'"
      type="error"
      :closable="false"
      :title="task.errorCode || '规划失败'"
      :description="task.errorMessage || '规划任务未能完成。'"
      show-icon
    />
    <el-alert
      v-else-if="task.status === 'NEEDS_ADJUSTMENT'"
      type="warning"
      :closable="false"
      title="行程需要调整"
      :description="task.errorMessage || task.message || '当前约束存在冲突，请调整条件后重新提交。'"
      show-icon
    />

    <div class="event-panel">
      <h3>事件时间线</h3>
      <p v-if="!events.length" class="empty-events">尚未收到进度事件</p>
      <ol v-else>
        <li v-for="event in events" :key="event.key">
          <time>{{ event.occurredAt || '时间未提供' }}</time>
          <strong>{{ event.step || event.status || '未知事件' }}</strong>
          <span>{{ event.message || '未提供事件说明' }}</span>
          <small v-if="event.eventId != null">事件 #{{ event.eventId }}</small>
          <small v-else>任务状态快照（不可作为 Trace 游标）</small>
        </li>
      </ol>
    </div>
  </section>
</template>

<script>
import plannerState from '../../planning/plannerState.cjs'

const { shouldEmitTerminal } = plannerState

export default {
  name: 'PlanningProgress',
  emits: ['terminal'],
  props: {
    task: { type: Object, default: () => ({}) },
    events: { type: Array, default: () => [] },
    connectionState: { type: String, default: 'disconnected' },
    terminalConfirmed: { type: Boolean, default: false }
  },
  data() {
    return { emittedTerminal: '' }
  },
  computed: {
    safePercentage() {
      const value = Number(this.task.progressPercent)
      return Number.isFinite(value) ? Math.min(100, Math.max(0, Math.round(value))) : 0
    },
    statusText() {
      return {
        PENDING: '等待开始',
        RUNNING: '正在规划',
        COMPLETED: '规划完成',
        NEEDS_ADJUSTMENT: '需要调整',
        FAILED: '规划失败'
      }[this.task.status] || '未知任务状态'
    },
    stepText() {
      return {
        REQUIREMENT: '校验旅行约束',
        KNOWLEDGE_RETRIEVAL: '检索来源文档',
        TOOL_EXECUTION: '查询旅行工具事实',
        PREPARATION: '准备规划上下文',
        GENERATION: '生成结构化行程',
        VALIDATION: '执行确定性校验',
        REPAIR: '有限次数修正',
        COMPLETION: '保存行程版本'
      }[this.task.currentStep] || (this.task.currentStep ? `未知步骤：${this.task.currentStep}` : '尚未提供步骤')
    },
    connectionText() {
      return {
        connecting: '正在连接进度流',
        connected: '进度流已连接',
        checking: '正在核对任务状态',
        reconnecting: '进度流正在重连',
        completed: '任务已到终态',
        stopped: '已停止读取进度流',
        disconnected: '进度流已断开'
      }[this.connectionState] || '连接状态未知'
    },
    statusTagType() {
      if (this.connectionState === 'connected' || this.connectionState === 'completed') return 'success'
      if (this.connectionState === 'reconnecting' || this.connectionState === 'checking') return 'warning'
      return 'info'
    },
    progressType() {
      if (this.task.status === 'FAILED') return 'exception'
      if (this.task.status === 'COMPLETED') return 'success'
      return undefined
    },
    terminalSignal() {
      if (!shouldEmitTerminal(this.task, this.terminalConfirmed)) return ''
      return `${this.task.status}-${this.task.planId || ''}-${this.task.errorCode || ''}`
    }
  },
  watch: {
    terminalSignal: {
      immediate: true,
      handler(signature) {
        if (!signature || signature === this.emittedTerminal) return
        this.emittedTerminal = signature
        this.$emit('terminal', this.task)
      }
    }
  }
}
</script>

<style scoped>
.planning-progress { padding: 22px; border: 1px solid #dcdfe6; border-radius: 14px; background: #fff; }
.progress-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.eyebrow { margin: 0 0 4px; color: #606266; font-size: 13px; }
.progress-header h2 { margin: 0; }
.status-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.status-grid div { padding: 12px; border-radius: 8px; background: #f5f7fa; }
dt { color: #606266; font-size: 13px; }
dd { margin: 6px 0 0; font-weight: 600; }
.message { line-height: 1.6; }
.event-panel { margin-top: 22px; }
.event-panel ol { margin: 0; padding-left: 22px; }
.event-panel li { display: grid; grid-template-columns: minmax(130px, auto) minmax(130px, auto) 1fr; gap: 10px; padding: 10px 0; }
.event-panel small { grid-column: 2 / -1; color: #606266; }
.empty-events { color: #606266; }
@media (max-width: 680px) {
  .progress-header { flex-direction: column; }
  .status-grid { grid-template-columns: 1fr; }
  .event-panel li { grid-template-columns: 1fr; }
  .event-panel small { grid-column: auto; }
}
</style>
