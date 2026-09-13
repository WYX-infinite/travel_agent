<template>
  <main class="planner-page">
    <section class="planner-intro">
      <p class="eyebrow">单城市 · 多约束 · 可追溯</p>
      <h1>旅行规划</h1>
      <p>提交明确约束后，系统会异步检索来源、查询工具事实并校验行程。最终事实仍以后端返回为准。</p>
    </section>

    <div class="planner-layout">
      <el-card class="form-card" shadow="never">
        <template #header><strong>本次旅行约束</strong></template>
        <el-alert
          v-if="preferenceNotice"
          type="warning"
          :closable="false"
          :title="preferenceNotice"
          show-icon
          class="notice"
        />
        <el-alert
          v-if="submitError"
          type="error"
          :closable="false"
          :title="submitError.title"
          :description="submitError.message"
          show-icon
          class="notice"
        />

        <el-form
          ref="plannerForm"
          :model="planner.form"
          :rules="rules"
          label-position="top"
          :disabled="hasActiveTask"
          @submit.prevent="submitPlan"
        >
          <div class="form-grid">
            <el-form-item label="目的城市" prop="destinationCity">
              <el-input v-model="planner.form.destinationCity" maxlength="80" placeholder="例如：杭州" clearable />
            </el-form-item>
            <el-form-item label="同行类型" prop="companionType">
              <el-select v-model="planner.form.companionType" placeholder="请选择" class="full-width">
                <el-option v-for="item in companionOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker v-model="planner.form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" class="full-width" />
            </el-form-item>
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker v-model="planner.form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" class="full-width" />
            </el-form-item>
            <el-form-item label="人数" prop="travelers">
              <el-input-number v-model="planner.form.travelers" :min="1" :max="50" class="full-width" />
            </el-form-item>
            <el-form-item label="总预算（人民币）" prop="totalBudget">
              <el-input-number v-model="planner.form.totalBudget" :min="1" :precision="2" :step="100" class="full-width" />
            </el-form-item>
          </div>

          <el-form-item label="兴趣" prop="interests">
            <el-checkbox-group v-model="planner.form.interests" @change="touchField('interests')">
              <el-checkbox v-for="item in interestOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <div class="form-grid">
            <el-form-item label="旅行节奏" prop="pace">
              <el-select v-model="planner.form.pace" placeholder="请选择" class="full-width" @change="touchField('pace')">
                <el-option v-for="item in paceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="主要交通方式" prop="travelMode">
              <el-select v-model="planner.form.travelMode" placeholder="请选择" class="full-width" @change="touchField('travelMode')">
                <el-option v-for="item in modeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="住宿地址 / 位置偏好" prop="accommodationAddress">
            <el-input
              v-model="planner.form.accommodationAddress"
              maxlength="255"
              placeholder="例如：杭州市上城区湖滨街道"
              clearable
              @input="touchField('accommodationAddress')"
            />
          </el-form-item>
          <el-form-item label="特殊要求" prop="specialRequirements">
            <el-input
              v-model="planner.form.specialRequirements"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="例如：每天最晚 20:00 结束"
            />
          </el-form-item>

          <div class="preference-switch">
            <div>
              <strong>使用已保存偏好</strong>
              <p>只会补全尚未编辑的兴趣、节奏、交通方式和住宿位置。</p>
            </div>
            <el-switch v-model="planner.form.useSavedPreferences" @change="togglePreferences" />
          </div>

          <el-button
            type="primary"
            size="large"
            native-type="submit"
            :loading="submitting"
            :disabled="submitting || hasActiveTask"
            class="submit-button"
          >开始规划</el-button>
        </el-form>
      </el-card>

      <div class="progress-column">
        <PlanningProgress
          v-if="task.taskId"
          :task="task"
          :events="events"
          :connection-state="connectionState"
          :terminal-confirmed="task.terminalConfirmed === true"
          @terminal="handleTerminal"
        />
        <el-card v-else shadow="never" class="waiting-card">
          <h2>规划进度将在这里显示</h2>
          <p>页面不会用模拟数据冒充真实行程。提交后可看到后端任务状态、步骤和持久化事件。</p>
        </el-card>
      </div>
    </div>
  </main>
</template>

<script>
import { ElMessage } from 'element-plus'
import PlanningProgress from '../components/planning/PlanningProgress.vue'
import {
  createPlanningTask,
  getPlanningTask,
  isAuthenticationError,
  subscribePlanningEvents
} from '../api/planning'
import { getTravelPreferences } from '../api/preferences'
import plannerState from '../planning/plannerState.cjs'
import session from '../auth/session.cjs'

