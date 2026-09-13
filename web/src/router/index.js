import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Index from '../views/Index.vue'
import RoutePlan from '@/views/RoutePlan.vue'
import Planner from '@/views/Planner.vue'
import PlanDetail from '@/views/PlanDetail.vue'
import MyPlans from '@/views/MyPlans.vue'
import PreferenceSettings from '@/views/PreferenceSettings.vue'
import KnowledgeAdmin from '@/views/KnowledgeAdmin.vue'
import Forbidden from '@/views/Forbidden.vue'
import session from '../auth/session.cjs'

const protectedMeta = { requiresHeader: true, requiresFooter: true, requiresAuth: true }
const routes = [
  { path: '/', redirect: '/index' },
  { path: '/login', name: '登录', component: Login, meta: { requiresHeader: false, requiresFooter: false } },
  { path: '/index', name: '系统首页', component: Index, meta: protectedMeta },
  { path: '/suggest', name: '旅行推荐', redirect: { name: 'travel-planner' }, meta: protectedMeta },
  { path: '/planner', name: 'travel-planner', component: Planner, meta: protectedMeta },
  { path: '/plans', name: 'my-plans', component: MyPlans, meta: protectedMeta },
  { path: '/plans/:id', name: 'plan-detail', component: PlanDetail, props: true, meta: protectedMeta },
  { path: '/preferences', name: 'preference-settings', component: PreferenceSettings, meta: protectedMeta },
  { path: '/admin/knowledge', name: 'knowledge-admin', component: KnowledgeAdmin, meta: { ...protectedMeta, requiresAdmin: true } },
  { path: '/forbidden', name: 'forbidden', component: Forbidden, meta: protectedMeta },
  { path: '/collect', redirect: '/plans' },
  { path: '/routePlan', name: '推荐路线', component: RoutePlan, meta: protectedMeta },
  { path: '/about', name: 'about', component: () => import('../views/AboutView.vue'), meta: protectedMeta },
  { path: '/:pathMatch(.*)*', redirect: '/index' }
]

const router = createRouter({ history: createWebHistory(process.env.BASE_URL), routes })

router.beforeEach(to => {
  const authenticated = session.isAuthenticated(sessionStorage)
  const user = authenticated ? session.getUserProfile(sessionStorage) : null
  if (to.path === '/login' && authenticated) return session.safeRedirect(to.query.redirect)
  const decision = session.routeDecision(to.meta, user)
  if (decision.action === 'login') return { path: '/login', query: { redirect: to.fullPath } }
  if (decision.action === 'forbidden') return { path: '/forbidden', replace: true }
  return true
})

export default router
