<template>
  <div class="travel-platform" style="margin-top: 70px">
    <HeroSection />
    <RecommendedSection />
    <DataShowcase />
    <StatsShowcase />
  </div>
</template>

<script>
import HeroSection from "@/components/userIndex/HeroSection.vue";
import RecommendedSection from "@/components/userIndex/RecommendedSection.vue";
import DataShowcase from "@/components/userIndex/DataShowcase.vue";
import StatsShowcase from "@/components/userIndex/StatsShowcase.vue";

export default {
  name: "TravelPlatformHome",
  components: {
    HeroSection,
    RecommendedSection,
    DataShowcase,
    StatsShowcase,
  },
};
</script>

<style scoped>
/* 全局基础样式 - 核心是统一居中容器和紧凑布局 */
.travel-platform {
  font-family: "Helvetica Neue", Arial, sans-serif;
  color: #333;
  line-height: 1.6;
  max-width: 1400px; /* 限制最大宽度，保证紧凑感 */
  margin: 0 auto; /* 整体居中 */
  padding: 0;
}

/* 通用容器样式 - 所有子组件共享这个居中容器 */
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  width: 100%;
}

/* 通用按钮样式（全局共享） */
.btn {
  padding: 10px 20px;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
  font-size: 0.95rem;
}

.btn-primary {
  background-color: #2196f3;
  color: white;
}

.btn-primary:hover {
  background-color: #0b7dda;
}

.btn-outline {
  background-color: white;
  color: #2196f3;
  border: 1px solid #2196f3;
}

.btn-outline:hover {
  background-color: #f5f9ff;
}

.btn-small {
  padding: 8px 16px;
  font-size: 0.85rem;
}

/* 响应式基础调整 */
@media (max-width: 768px) {
  .travel-platform {
    max-width: 100%;
    padding: 0;
  }

  .container {
    padding: 0 15px;
  }
}
</style>
