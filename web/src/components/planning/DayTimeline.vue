<template>
  <section class="day-timeline" aria-label="每日行程">
    <p v-if="!dayItems.length" class="empty-state">暂无可展示的日程安排</p>
    <article v-for="day in dayItems" :key="day.date" class="day-card">
      <h3>{{ day.date || '日期未提供' }}</h3>
      <ol class="timeline-list">
        <li v-for="item in safeItems(day)" :key="itemKey(day, item)" class="timeline-item">
          <div class="time-column">
            <strong>{{ item.startTime || '时间未知' }}</strong>
            <span>{{ item.endTime || '时间未知' }}</span>
          </div>
          <div class="activity-column">
            <div class="item-heading">
              <h4>{{ item.poiName || '地点名称未提供' }}</h4>
              <el-tag size="small" :type="costView(item.cost).estimated ? 'warning' : 'info'">
                {{ costView(item.cost).label }}
              </el-tag>
            </div>
            <p class="address">地址：{{ item.address || '暂无可靠地址事实' }}</p>
            <p>{{ item.activity || '暂无活动说明' }}</p>
            <p class="cost">费用：{{ costView(item.cost).amount }}</p>

            <div v-if="item.nextSegment" class="route-fact">
              <strong>下一段路线：{{ item.nextSegment.verified ? '已验证' : '未验证' }}</strong>
              <span v-if="item.nextSegment.mode">方式 {{ item.nextSegment.mode }}</span>
              <span v-if="item.nextSegment.distanceMeters != null">距离 {{ item.nextSegment.distanceMeters }} 米</span>
              <span v-if="item.nextSegment.durationMinutes != null">约 {{ item.nextSegment.durationMinutes }} 分钟</span>
            </div>
            <p v-else class="empty-fact">暂无路线事实</p>

            <EvidenceList :evidence="item.evidence" />
          </div>
        </li>
      </ol>
    </article>
  </section>
</template>

<script>
import EvidenceList from './EvidenceList.vue'
import plannerState from '../../planning/plannerState.cjs'

const { formatCost } = plannerState

export default {
  name: 'DayTimeline',
  components: { EvidenceList },
  props: {
    days: { type: Array, default: () => [] }
  },
  computed: {
    dayItems() {
      return Array.isArray(this.days) ? this.days : []
    }
  },
  methods: {
    costView: formatCost,
    safeItems(day) {
      return Array.isArray(day?.items) ? day.items : []
    },
    itemKey(day, item) {
      return `${day.date || 'unknown'}-${item.poiId || item.poiName || 'item'}-${item.startTime || 'start'}-${item.endTime || 'end'}`
    }
  }
}
</script>

<style scoped>
.day-timeline { display: grid; gap: 20px; }
.day-card { padding: 22px; border: 1px solid #dcdfe6; border-radius: 14px; background: #fff; }
.day-card h3 { margin: 0 0 18px; color: #1f3d57; }
.timeline-list { list-style: none; margin: 0; padding: 0; }
.timeline-item { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 18px; padding: 18px 0; border-top: 1px solid #ebeef5; }
.timeline-item:first-child { border-top: 0; padding-top: 0; }
.time-column { display: flex; flex-direction: column; gap: 6px; color: #35536b; }
.activity-column { min-width: 0; }
.item-heading, .route-fact { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.item-heading { justify-content: space-between; }
.item-heading h4 { margin: 0; font-size: 18px; }
.activity-column p { line-height: 1.6; }
.address, .empty-fact { color: #606266; }
.cost { font-weight: 600; }
.route-fact { padding: 12px; border-radius: 8px; background: #f0f5f8; color: #304b60; }
.empty-state { color: #606266; }
@media (max-width: 600px) {
  .day-card { padding: 16px; }
  .timeline-item { grid-template-columns: 1fr; gap: 8px; }
  .time-column { flex-direction: row; }
}
</style>
