import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Index from '../views/Index.vue'
import Suggest from "@/views/Suggest.vue";
import Collect from "@/views/Collect.vue";
import RoutePlan from "@/views/RoutePlan.vue";

const routes = [
  {
    path: '/login',
    name: '登录',
    component: Login,
    meta:{
      requiresHeader: false,
      requiresFooter: false
    }
  },
  {
    path: '/index',
    name: '系统首页',
    component: Index,
    meta:{
      requiresHeader: true,
      requiresFooter: true
    }
  },
  {
    path: '/suggest',
    name: '旅行推荐',
    component: Suggest,
    meta:{
      requiresHeader: true,
      requiresFooter: true
    }
  },
  {
    path: '/collect',
    name: '我的收藏',
    component: Collect,
    meta:{
      requiresHeader: true,
      requiresFooter: true
    }
  },
  {
    path: '/routePlan',
    name: '推荐路线',
    component: RoutePlan,
    meta:{
      requiresHeader: true,
      requiresFooter: true
    }
  },
  {
    path: '/about',
    name: 'about',
    component: () => import(/* webpackChunkName: "about" */ '../views/AboutView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

export default router