const {
  applySavedPreferences,
  buildPlanningPayload,
  classifyTaskStatus,
  createPlannerState,
  disableSavedPreferences,
  enableSavedPreferences,
  markFieldDirty,
  mergeTaskEvent,
  preferenceFailureNotice,
  terminalConfirmationForUpdate,
  validatePlanningPayload
} = plannerState

const ACTIVE_STATUSES = ['PENDING', 'RUNNING']
const TASK_STORAGE_KEY = 'activePlanningTaskId'

export default {
  name: 'PlannerPage',
  components: { PlanningProgress },
  data() {
    return {
      planner: createPlannerState(),
      savedPreferences: null,
      preferenceNotice: '',
      submitError: null,
      submitting: false,
      task: {},
      events: [],
      connectionState: 'disconnected',
      streamController: null,
      rules: {
        destinationCity: [{ required: true, message: '请输入目的城市', trigger: 'blur' }],
        startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
        endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
        travelers: [{ required: true, message: '请输入人数', trigger: 'change' }],
        totalBudget: [{ required: true, message: '请输入总预算', trigger: 'change' }],
        companionType: [{ required: true, message: '请选择同行类型', trigger: 'change' }],
        interests: [{ type: 'array', required: true, min: 1, message: '至少选择一项兴趣', trigger: 'change' }],
        pace: [{ required: true, message: '请选择旅行节奏', trigger: 'change' }],
        travelMode: [{ required: true, message: '请选择交通方式', trigger: 'change' }]
      },
      interestOptions: [
        { value: 'FOOD', label: '美食' },
        { value: 'HISTORY', label: '历史文化' },
        { value: 'NATURE', label: '自然景观' },
        { value: 'PHOTOGRAPHY', label: '摄影' },
        { value: 'SHOPPING', label: '购物' }
      ],
      paceOptions: [
        { value: 'RELAXED', label: '轻松' },
        { value: 'MODERATE', label: '适中' },
        { value: 'COMPACT', label: '紧凑' }
      ],
      companionOptions: [
        { value: 'SOLO', label: '独自旅行' },
        { value: 'COUPLE', label: '情侣 / 夫妻' },
        { value: 'FAMILY', label: '家庭亲子' },
        { value: 'FRIENDS', label: '朋友结伴' },
        { value: 'SENIORS', label: '长者同行' }
      ],
      modeOptions: [
        { value: 'WALKING', label: '步行' },
        { value: 'TRANSIT', label: '公共交通' },
        { value: 'TAXI', label: '出租车 / 网约车' },
        { value: 'DRIVING', label: '驾车' }
      ]
    }
  },
  computed: {
    hasActiveTask() {
      return ACTIVE_STATUSES.includes(this.task.status)
    }
  },
  async mounted() {
    this.loadPreferences()
    await this.restoreTask()
  },
  beforeUnmount() {
    this.stopStream()
  },
  methods: {
    touchField(field) {
      markFieldDirty(this.planner, field)
    },
    togglePreferences(enabled) {
      if (!enabled) disableSavedPreferences(this.planner)
      else {
        enableSavedPreferences(this.planner)
        applySavedPreferences(this.planner, this.savedPreferences)
      }
    },
    async loadPreferences() {
      try {
        const preference = await getTravelPreferences()
        this.savedPreferences = preference
        applySavedPreferences(this.planner, preference)
      } catch (error) {
        if (isAuthenticationError(error)) {
          this.goToLogin(error.message)
          return
        }
        this.preferenceNotice = preferenceFailureNotice(error).message
      }
    },
    async submitPlan() {
      if (this.submitting || this.hasActiveTask) return
      this.submitError = null
      const elementValid = await this.$refs.plannerForm.validate().catch(() => false)
      if (!elementValid) return
      const errors = validatePlanningPayload(this.planner.form)
      if (Object.keys(errors).length) {
        this.submitError = { title: '请检查旅行约束', message: Object.values(errors).join('；') }
        return
      }
      this.submitting = true
      try {
        const created = await createPlanningTask(buildPlanningPayload(this.planner.form, this.planner.fields))
        if (!created?.taskId) throw new Error('TASK_ID_MISSING')
        this.task = { ...created, terminalConfirmed: false }
        this.events = []
        sessionStorage.setItem(TASK_STORAGE_KEY, String(created.taskId))
        this.startStream(created.taskId)
      } catch (error) {
        this.handleRequestError(error, '创建规划任务失败')
      } finally {
        this.submitting = false
      }
    },
    async restoreTask() {
      const taskId = sessionStorage.getItem(TASK_STORAGE_KEY)
      if (!taskId) return
      try {
        const restored = await getPlanningTask(taskId)
        this.task = {
          ...restored,
          terminalConfirmed: terminalConfirmationForUpdate(restored, 'task-status')
        }
        const result = classifyTaskStatus(restored)
        if (result.kind === 'active') this.startStream(taskId)
        else this.handleTerminal(restored)
      } catch (error) {
        sessionStorage.removeItem(TASK_STORAGE_KEY)
        this.handleRequestError(error, '恢复规划任务失败')
      }
    },
    startStream(taskId) {
      this.stopStream()
      try {
        this.streamController = subscribePlanningEvents(taskId, {
          onEvent: event => {
            this.events = mergeTaskEvent(this.events, event)
            this.applyEventToTask(event.data, event.event)
          },
          onTaskStatus: status => {
            this.task = {
              ...this.task,
              ...status,
              terminalConfirmed: terminalConfirmationForUpdate(status, 'task-status')
            }
            if (classifyTaskStatus(this.task).kind !== 'active') this.handleTerminal(this.task)
          },
          onMalformedEvent: () => {
            ElMessage.warning('收到无法解析的进度事件，已忽略并继续核对任务状态。')
          },
          onError: error => this.handleRequestError(error, '规划进度连接失败'),
          onConnectionState: state => { this.connectionState = state }
        })
        this.streamController.start()
      } catch (error) {
        this.handleRequestError(error, '无法订阅规划进度')
      }
    },
    applyEventToTask(data = {}, eventName = '') {
      const status = data.status || this.task.status
      this.task = {
        ...this.task,
        status,
        currentStep: data.currentStep || data.step || this.task.currentStep,
        progressPercent: data.progressPercent ?? this.task.progressPercent,
        planId: data.planId ?? this.task.planId,
        errorCode: data.errorCode ?? this.task.errorCode,
        errorMessage: data.errorMessage ?? this.task.errorMessage,
        message: data.message || this.task.message,
        terminalConfirmed: terminalConfirmationForUpdate({ status }, eventName)
      }
      if (eventName === 'planning-status' && classifyTaskStatus(this.task).kind !== 'active') {
        this.handleTerminal(this.task)
      }
    },
    handleTerminal(task) {
      const result = classifyTaskStatus(task)
      if (result.kind === 'active') return
      this.stopStream()
      sessionStorage.removeItem(TASK_STORAGE_KEY)
      if (result.navigate) {
        this.$router.push({ name: 'plan-detail', params: { id: result.planId } })
      } else if (result.kind === 'invalid-completion') {
        this.submitError = { title: '规划结果不完整', message: '任务已完成，但后端未返回行程编号。' }
      } else if (result.kind === 'unknown') {
        this.submitError = { title: '未知任务状态', message: `后端返回了无法识别的状态：${task.status || '未提供'}` }
      }
    },
    stopStream() {
      if (this.streamController) this.streamController.stop()
      this.streamController = null
    },
    handleRequestError(error, fallbackTitle) {
      if (isAuthenticationError(error)) {
        this.goToLogin(error.message)
        return
      }
      this.submitError = {
        title: error?.code || fallbackTitle,
        message: error?.message || '请求未成功，请稍后重试。'
      }
    },
    goToLogin(message) {
      ElMessage.warning(message || '请重新登录。')
      const target = session.authenticationRedirect(sessionStorage, this.$route.fullPath)
      this.$router.replace(target)
    }
  }
}
</script>

