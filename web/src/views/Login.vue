<template>
  <main class="login-page">
    <section class="login-visual" aria-hidden="true">
      <img :src="leftImg" alt="" />
    </section>
    <section class="login-panel">
      <el-form ref="loginForm" :model="form" :rules="rules" class="login-form" @submit.prevent="handleLogin">
        <p class="eyebrow">MULTI-CONSTRAINT TRAVEL AGENT</p>
        <h1>登录旅行智能体</h1>
        <p class="intro">登录后可管理规划历史、长期偏好与异步任务。</p>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

        <el-form-item prop="username">
          <el-input v-model.trim="form.username" autocomplete="username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" autocomplete="current-password" placeholder="密码" type="password" show-password size="large" />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model.trim="form.captchaCode" placeholder="验证码" maxlength="8" size="large" @keyup.enter="handleLogin" />
            <button type="button" class="captcha-button" aria-label="刷新验证码" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码，点击刷新" />
              <span v-else>{{ captchaLoading ? '加载中' : '点击刷新' }}</span>
            </button>
          </div>
        </el-form-item>
        <el-button native-type="submit" type="primary" size="large" class="submit-button" :loading="submitting" :disabled="submitting || !captchaId">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script>
import leftImg from '@/assets/login/leftImg.png'
import session from '../auth/session.cjs'

export default {
  name: 'LoginPage',
  data() {
    return {
      leftImg,
      captchaId: '',
      captchaImage: '',
      captchaLoading: false,
      submitting: false,
      errorMessage: '',
      form: { username: '', password: '', captchaCode: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
        captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
      }
    }
  },
  mounted() {
    this.refreshCaptcha()
  },
  methods: {
    async refreshCaptcha() {
      if (this.captchaLoading) return
      this.captchaLoading = true
      try {
        const captcha = await this.$axios.get('/api/auth/captcha')
        this.captchaId = captcha.captchaId
        this.captchaImage = captcha.imageDataUrl
        this.form.captchaCode = ''
      } catch (error) {
        this.errorMessage = error?.message || '验证码加载失败，请重试。'
      } finally {
        this.captchaLoading = false
      }
    },
    async handleLogin() {
      if (this.submitting) return
      this.submitting = true
      const valid = await this.$refs.loginForm.validate().catch(() => false)
      if (!valid || !this.captchaId) { this.submitting = false; return }
      this.errorMessage = ''
      try {
        const result = await this.$axios.post('/api/auth/login', {
          username: this.form.username,
          password: this.form.password,
          captchaId: this.captchaId,
          captchaCode: this.form.captchaCode
        })
        session.saveAuthSession(sessionStorage, result.accessToken, result.user)
        this.$message.success(`欢迎回来，${result.user.name}`)
        this.$router.replace(session.safeRedirect(this.$route.query.redirect))
      } catch (error) {
        this.errorMessage = error?.message || '登录失败，请检查输入。'
        await this.refreshCaptcha()
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: 1.05fr .95fr; background: #eef4f7; color: #263746; }
.login-visual { display: flex; align-items: center; justify-content: center; padding: 7vw; background: linear-gradient(145deg, #dceef4, #f8fbfc); }
.login-visual img { width: min(640px, 100%); }
.login-panel { display: flex; align-items: center; justify-content: center; padding: 40px; background: #fff; }
.login-form { width: min(430px, 100%); text-align: left; }
.login-form h1 { margin: 8px 0 12px; font-size: clamp(30px, 4vw, 46px); }
.eyebrow { margin: 0; color: #2768a9; font-size: 12px; font-weight: 800; letter-spacing: .09em; }
.intro { margin: 0 0 28px; color: #647681; line-height: 1.7; }
.el-alert { margin-bottom: 18px; }
.captcha-row { width: 100%; display: grid; grid-template-columns: 1fr 132px; gap: 10px; }
.captcha-button { height: 40px; padding: 0; overflow: hidden; border: 1px solid #d7dee3; border-radius: 5px; background: #f6f8f9; cursor: pointer; }
.captcha-button img { width: 100%; height: 100%; object-fit: contain; }
.submit-button { width: 100%; margin-top: 6px; }
@media (max-width: 760px) {
  .login-page { grid-template-columns: 1fr; }
  .login-visual { display: none; }
  .login-panel { padding: 24px; }
}
@media (max-width: 390px) {
  .captcha-row { grid-template-columns: 1fr 108px; }
}
</style>
