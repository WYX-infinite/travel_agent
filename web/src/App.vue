<template>
  <div id="app">
    <Header v-if="$route.meta.requiresHeader"></Header>
    <router-view></router-view>
    <Footer v-if="$route.meta.requiresFooter"></Footer>
  </div>
</template>

<script>


import Footer from "@/components/Footer.vue";
import Header from "@/components/Header.vue";

export default {
  name: 'app',
  components: {
    Header,
    Footer
  }
}
</script>

<style>
#app {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin: 0;
}
</style>
