<template>
  <section class="section recommended">
    <div class="container">
      <div class="section-header">
        <div class="header-content">
          <h2 class="section-title">为您推荐</h2>
          <div class="section-subtitle">探索精心挑选的旅行体验</div>
        </div>
        <button class="refresh-btn" @click="refreshRecommendations" :disabled="loading">
          <span v-if="loading">
            <div class="btn-spinner"></div>
            加载中...
          </span>
          <span v-else>
            <svg class="refresh-icon" viewBox="0 0 24 24" width="16" height="16">
              <path
                  fill="currentColor"
                  d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
              />
            </svg>
            换一批推荐
          </span>
        </button>
      </div>

      <div v-if="loading" class="loading-container">
        <div class="loading-spinner"></div>
        <p>正在为您推荐精彩的旅行体验...</p>
      </div>

      <div v-else class="recommended-container">
        <div
            class="recommended-card"
            v-for="item in recommendedItems"
            :key="item.id"
            @mouseenter="hoverCard = item.id"
            @mouseleave="hoverCard = null"
        >
          <div class="card-image-container">
            <div class="recommended-image" :style="{ backgroundImage: `url(${item.image})` }">
              <div class="image-overlay"></div>
              <div class="duration-badge">{{ item.duration }}天</div>
            </div>
          </div>

          <div class="recommended-content">
            <div class="content-header">
              <h3 class="recommended-title">{{ item.title }}</h3>
              <div class="rating-container">
                <div class="rating-stars">
                  <span v-for="n in 5" :key="n" class="star">★</span>
                </div>
                <span class="rating-value">{{ item.rating }}</span>
              </div>
            </div>

            <p class="recommended-description">{{ item.description }}</p>

            <div class="recommended-meta">
              <div class="meta-item">
                <svg class="meta-icon" viewBox="0 0 24 24" width="16" height="16">
                  <path
                      fill="currentColor"
                      d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
                  />
                </svg>
                <span>{{ item.groupSize }}人团</span>
              </div>
            </div>

            <div class="recommended-price">
              <div class="price-and-reason">
                <div class="price-container">
                  <span class="price-prefix">起</span>
                  <span class="current-price">¥{{ item.price }}</span>
                  <span class="price-unit">/人</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: "RecommendedSection",
  data() {
    return {
      recommendedItems: [
        {
          id: 1,
          image: "https://images.unsplash.com/photo-1512453979798-5ea266f8880c?ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D&auto=format&fit=crop&w=1000&q=80",
          title: "云南大理3天经济游",
          description: "漫步古城，骑行洱海，体验白族风情，性价比之选。",
          duration: 3,
          rating: 4.8,
          groupSize: 15,
          price: 1200
        },
        {
          id: 2,
          image: "https://images.unsplash.com/photo-1519922639192-e73293ca430e?ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D&auto=format&fit=crop&w=1000&q=80",
          title: "桂林阳朔5天舒适之旅",
          description: "漓江游船、遇龙河骑行，住四星酒店，服务贴心。",
          duration: 5,
          rating: 4.7,
          groupSize: 10,
          price: 2800
        },
        {
          id: 3,
          image: "https://images.unsplash.com/photo-1528164344705-47542687000d?ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D&auto=format&fit=crop&w=1000&q=80",
          title: "马尔代夫6天奢华度假",
          description: "私人海岛别墅，浮潜天堂，一价全包五星体验。",
          duration: 6,
          rating: 4.9,
          groupSize: 2,
          price: 15000
        }
      ],
      loading: false,
      hoverCard: null,
      themes: [
        "异国风情·多元体验：3个不同国家的海岛、文化与自然之旅",
        "价格阶梯·丰俭由人：经济实惠、中端舒适、高端奢华的3种选择",
        "四季流转·时光印记：春之浪漫、夏之清凉、秋之绚烂的3个目的地",
        "主题鲜明·深度沉浸：美食、摄影、冒险3种主题旅行体验",
        "时间长短·随心选择：周末短途、经典7日、深度15天3种时长",
        "同行伙伴·共享美好：情侣蜜月、亲子家庭、朋友聚会的3种方案",
        "小众秘境·避开人潮：3个鲜为人知但惊艳无比的旅行宝藏地",
        "文化寻根·心灵触动：历史、传统、民俗3类文化深度游",
        "自然疗愈·回归本真：森林、温泉、星空3种健康养生之旅",
        "城市探索·艺术发现：3个城市的建筑、艺术与生活深度体验",
      ]
    };
  },
  mounted() {
  },
  methods: {
    fetchRecommendations() {
      this.loading = true;
      this.error = null;
      const requestMessage = this.generateRandomRequestMessage();
      this.$axios
          .get(`http://localhost:8181/recommended?msg=${requestMessage}`)
          .then((response) => {
            this.loading = false;
            if (response.data && Array.isArray(response.data)) {
              this.recommendedItems = response.data;
            }
          });
    },

    generateRandomRequestMessage() {
      const randomIndex = Math.floor(Math.random() * this.themes.length);
      const currentTheme = this.themes[randomIndex];
      return `用户请求：${currentTheme}。请严格按照JSON数组格式返回3个旅游推荐产品，每个对象包含以下字段：id、image、title、description、duration、rating、groupSize、price。图片请使用真实的Unsplash旅游图片URL。确保直接返回数组，不要嵌套在其他字段中。`;
    },

    refreshRecommendations() {
      this.fetchRecommendations();
    },
  },
};
</script>

