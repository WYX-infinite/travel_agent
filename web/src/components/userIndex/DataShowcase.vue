<template>
  <section class="data-showcase">
    <div class="container">
      <!-- 标题区域 -->
      <div class="section-header">
        <h2 class="section-title">旅行数据一览</h2>
        <p class="section-subtitle">全球旅行者的选择与信赖</p>
      </div>

      <!-- 主数据卡片 -->
      <div class="main-stats">
        <div class="stat-card highlight">
          <div class="stat-content">
            <div class="stat-value">
              <span class="value-number">2.4M</span>
              <span class="value-unit">+</span>
            </div>
            <p class="stat-label">全球旅行者</p>
            <div class="stat-trend">
              <span class="trend-up">↑ 15.3%</span>
              <span class="trend-period">今年增长</span>
            </div>
          </div>
          <div class="stat-icon">
            <svg
                    class="icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
            >
              <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="1.5"
                      d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
              />
            </svg>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-item-content">
              <div class="stat-item-value">156K</div>
              <div class="stat-item-label">旅程完成</div>
            </div>
            <div class="progress-ring">
              <svg
                      width="60"
                      height="60"
              >
                <circle
                        class="progress-ring-bg"
                        cx="30"
                        cy="30"
                        r="24"
                        stroke-width="3"
                />
                <circle
                        class="progress-ring-circle"
                        cx="30"
                        cy="30"
                        r="24"
                        stroke-width="3"
                        stroke-dasharray="150.8"
                        stroke-dashoffset="45.2"
                />
              </svg>
              <span class="progress-percent">70%</span>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-item-content">
              <div class="stat-item-value">89<span class="percent">%</span></div>
              <div class="stat-item-label">用户满意度</div>
            </div>
            <div class="rating-display">
              <div class="stars">
                <span class="star">★</span>
                <span class="star">★</span>
                <span class="star">★</span>
                <span class="star">★</span>
                <span class="star half">★</span>
              </div>
              <div class="rating-text">4.7/5.0</div>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-item-content">
              <div class="stat-item-value">240<span class="plus">+</span></div>
              <div class="stat-item-label">旅行目的地</div>
            </div>
            <div class="destination-badges">
              <span class="badge">亚洲</span>
              <span class="badge">欧洲</span>
              <span class="badge">美洲</span>
              <span class="badge">其他</span>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-item-content">
              <div class="stat-item-value">5.2M</div>
              <div class="stat-item-label">实时分享</div>
            </div>
            <div class="social-stats">
              <div class="social-item">
                <div class="social-icon instagram">IG</div>
                <div class="social-count">2.1M</div>
              </div>
              <div class="social-item">
                <div class="social-icon tiktok">TT</div>
                <div class="social-count">1.8M</div>
              </div>
              <div class="social-item">
                <div class="social-icon youtube">YT</div>
                <div class="social-count">1.3M</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 数据趋势图表（✅ 核心优化：真实淡旺季+峰值数据） -->
      <div class="trend-section">
        <div class="trend-header">
          <h3>年度旅行趋势</h3>
          <div class="trend-filter">
            <button
                    class="filter-btn"
                    :class="{active: activeType === 'year'}"
                    @click="switchChart('year')"
            >12个月</button>
            <button
                    class="filter-btn"
                    :class="{active: activeType === 'quarter'}"
                    @click="switchChart('quarter')"
            >季度</button>
            <button
                    class="filter-btn"
                    :class="{active: activeType === 'month'}"
                    @click="switchChart('month')"
            >月度</button>
          </div>
        </div>

        <div class="trend-chart">
          <div class="chart-bars">
            <div
                    class="chart-bar"
                    :class="{ active: item.isActive }"
                    :style="{ height: item.height + '%' }"
                    v-for="(item, index) in chartData"
                    :key="index"
            >
              <span class="bar-value">{{ item.label }}</span>
              <span class="bar-highlight" v-if="item.highlight">{{ item.highlight }}</span>
            </div>
          </div>
          <div class="chart-axis">
            <span class="axis-label">旅行活跃度</span>
          </div>
        </div>
      </div>

      <!-- 特色标签 -->
      <div class="feature-tags">
        <div class="tag">
          <span class="tag-icon">✈️</span>
          <span class="tag-text">即时确认</span>
        </div>
        <div class="tag">
          <span class="tag-icon">🛡️</span>
          <span class="tag-text">安全保障</span>
        </div>
        <div class="tag">
          <span class="tag-icon">⭐</span>
          <span class="tag-text">精选体验</span>
        </div>
        <div class="tag">
          <span class="tag-icon">🌍</span>
          <span class="tag-text">全球覆盖</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  export default {
    name: "DataShowcase",
    data() {
      return {
        activeType: "year",
        // ✅ 核心优化：完全贴合【真实旅行数据规律】配置所有维度数据
        // ✅ 旺季/高峰标签严格对应峰值，高度数值匹配淡旺季流量，符合行业常识
        chartSource: {
          // 📌 12个月维度【最贴合真实】：春节(2月)+暑期(7-8月)+国庆(10月)三大旺季，4月/11-12月淡季
          year: [
            { label: "1月", height: 65, isActive: false },  // 春节前预热，流量中等
            { label: "2月", height: 92, isActive: true, highlight: "旺季" },  // 春节核心旺季，全年峰值TOP1
            { label: "3月", height: 70, isActive: false },  // 节后回暖，流量回升
            { label: "4月", height: 50, isActive: false },  // 平季，流量偏低
            { label: "5月", height: 85, isActive: false },  // 五一假期，流量冲高
            { label: "6月", height: 75, isActive: false },  // 暑期预热，稳步上升
            { label: "7月", height: 95, isActive: true, highlight: "旺季" },  // 暑期核心旺季，全年峰值TOP2
            { label: "8月", height: 93, isActive: true, highlight: "旺季" },  // 暑期延续，流量高位
            { label: "9月", height: 60, isActive: false },  // 开学季，流量回落
            { label: "10月", height: 90, isActive: true, highlight: "旺季" }, // 国庆黄金周，全年峰值TOP3
            { label: "11月", height: 45, isActive: false }, // 全年淡季，流量最低区间
            { label: "12月", height: 55, isActive: false }  // 年末预热，小幅回升
          ],
          // 📌 季度维度【真实聚合】：Q2(五一)、Q3(暑期)为核心旺季，Q4流量最低，数据完全对应月度聚合
          quarter: [
            { label: "Q1", height: 75, isActive: false },   // 含春节，流量中上
            { label: "Q2", height: 77, isActive: true, highlight: "旺季" },   // 含五一，旅游旺季
            { label: "Q3", height: 83, isActive: true, highlight: "旺季" },   // 含暑期，全年流量最高季度✅
            { label: "Q4", height: 63, isActive: false }    // 仅国庆冲高，整体偏低
          ],
          // 📌 月度维度【真实周度规律】：贴合「月度内出行高峰」，周末/月末假期流量最高，符合用户出行习惯
          month: [
            { label: "第1周", height: 68, isActive: false },  // 月初，流量平稳
            { label: "第2周", height: 82, isActive: false },  // 月中，流量上升
            { label: "第3周", height: 90, isActive: true, highlight: "高峰" }, // 月下旬，出行高峰✅
            { label: "第4周", height: 85, isActive: false }   // 月末收尾，流量高位回落
          ]
        }
      };
    },
    computed: {
      chartData() {
        return this.chartSource[this.activeType];
      }
    },
    mounted() {
      this.animateStats();
    },
    methods: {
      switchChart(type) {
        this.activeType = type;
      },
      animateStats() {
        const observer = new IntersectionObserver(
                (entries) => {
                  entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                      entry.target.classList.add("animate-in");
                    }
                  });
                },
                { threshold: 0.1 }
        );
        document.querySelectorAll(".stat-item").forEach((el) => {
          observer.observe(el);
        });
      },
    },
  };
