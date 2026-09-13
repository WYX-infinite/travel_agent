<template>
  <nav class="travel-nav" aria-label="主导航">
    <div class="nav-container">
      <router-link to="/index" class="nav-logo">TravelAgent</router-link>
      <button class="menu-toggle" type="button" :aria-expanded="mobileOpen" aria-label="切换导航菜单" @click="mobileOpen = !mobileOpen">☰</button>
      <ul :class="['nav-list', { open: mobileOpen }]">
        <li><router-link to="/index" class="nav-link" @click="mobileOpen = false">首页</router-link></li>
        <li><router-link to="/planner" class="nav-link" @click="mobileOpen = false">旅行规划</router-link></li>
        <li><router-link to="/plans" class="nav-link" @click="mobileOpen = false">我的行程</router-link></li>
        <li><router-link to="/preferences" class="nav-link" @click="mobileOpen = false">偏好设置</router-link></li>
        <li v-if="user?.role === 'ADMIN'"><router-link to="/admin/knowledge" class="nav-link" @click="mobileOpen = false">知识库管理</router-link></li>
        <li class="identity"><span>{{ user?.name || user?.username || '用户' }}<small>{{ user?.role === 'ADMIN' ? '管理员' : '用户' }}</small></span></li>
        <li><button type="button" class="logout-button" @click="logout">退出</button></li>
      </ul>
    </div>
  </nav>
</template>

<script>
import { ElMessageBox } from 'element-plus'
import session from '../auth/session.cjs'

export default {
  name: 'TravelNavbar',
  data() { return { user: null, mobileOpen: false } },
  mounted() { this.refreshUser() },
  watch: {
    '$route.fullPath'() { this.mobileOpen = false; this.refreshUser() }
  },
  methods: {
    refreshUser() { this.user = session.getUserProfile(sessionStorage) },
    async logout() {
      await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { confirmButtonText: '退出', cancelButtonText: '取消' }).catch(() => false)
        .then(confirmed => {
          if (confirmed === false) return
          session.clearAuthSession(sessionStorage)
          this.$router.replace('/login')
        })
    }
  }
}
</script>

<style scoped>
.travel-nav { position: fixed; inset: 0 0 auto; z-index: 1000; background: #fff; box-shadow: 0 2px 12px rgba(34, 55, 68, .08); }
.nav-container { min-height: 64px; max-width: 1200px; margin: auto; padding: 0 24px; display: flex; justify-content: space-between; align-items: center; }
.nav-logo { color: #253b48; font-size: 22px; font-weight: 800; text-decoration: none; }
.nav-list { display: flex; align-items: center; gap: 22px; margin: 0; padding: 0; list-style: none; }
.nav-link, .logout-button { color: #405766; font: inherit; font-size: 14px; text-decoration: none; border: 0; background: none; cursor: pointer; }
.nav-link.router-link-active { color: #1768a6; font-weight: 700; }
.identity span { display: flex; flex-direction: column; color: #263746; font-size: 13px; font-weight: 700; }
.identity small { color: #788b96; font-size: 11px; font-weight: 500; }
.logout-button { color: #a43d3d; }
.menu-toggle { display: none; border: 1px solid #d8e0e5; border-radius: 7px; background: #fff; padding: 7px 10px; font-size: 20px; }
@media (max-width: 900px) {
  .menu-toggle { display: block; }
  .nav-list { display: none; position: absolute; top: 64px; left: 12px; right: 12px; padding: 18px; align-items: stretch; flex-direction: column; gap: 16px; background: #fff; border: 1px solid #e1e7eb; border-radius: 12px; box-shadow: 0 12px 28px rgba(34, 55, 68, .14); }
  .nav-list.open { display: flex; }
}
@media (max-width: 390px) { .nav-container { padding: 0 14px; } }
</style>