<style scoped>
.planner-page { min-height: 100vh; padding: 92px 24px 48px; background: #f3f6f8; color: #263746; }
.planner-intro { max-width: 1180px; margin: 0 auto 24px; }
.planner-intro h1 { margin: 4px 0 10px; font-size: clamp(30px, 4vw, 48px); }
.planner-intro > p:last-child { max-width: 760px; color: #536775; line-height: 1.7; }
.eyebrow { margin: 0; color: #2768a9; font-size: 13px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.planner-layout { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(360px, .85fr); gap: 24px; max-width: 1180px; margin: 0 auto; align-items: start; }
.form-card, .waiting-card { border-radius: 14px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.full-width { width: 100%; }
.notice { margin-bottom: 18px; }
.preference-switch { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin: 14px 0 22px; padding: 16px; border-radius: 10px; background: #eef4f8; }
.preference-switch p { margin: 6px 0 0; color: #536775; font-size: 13px; }
.submit-button { width: 100%; }
.progress-column { position: sticky; top: 88px; }
.waiting-card h2 { margin-top: 0; font-size: 20px; }
.waiting-card p { color: #536775; line-height: 1.7; }
@media (max-width: 900px) {
  .planner-layout { grid-template-columns: 1fr; }
  .progress-column { position: static; }
}
@media (max-width: 560px) {
  .planner-page { padding: 78px 12px 30px; }
  .form-grid { grid-template-columns: 1fr; }
  .preference-switch { align-items: flex-start; }
}
</style>