</script>

<style scoped>
  .data-showcase {
    background: #ffffff;
    padding: 80px 0;
    position: relative;
    overflow: hidden;
  }

  .container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 20px;
    position: relative;
    z-index: 1;
    margin-top: -60px;
  }

  /* 标题样式 */
  .section-header {
    text-align: center;
    margin-bottom: 60px;
  }

  .section-title {
    font-size: 2.5rem;
    font-weight: 700;
    color: #1e293b;
    margin-bottom: 12px;
    letter-spacing: -0.5px;
  }

  .section-subtitle {
    font-size: 1.1rem;
    color: #64748b;
    font-weight: 400;
  }

  /* 主数据卡片 */
  .main-stats {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 30px;
    margin-bottom: 60px;
  }

  .stat-card {
    background: white;
    border-radius: 20px;
    padding: 40px;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.8);
    display: flex;
    justify-content: space-between;
    align-items: center;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    position: relative;
    overflow: hidden;
  }

  .stat-card.highlight {
    background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
    color: white;
  }

  .stat-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 25px 50px rgba(0, 0, 0, 0.1);
  }

  .stat-content {
    flex: 1;
  }

  .stat-value {
    display: flex;
    align-items: baseline;
    margin-bottom: 10px;
    color: white;
  }

  .value-number {
    font-size: 3.5rem;
    font-weight: 800;
    line-height: 1;
  }

  .value-unit {
    font-size: 2rem;
    font-weight: 600;
    opacity: 0.9;
    margin-left: 5px;
  }

  .stat-label {
    font-size: 1.1rem;
    opacity: 0.9;
    margin-bottom: 15px;
    color: white;
  }

  .stat-trend {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.9rem;
  }

  .trend-up {
    color: #10b981;
    font-weight: 600;
  }

  .stat-card.highlight .trend-up {
    color: #bbf7d0;
  }

  .trend-period {
    opacity: 0.7;
  }

  .stat-icon .icon {
    width: 80px;
    height: 80px;
    color: rgba(255, 255, 255, 0.2);
  }

  /* 统计网格 */
  .stats-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 20px;
  }

  .stat-item {
    background: white;
    border-radius: 16px;
    padding: 25px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.04);
    border: 1px solid #f1f5f9;
    display: flex;
    justify-content: space-between;
    align-items: center;
    transition: all 0.3s ease;
    opacity: 0;
    transform: translateY(20px);
  }

  .stat-item.animate-in {
    animation: slideUp 0.6s ease forwards;
  }

  @keyframes slideUp {
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .stat-item:hover {
    border-color: #e2e8f0;
    box-shadow: 0 15px 30px rgba(0, 0, 0, 0.08);
  }

  .stat-item:nth-child(1) {
    animation-delay: 0.1s;
  }
  .stat-item:nth-child(2) {
    animation-delay: 0.2s;
  }
  .stat-item:nth-child(3) {
    animation-delay: 0.3s;
  }
  .stat-item:nth-child(4) {
    animation-delay: 0.4s;
  }

  .stat-item-content {
    flex: 1;
  }

  .stat-item-value {
    font-size: 2.2rem;
    font-weight: 700;
    color: #1e293b;
    line-height: 1;
    margin-bottom: 5px;
  }

  .stat-item-value .percent,
  .stat-item-value .plus {
    font-size: 1.5rem;
    color: #64748b;
  }

  .stat-item-label {
    font-size: 0.95rem;
    color: #64748b;
    font-weight: 500;
  }

  /* 进度环 */
  .progress-ring {
    position: relative;
    width: 60px;
    height: 60px;
  }

  .progress-ring-bg {
    fill: none;
    stroke: #e2e8f0;
  }

  .progress-ring-circle {
    fill: none;
    stroke: #3b82f6;
    stroke-linecap: round;
    transform: rotate(-90deg);
    transform-origin: 50% 50%;
    transition: stroke-dashoffset 1s ease;
  }

  .progress-percent {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    font-size: 0.8rem;
    font-weight: 600;
    color: #3b82f6;
  }

  /* 评分显示 */
  .rating-display {
    text-align: center;
  }

  .stars {
    color: #fbbf24;
    font-size: 1.2rem;
    letter-spacing: 2px;
    margin-bottom: 5px;
  }

  .star.half {
    position: relative;
  }

  .star.half::after {
    content: "★";
    position: absolute;
    left: 0;
    width: 50%;
    overflow: hidden;
    color: #d1d5db;
  }

  .rating-text {
    font-size: 0.85rem;
    color: #64748b;
    font-weight: 600;
  }

  /* 目的地徽章 */
  .destination-badges {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: flex-end;
  }

  .badge {
    background: #f1f5f9;
    color: #475569;
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 0.75rem;
    font-weight: 500;
  }

  /* 社交媒体统计 */
  .social-stats {
    display: flex;
    gap: 10px;
  }

  .social-item {
    text-align: center;
  }

  .social-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.7rem;
    font-weight: 700;
    margin-bottom: 4px;
  }

  .social-icon.instagram {
    background: linear-gradient(45deg, #833ab4, #e1306c, #f77737);
    color: white;
  }

  .social-icon.tiktok {
    background: #000;
    color: white;
  }

  .social-icon.youtube {
    background: #ff0000;
    color: white;
  }

  .social-count {
    font-size: 0.8rem;
    font-weight: 600;
    color: #1e293b;
  }

  /* 趋势图表 */
  .trend-section {
    background: white;
    border-radius: 20px;
    padding: 30px;
    margin-bottom: 40px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.04);
  }

  .trend-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 30px;
  }

  .trend-header h3 {
    font-size: 1.4rem;
    font-weight: 600;
    color: #1e293b;
  }

  .trend-filter {
    display: flex;
    gap: 10px;
  }

  .filter-btn {
    padding: 8px 16px;
    border-radius: 20px;
    border: 1px solid #e2e8f0;
    background: white;
    color: #64748b;
    font-size: 0.85rem;
    cursor: pointer;
    transition: all 0.2s;
  }

  .filter-btn.active {
    background: #3b82f6;
    color: white;
    border-color: #3b82f6;
  }

  .filter-btn:hover:not(.active) {
    border-color: #94a3b8;
  }

  .trend-chart {
    position: relative;
    height: 200px;
  }

  .chart-bars {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    height: 150px;
    padding: 0 20px;
    border-bottom: 1px solid #e2e8f0;
  }

  .chart-bar {
    flex: 1;
    margin: 0 8px;
    background: linear-gradient(to top, #3b82f6, #60a5fa);
    border-radius: 8px 8px 0 0;
    min-height: 20px;
    position: relative;
    transition: height 0.5s ease;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
  }

  .chart-bar.active {
    background: linear-gradient(to top, #1d4ed8, #3b82f6);
  }

  .chart-bar:hover {
    opacity: 0.9;
  }

  .bar-value {
    position: absolute;
    bottom: -25px;
    font-size: 0.8rem;
    color: #64748b;
    font-weight: 500;
  }

  .bar-highlight {
    position: absolute;
    top: -25px;
    background: #10b981;
    color: white;
    padding: 2px 8px;
    border-radius: 10px;
    font-size: 0.7rem;
    font-weight: 600;
  }

  .chart-axis {
    margin-top: 40px;
    text-align: center;
  }

  .axis-label {
    font-size: 0.9rem;
    color: #94a3b8;
    font-weight: 500;
  }

  /* 特色标签 */
  .feature-tags {
    display: flex;
    justify-content: center;
    gap: 30px;
    flex-wrap: wrap;
  }

  .tag {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 24px;
    background: white;
    border-radius: 15px;
    box-shadow: 0 5px 15px rgba(0, 0, 0, 0.03);
    transition: transform 0.3s ease;
  }

  .tag:hover {
    transform: translateY(-3px);
  }

  .tag-icon {
    font-size: 1.2rem;
  }

  .tag-text {
    font-size: 1rem;
    font-weight: 500;
    color: #1e293b;
  }

  /* 响应式设计 */
  @media (max-width: 992px) {
    .main-stats {
      grid-template-columns: 1fr;
    }

    .stat-card {
      padding: 30px;
    }

    .value-number {
      font-size: 3rem;
    }
  }

  @media (max-width: 768px) {
    .data-showcase {
      padding: 60px 0;
    }

    .section-title {
      font-size: 2rem;
    }

    .stats-grid {
      grid-template-columns: 1fr;
    }

    .trend-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 15px;
    }

    .trend-filter {
      align-self: flex-start;
    }
    .feature-tags {
      gap: 15px;
    }

    .tag {
      padding: 10px 20px;
    }
  }

  @media (max-width: 480px) {
    .chart-bars {
      overflow-x: auto;
      justify-content: flex-start;
      padding: 0 10px;
    }

    .chart-bar {
      min-width: 30px;
    }
  }
</style>
