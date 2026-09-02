<template>
  <footer class="footer">
    <div class="container footer-content">
      <!-- 公司介绍 -->
      <div class="footer-section">
        <div class="logo">
          <h2>Travel-Agent</h2>
          <p>发现世界之美</p>
        </div>
        <p class="footer-description">
          我们致力于为您提供最优质的旅行体验，精选全球目的地，个性化定制旅行方案，让每一次旅行都成为难忘的回忆。
        </p>
        <div class="social-links">
          <span class="social-link">📘</span>
          <span class="social-link">📷</span>
          <span class="social-link">🐦</span>
          <span class="social-link">📹</span>
        </div>
      </div>

      <!-- 企业优势 -->
      <div class="footer-section">
        <h3 class="footer-title">企业优势</h3>
        <ul class="footer-links">
          <li><span>全球精选目的地</span></li>
          <li><span>24/7 全天候服务</span></li>
          <li><span>个性化旅行定制</span></li>
          <li><span>专属旅行顾问</span></li>
          <li><span>价格透明保障</span></li>
        </ul>
      </div>

      <!-- 我们的承诺 -->
      <div class="footer-section">
        <h3 class="footer-title">我们的承诺</h3>
        <ul class="footer-links">
          <li><span>品质服务，用心体验</span></li>
          <li><span>安全第一，全程无忧</span></li>
          <li><span>价格透明，拒绝隐形消费</span></li>
          <li><span>客户至上，满意度优先</span></li>
          <li><span>灵活定制，满足个性需求</span></li>
        </ul>
      </div>

      <!-- 联系我们 -->
      <div class="footer-section">
        <h3 class="footer-title">联系我们</h3>
        <ul class="footer-contact">
          <li><span class="contact-icon">📞</span> +86 600-123-8888</li>
          <li><span class="contact-icon">📧</span> java@travelAgent.com</li>
          <li><span class="contact-icon">📍</span> 上海市黄浦区中山南路100号</li>
          <li><span class="contact-icon">🕒</span> 周一至周五 9:00-18:00</li>
        </ul>
      </div>
    </div>

    <!-- 底部版权信息 - 修改为居中 -->
    <div class="footer-bottom">
      <div class="container">
        <p class="copyright-text">© 2026 Java@AI Agent</p>
      </div>
    </div>
  </footer>
</template>

<script>
export default {
  name: "FooterSection",
};
</script>

<style scoped>
.footer {
  background: linear-gradient(135deg, #2c3e50 0%, #1a252f 100%);
  color: white;
  padding: 70px 0 0;
  margin-top: 30px;
  width: 100%;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.footer-content {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 40px;
  padding-bottom: 50px;
}

.footer-section .logo h2 {
  color: #fff;
  font-size: 2rem;
  font-weight: 700;
  margin: 0 0 5px 0;
  letter-spacing: 1px;
}

.footer-section .logo p {
  color: #4fc3f7;
  font-size: 1rem;
  margin: 0 0 20px 0;
  font-weight: 300;
}

.footer-description {
  color: #b0bec5;
  font-size: 0.95rem;
  line-height: 1.7;
  margin-bottom: 25px;
}

.social-links {
  display: flex;
  gap: 15px;
  margin-top: 20px;
}

.social-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  color: #fff;
  font-size: 1.2rem;
  transition: all 0.3s ease;
  cursor: default;
}

.social-link:hover {
  background: rgba(79, 195, 247, 0.2);
  transform: translateY(-2px);
}

.footer-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 25px 0;
  color: white;
  position: relative;
  padding-bottom: 10px;
}

.footer-title::after {
  content: "";
  position: absolute;
  left: 0;
  bottom: 0;
  width: 40px;
  height: 2px;
  background: #4fc3f7;
}

.footer-links {
  list-style: none;
  padding: 0;
  margin: 0;
}

.footer-links li {
  margin-bottom: 12px;
  transition: transform 0.2s ease;
}

.footer-links li:hover {
  transform: translateX(5px);
}

.footer-links span {
  color: #b0bec5;
  font-size: 0.95rem;
  line-height: 1.5;
  cursor: default;
}

.footer-links span:hover {
  color: #4fc3f7;
}

.footer-contact {
  list-style: none;
  padding: 0;
  margin: 0;
}

.footer-contact li {
  margin-bottom: 15px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  color: #b0bec5;
  font-size: 0.95rem;
  line-height: 1.5;
}

.contact-icon {
  font-size: 1.1rem;
  color: #4fc3f7;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}

.footer-bottom {
  background-color: rgba(0, 0, 0, 0.2);
  padding: 25px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

/* 版权信息居中样式 */
.copyright-text {
  color: #90a4ae;
  font-size: 0.9rem;
  margin: 0;
  text-align: center;
}

/* 响应式设计 */
@media (max-width: 992px) {
  .footer-content {
    grid-template-columns: repeat(2, 1fr);
    gap: 35px;
  }
}

@media (max-width: 768px) {
  .footer {
    padding: 50px 0 0;
    margin-top: 60px;
  }

  .footer-content {
    grid-template-columns: 1fr;
    gap: 30px;
    padding-bottom: 40px;
  }

  .footer-section {
    text-align: center;
  }

  .footer-title::after {
    left: 50%;
    transform: translateX(-50%);
  }

  .social-links {
    justify-content: center;
  }

  .footer-contact li {
    justify-content: center;
  }
}
</style>
