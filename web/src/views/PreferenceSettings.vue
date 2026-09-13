<template>
  <main class="page-shell">
    <header class="page-heading"><p class="eyebrow">TRAVEL PREFERENCES</p><h1>长期偏好</h1><p>这些字段只会补全规划表单中尚未填写的内容，不会覆盖你本次手动输入的约束。</p><p>日期、预算、同行信息、特殊要求、聊天记录和完整历史行程不会保存为长期偏好。</p></header>
    <el-skeleton v-if="loading" :rows="6" animated class="form-card" />
    <el-alert v-else-if="loadError" type="error" :title="loadError.title" :description="loadError.message" :closable="false" show-icon class="form-card" />
    <el-form v-else ref="preferenceForm" :model="form" label-position="top" class="form-card" @submit.prevent="save">
      <el-alert v-if="updatedAt" type="info" :closable="false" :title="`服务端最近更新：${formatTime(updatedAt)}`" />
      <el-form-item label="兴趣方向" :error="errors.interests">
        <el-checkbox-group v-model="form.interests">
          <el-checkbox v-for="item in interests" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <div class="two-column">
        <el-form-item label="行程节奏" :error="errors.pace"><el-select v-model="form.pace" clearable class="full"><el-option v-for="item in paces" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
        <el-form-item label="主要交通方式" :error="errors.travelMode"><el-select v-model="form.travelMode" clearable class="full"><el-option v-for="item in modes" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
      </div>
      <el-form-item label="住宿位置偏好" :error="errors.accommodationPreference">
        <el-input v-model="form.accommodationPreference" type="textarea" :rows="4" maxlength="255" show-word-limit placeholder="例如：地铁站步行十分钟内" />
      </el-form-item>
      <div class="actions">
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="saving || clearing">保存完整偏好</el-button>
        <el-button type="danger" plain :loading="clearing" :disabled="saving || clearing" @click="clearAll">清除已保存偏好</el-button>
      </div>
    </el-form>
  </main>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTravelPreferences, replaceTravelPreferences, clearTravelPreferences } from '../api/preferences'
import preferenceState from '../preferences/preferenceState.cjs'

export default {
  name: 'PreferenceSettingsPage',
  data() {
    return {
      loading: true, saving: false, clearing: false, loadError: null, updatedAt: null,
      form: preferenceState.emptyPreferenceForm(), errors: {},
      interests: [{ value: 'FOOD', label: '美食' }, { value: 'HISTORY', label: '历史文化' }, { value: 'NATURE', label: '自然风景' }, { value: 'PHOTOGRAPHY', label: '摄影' }, { value: 'SHOPPING', label: '购物' }],
      paces: [{ value: 'RELAXED', label: '舒缓' }, { value: 'MODERATE', label: '适中' }, { value: 'COMPACT', label: '紧凑' }],
      modes: [{ value: 'WALKING', label: '步行' }, { value: 'TRANSIT', label: '公共交通' }, { value: 'TAXI', label: '出租车' }, { value: 'DRIVING', label: '自驾' }]
    }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.loadError = null
      try {
        const data = await getTravelPreferences()
        this.form = { interests: Array.isArray(data?.interests) ? data.interests : [], pace: data?.pace || '', travelMode: data?.travelMode || '', accommodationPreference: data?.accommodationPreference || '' }
        this.updatedAt = data?.updatedAt || null
      } catch (error) { this.loadError = { title: error?.code || '偏好加载失败', message: error?.message || '暂时无法读取偏好。' } }
      finally { this.loading = false }
    },
    async save() {
      if (this.saving || this.clearing) return
      this.errors = preferenceState.validatePreferenceForm(this.form)
      if (Object.keys(this.errors).length) return
      this.saving = true
      try {
        const confirmed = await ElMessageBox.confirm('保存会整体替换当前账号的四项长期偏好，是否继续？', '保存偏好', { confirmButtonText: '保存', cancelButtonText: '取消' }).then(() => true).catch(() => false)
        if (!confirmed) return
        await replaceTravelPreferences(preferenceState.buildPreferencePayload(this.form)); ElMessage.success('偏好已保存。'); await this.load()
      }
      catch (error) { ElMessage.error(error?.message || '偏好保存失败。') }
      finally { this.saving = false }
    },
    async clearAll() {
      if (this.saving || this.clearing) return
      this.clearing = true
      try {
        const confirmed = await ElMessageBox.confirm('将删除当前账号的全部长期偏好，是否继续？', '清除偏好', { confirmButtonText: '清除', cancelButtonText: '取消', type: 'warning' }).then(() => true).catch(() => false)
        if (!confirmed) return
        await clearTravelPreferences(); ElMessage.success('偏好已清除。'); await this.load()
      }
      catch (error) { ElMessage.error(error?.message || '偏好清除失败。') }
      finally { this.clearing = false }
    },
    formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '暂无' }
  }
}
</script>

<style scoped>
.page-shell { min-height: 100vh; padding: 92px 24px 48px; background: #f3f6f8; color: #263746; text-align: left; }.page-heading, .form-card { max-width: 840px; margin: 0 auto 24px; }.page-heading h1 { margin: 6px 0; font-size: clamp(32px, 5vw, 50px); }.page-heading p { color: #5d717d; line-height: 1.7; }.eyebrow { font-size: 12px; font-weight: 800; color: #2768a9 !important; letter-spacing: .1em; }.form-card { padding: 26px; border: 1px solid #dde5e9; border-radius: 14px; background: #fff; box-sizing: border-box; }.form-card .el-alert { margin-bottom: 20px; }.two-column { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }.full { width: 100%; }.actions { display: flex; gap: 10px; flex-wrap: wrap; }
@media (max-width: 600px) { .page-shell { padding: 82px 12px 32px; }.two-column { grid-template-columns: 1fr; }.form-card { padding: 18px; } }
</style>
