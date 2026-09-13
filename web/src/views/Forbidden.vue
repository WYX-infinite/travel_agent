<template>
  <main class="state-page">
    <el-result icon="warning" title="无权访问" sub-title="当前账号没有访问该管理页面的权限。">
      <template #extra><el-button type="primary" @click="$router.replace('/index')">返回首页</el-button></template>
    </el-result>
  </main>
</template>
<script>export default { name: 'ForbiddenPage' }</script>
<style scoped>.state-page { min-height: 70vh; display: grid; place-items: center; padding: 84px 16px 30px; }</style>
