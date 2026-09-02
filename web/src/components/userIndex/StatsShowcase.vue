<template>
  <section class="stats-showcase">
    <div class="container">
      <div class="section-header">
        <h2 class="section-title">探索世界的无限可能</h2>
        <p class="section-subtitle">用数据见证每一次旅程的精彩瞬间</p>
      </div>

      <div class="stats-grid">
        <div
          v-for="stat in statsData"
          :key="stat.id"
          class="stat-card"
          @mouseenter="animateCard(stat.id)"
          @mouseleave="resetCard"
          :class="{ 'active': activeCardId === stat.id }"
        >
          <div class="card-decoration"></div>
          <div class="stat-content">
            <div class="stat-icon">
              <i :class="stat.icon"></i>
            </div>
            <h3 class="stat-value">{{ stat.value }}</h3>
            <p class="stat-description">{{ stat.description }}</p>
          </div>
        </div>
      </div>

      <div class="cta-container">
        <button
          class="btn btn-primary"
          @click="handleExplore"
          @mouseenter="animateButton"
          @mouseleave="resetButton"
        >
          <span class="btn-text">立即探索</span>
          <span class="btn-icon">→</span>
        </button>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: "StatsShowcase",
  data() {
    return {
      statsData: [
        {
          id: 1,
          icon: "fas fa-globe-americas",
          value: "200+",
          description: "全球精选目的地",
          color: "#3b82f6",
        },
        {
          id: 2,
          icon: "fas fa-users",
          value: "50K+",
          description: "活跃旅行者用户",
          color: "#10b981",
        },
        {
          id: 3,
          icon: "fas fa-route",
          value: "10K+",
          description: "定制行程路线",
          color: "#8b5cf6",
        },
        {
          id: 4,
          icon: "fas fa-star",
          value: "98%",
          description: "用户满意度好评",
          color: "#f59e0b",
        },
      ],
      activeCardId: null,
      isButtonActive: false,
    };
  },
  methods: {
    animateCard(id) {
      this.activeCardId = id;
    },

    resetCard() {
      this.activeCardId = null;
    },

    animateButton() {
      this.isButtonActive = true;
    },

    resetButton() {
      this.isButtonActive = false;
    },

    handleExplore() {
      this.$router.push("/suggest");
    },
  },
};
</script>

<style scoped>
/* 确保已在项目中全局引入 Font Awesome 或使用 cdn */
@import url("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css");

.stats-showcase {
  padding: 5rem 1.5rem;
  background-color: #ffffff;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
  z-index: 2;
  margin-top: -60px;
}

.section-header {
  margin-bottom: 4rem;
}

.section-title {
  font-size: 2.25rem;
  font-weight: 700;
  color: #111827;
  margin-bottom: 0.75rem;
  letter-spacing: -0.025em;
}

.section-subtitle {
  font-size: 1.125rem;
  color: #6b7280;
  font-weight: 400;
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.6;
}

.stats-grid {
  display: grid;
  /* 修正了原代码中不完整的 grid-template-columns 属性 */
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 2rem;
  margin-bottom: 5rem;
}

.stat-card {
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 1.25rem;
  padding: 2.5rem 2rem;
  position: relative;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.1);
  cursor: default;
  z-index: 1;
}

.card-decoration {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.4s ease;
  z-index: 2;
}

.stat-card:hover {
  border-color: #d1d5db;
  transform: translateY(-8px);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05),
    0 8px 10px -6px rgba(0, 0, 0, 0.02);
}

.stat-card:hover .card-decoration {
  transform: scaleX(1);
}

.stat-card.active .card-decoration {
  transform: scaleX(1);
}

.stat-content {
  position: relative;
  z-index: 3;
}

.stat-icon {
  font-size: 2.5rem;
  color: #374151;
  margin-bottom: 1.5rem;
  transition: color 0.3s ease, transform 0.3s ease;
  display: inline-block;
}

.stat-card:hover .stat-icon {
  color: #3b82f6;
  transform: scale(1.1);
}

.stat-value {
  font-size: 3rem;
  font-weight: 800;
  color: #111827;
  margin-bottom: 0.75rem;
  line-height: 1.1;
  letter-spacing: -0.025em;
}

.stat-description {
  font-size: 1.125rem;
  color: #6b7280;
  font-weight: 500;
  line-height: 1.5;
}

.cta-container {
  margin-top: 2rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1rem 2.5rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: #ffffff;
  background-color: #111827;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.btn::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 1;
}

.btn:hover::before {
  opacity: 1;
}

.btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px -5px rgba(59, 130, 246, 0.3);
}

.btn-text {
  position: relative;
  z-index: 2;
  margin-right: 0.75rem;
}

.btn-icon {
  position: relative;
  z-index: 2;
  transition: transform 0.3s ease;
}

.btn:hover .btn-icon {
  transform: translateX(4px);
}

/* --- 响应式样式 --- */
@media (max-width: 1024px) {
  .stats-grid {
    gap: 1.75rem;
  }

  .stat-card {
    padding: 2.25rem 1.75rem;
  }
}

@media (max-width: 768px) {
  .stats-showcase {
    padding: 3.5rem 1.25rem;
  }

  .section-title {
    font-size: 1.875rem;
  }

  .section-subtitle {
    font-size: 1.05rem;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 1.5rem;
    margin-bottom: 4rem;
  }

  .stat-value {
    font-size: 2.5rem;
  }

  .stat-description {
    font-size: 1rem;
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
    max-width: 320px;
    margin-left: auto;
    margin-right: auto;
  }

  .stat-card {
    padding: 2rem 1.5rem;
  }

  .btn {
    width: 100%;
    max-width: 280px;
  }
}

/* --- 背景装饰 --- */
.stats-showcase::before {
  content: "";
  position: absolute;
  top: 0;
  right: 0;
  width: 300px;
  height: 300px;
  background: radial-gradient(
    circle,
    rgba(59, 130, 246, 0.03) 0%,
    rgba(255, 255, 255, 0) 70%
  );
  z-index: 1;
}

.stats-showcase::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 0;
  width: 200px;
  height: 200px;
  background: radial-gradient(
    circle,
    rgba(139, 92, 246, 0.03) 0%,
    rgba(255, 255, 255, 0) 70%
  );
  z-index: 1;
}
</style>
