<template>
  <div class="result-container" v-loading="loading">
    <!-- 暂无收藏 -->
    <div v-if="!recommendations.length" class="empty-result">
      <div class="empty-icon">
        <el-icon>
          <Collection />
        </el-icon>
      </div>
      <h3>暂无收藏信息</h3>
      <p>您还未收藏任何旅行方案，快去收藏喜欢的方案吧</p>
    </div>

    <!-- 收藏列表 -->
    <div v-else class="recommendations-list">
      <h2 class="result-title">
        <el-icon>
          <Promotion />
        </el-icon>
        AI为您推荐的旅行目的地
      </h2>

      <div class="card-wrapper">
        <div v-for="(rec, index) in recommendations" :key="rec.recommendationId" class="recommendation-card">
          <!-- 卡片头部 -->
          <div class="card-header">
            <h3 class="destination-name">{{ rec.destination }}</h3>
            <div class="meta-info">
              <span>
                <el-icon><Coin /></el-icon>
                {{ rec.estimatedCost || '待估算' }}
              </span>
              <span>
                <el-icon><Calendar /></el-icon>
                {{ rec.bestSeason || '全年适宜' }}
              </span>
            </div>
          </div>

          <!-- 卡片内容 -->
          <div class="card-content">
            <!-- 推荐理由 -->
            <div class="info-section">
              <h4>
                <el-icon><ChatDotRound /></el-icon>
                推荐理由
              </h4>
              <p>{{rec.recommendationReason || '暂无推荐理由' }}</p>
            </div>

            <!-- 住宿、交通 -->
            <div class="info-grid">
              <div class="info-item">
                <h4>
                  <el-icon><House /></el-icon>
                  住宿建议
                </h4>
                <p>{{rec.recommendedAccommodation || '暂无住宿建议' }}</p>
              </div>
              <div class="info-item">
                <h4>
                  <el-icon><Van /></el-icon>
                  交通建议
                </h4>
                <p>{{rec.recommendedTransportation || '暂无交通建议' }}</p>
              </div>
            </div>
            <!-- 特别提示 -->
            <div v-if="rec.specialTips" class="info-section">
              <h4>
                <el-icon><Warning /></el-icon>
                特别提示
              </h4>
              <p>{{ rec.specialTips }}</p>
            </div>
          </div>

          <!-- 卡片底部 -->
          <div class="card-footer">
            <span>推荐生成时间：{{ formatDate(rec.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>


<script>
import axios from "axios";

import {
  Calendar,
  ChatDotRound,
  Coin,
  Collection,
  House,
  Promotion,
  Van,
  Warning
} from "@element-plus/icons-vue";

export default {
  name: "TravelRecommendation",
  /*
   * Element Plus 图标
   */
  components: {
    Calendar,
    ChatDotRound,
    Coin,
    Collection,
    House,
    Promotion,
    Van,
    Warning
  },

  data() {
    return {
      loading: true,
      recommendations: [],
      /*
       * 卡片头部背景颜色
       */
      lightColorPool: [
        "#e0f5ff",
        "#d6f4ff",
        "#e6fbff",
        "#e8f8f5",
        "#d4f1f4",
        "#eafaf1",
        "#fff9e6",
        "#fff6e0",
        "#fff3d6",
        "#ffebee",
        "#fce4ec",
        "#fef0f0"
      ],
      user: null
    };
  },

  created() {
    this.initPage();
  },

  methods: {
    /*
     * 初始化页面
     */
    initPage() {
      /*
       * 从 localStorage 获取用户信息
       */
      const userString =
          localStorage.getItem("user");
      /*
       * 防止用户不存在时
       * JSON.parse 或 this.user.id 报错
       */
      if (!userString) {
        this.loading = false;
        console.warn("未获取到用户登录信息");
        return;
      }
      try {
        this.user = JSON.parse(userString);
      } catch (error) {
        console.error("用户信息解析失败：", error);
        this.loading = false;
        return;
      }

      /*
       * 检查用户 id
       */
      if (!this.user?.id) {
        console.warn("用户ID不存在");
        this.loading = false;
        return;
      }

      /*
       * 查询收藏列表
       */
      axios.get("http://localhost:8181/list",
              {
                params: {
                  id: this.user.id
                }
              }
          ).then((response) => {
            this.recommendations = response.data || [];
          }).catch((error) => {
            console.error("获取收藏列表失败：", error);
            this.recommendations = [];
          }).finally(() => {
            this.loading = false;
          });
    },

    /*
     * 日期格式化
     */
    formatDate(timestamp) {
      if (!timestamp) {
        return "N/A";
      }
      const date = new Date(timestamp);
      if (isNaN(date.getTime())) {
        return "无效日期";
      }
      return date.toLocaleString(
          "zh-CN",
          {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
          }
      );
    },

    /*
     * 保证相邻两个卡片颜色不同
     */
    getDiffAdjacentColor(index) {
      const colorLen = this.lightColorPool.length;
      let currentColor = this.lightColorPool[index % colorLen];
      /*
       * 如果是第二列
       * 检查与前一个颜色是否重复
       */
      if (index % 2 === 1) {
        const prevColor = this.lightColorPool[(index - 1) % colorLen];
        if (currentColor === prevColor) {
          currentColor = this.lightColorPool[(index + 1) % colorLen];
        }
      }
      return currentColor;
    }
  }
};
</script>


<style scoped>
/* ===============================
   空状态
   =============================== */
.empty-result {
  width: 100% !important;
  height: calc(100% - 48px) !important;
  display: flex !important;
  flex-direction: column !important;
  justify-content: center !important;
  align-items: center !important;
  text-align: center !important;
  padding: 60px 20px !important;
  color: #909399 !important;
  margin: 0 auto !important;
}

.empty-icon {
  font-size: 60px;
  color: #c0c4cc;
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
}

.empty-result h3 {
  font-size: 20px;
  margin-bottom: 12px;
  color: #606266;
}

.empty-result p {
  font-size: 14px;
  line-height: 1.6;
}

/* ===============================
   根容器
   =============================== */

.travel-recommendation-app {
  height: 100vh;
  min-height: 100vh;
  background-color: #f5f7fa;
  font-family:
      "Helvetica Neue",
      Arial,
      "PingFang SC",
      "Microsoft YaHei",
      sans-serif;
  padding-top: 24px;
  box-sizing: border-box;
  overflow: hidden;
}


/* ===============================
   结果容器
   =============================== */

.result-container {
  width: 100%;
  height: 100%;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 24px;
  box-sizing: border-box;
  overflow-y: auto;
  text-align: left;
  margin-top: 70px;
}


/* ===============================
   页面标题
   =============================== */

.result-title {
  font-size: 20px;
  color: #409eff;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
}


/* ===============================
   卡片容器
   一行两列
   =============================== */

.card-wrapper {
  display: flex;
  flex-wrap: wrap;
  width: 100%;
  align-items: flex-start;
  row-gap: 20px;
  column-gap: 20px;
}


/* ===============================
   推荐卡片
   =============================== */

.recommendation-card {
  width: calc(50% - 10px);
  border: 1px solid #e6e9f0;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.3s ease-in-out;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transform-origin: center center;
  box-sizing: border-box;
  margin: 0 !important;
}

.recommendation-card:hover {
  transform: scale(1.03);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12), 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: #d0e6ff;
}


/* ===============================
   卡片头部
   =============================== */

.card-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e6e9f0;
  text-align: left;
}

.destination-name {
  margin: 0 0 8px;
  font-size: 22px;
  color: #303133;
  font-weight: 600;
}

.meta-info {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #606266;
  flex-wrap: wrap;
}


.meta-info span {
  display: flex;
  align-items: center;
  gap: 6px;
}


/* ===============================
   卡片内容
   =============================== */

.card-content {
  padding: 20px;
  text-align: left;
}


/* ===============================
   普通信息区域
   =============================== */

.info-section {
  margin-bottom: 20px;
}


.info-section h4 {
  font-size: 16px;
  color: #409eff;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}


.info-section p {
  margin: 0;
  line-height: 1.6;
  color: #606266;
}


/* ===============================
   住宿 + 交通
   =============================== */

.info-grid {
  display: grid;
  grid-template-columns:1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}


.info-item {
  background-color: #f9fafc;
  padding: 16px;
  border-radius: 6px;
  border: 1px solid #e6e9f0;
  text-align: left;
}


.info-item h4 {
  font-size: 15px;
  color: #409eff;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}


.info-item p {
  margin: 0;
  line-height: 1.5;
  color: #606266;
  font-size: 14px;
}


/* ===============================
   卡片底部
   =============================== */

.card-footer {
  padding: 12px 20px;
  background-color: #fafafa;
  border-top: 1px solid #e6e9f0;
  font-size: 13px;
  color: #909399;
  text-align: right;
}


/* ===============================
   移动端
   =============================== */

@media (max-width: 768px) {
  .travel-recommendation-app {
    padding-top: 16px;
  }

  .result-container {
    padding: 16px;
  }

  .info-grid {
    grid-template-columns:1fr;
  }

  .empty-result {
    height: calc(100% - 32px) !important;
    padding: 20px !important;
  }

  .recommendation-card:hover {
    transform: scale(1);
  }

  .recommendation-card {
    width: 100% !important;
  }

  .card-wrapper {
    row-gap: 15px;
    column-gap: 0;
  }
}

</style>