<style scoped>
.section {
  padding: 80px 0;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  margin-top: -60px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 48px;
}

.header-content {
  flex: 1;
}

.section-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}

.section-subtitle {
  font-size: 1.125rem;
  color: #6b7280;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}

.refresh-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.3);
}

.refresh-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.refresh-icon {
  transition: transform 0.3s ease;
}

.refresh-btn:hover:not(:disabled) .refresh-icon {
  transform: rotate(180deg);
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 24px;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 3px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.recommended-container {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
}

.recommended-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  cursor: pointer;
}

.recommended-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
}

.card-image-container {
  height: 240px;
  position: relative;
}

.recommended-image {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  position: relative;
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to bottom, transparent 50%, rgba(0, 0, 0, 0.5));
}

.duration-badge {
  position: absolute;
  bottom: 16px;
  right: 16px;
  background: rgba(255, 255, 255, 0.95);
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 0.875rem;
  font-weight: 600;
  color: #1f2937;
}

.recommended-content {
  padding: 24px;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.recommended-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  line-height: 1.4;
}

.rating-container {
  display: flex;
  align-items: center;
  gap: 4px;
}

.rating-stars {
  color: #fbbf24;
  font-size: 1rem;
}

.rating-value {
  font-size: 0.875rem;
  font-weight: 600;
  color: #6b7280;
}

.recommended-description {
  color: #6b7280;
  font-size: 0.95rem;
  line-height: 1.6;
  margin-bottom: 20px;
  height: 4.8em;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.recommended-meta {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #6b7280;
  font-size: 0.875rem;
}

.meta-icon {
  opacity: 0.7;
}

.recommended-price {
  border-top: 1px solid #e5e7eb;
  padding-top: 20px;
}

.price-and-reason {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price-container {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.price-prefix {
  font-size: 0.875rem;
  color: #6b7280;
}

.current-price {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1f2937;
}

.price-unit {
  font-size: 0.875rem;
  color: #6b7280;
  margin-left: 2px;
}

.btn-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: white;
  animation: spin 1s ease-in-out infinite;
  margin-right: 8px;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 24px;
  text-align: center;
}

.error-icon {
  margin-bottom: 8px;
}

.error-message {
  color: #ef4444;
  font-size: 1.1rem;
  font-weight: 500;
}

.retry-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.retry-btn:hover {
  background: #2563eb;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}

.retry-icon {
  transition: transform 0.3s ease;
}

.retry-btn:hover .retry-icon {
  transform: rotate(180deg);
}

@media (max-width: 768px) {
  .section {
    padding: 60px 0;
  }

  .section-title {
    font-size: 2rem;
  }

  .recommended-container {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .refresh-btn {
    padding: 10px 20px;
    font-size: 0.9rem;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .recommended-container {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
