<template>
  <nav class="travel-nav">
    <div class="nav-container">
      <router-link to="/" class="nav-logo">TravelAgent</router-link>
      <ul class="nav-list">
        <li class="nav-item">
          <router-link to="/index" class="nav-link">首页</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/planner" class="nav-link">旅行规划</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/collect" class="nav-link">我的收藏</router-link>
        </li>
        <li class="nav-item">
          <router-link to="/routePlan" class="nav-link">路线推荐</router-link>
        </li>
<!--        <li class="nav-item">-->
<!--          <router-link-->
<!--            to="/map"-->
<!--            class="nav-link"-->
<!--          >酒店推荐</router-link>-->
<!--        </li>-->
        <li class="nav-item">
          <router-link to="/login" class="nav-link">退出</router-link>
        </li>
      </ul>
    </div>
  </nav>
</template>

<script>
export default {
  name: "TravelNavbar"
};
</script>

<style scoped>
.travel-nav {
  background-color: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
}

.nav-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
  height: 64px;
}

.nav-logo {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2c3e50;
  text-decoration: none;
  font-family: "Helvetica Neue", Arial, sans-serif;
  transition: color 0.3s ease;
}

.nav-logo:hover {
  color: #3498db;
}

.nav-list {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 0;
}

.nav-item {
  margin-left: 2rem;
}

.nav-link {
  color: #34495e;
  text-decoration: none;
  font-size: 0.95rem;
  font-weight: 500;
  font-family: "Helvetica Neue", Arial, sans-serif;
  transition: color 0.3s ease, border-bottom 0.3s ease;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid transparent;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: #3498db;
  border-bottom-color: #3498db;
}

@media (max-width: 768px) {
  .nav-container {
    padding: 0 1rem;
  }

  .nav-list {
    display: none;
  }
}
</style>
