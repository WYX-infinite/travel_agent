<template>
  <div class="login-container">
    <div class="login-left">
      <img :src="leftImg" alt="插画"/>
    </div>
    <div class="login-right">
      <el-form ref="loginForm" :model="form" class="login-form" autocomplete="on" label-position="left" :rules="rules">
        <div class="title-container">
          <h3 class="title">旅行智能体平台</h3>
        </div>
        <el-form-item prop="account">
          <el-input v-model="form.account" placeholder="请输入账号"></el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="请输入密码" type="password" show-password></el-input>
        </el-form-item>
        <el-form-item prop="captcha">
          <el-input v-model="form.captcha" placeholder="请输入验证码">
            <template #suffix>
              <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img"/>
            </template>
          </el-input>
        </el-form-item>

        <p class="tips" @click="openRegisterDialog">注册账号</p>

        <el-button type="primary" style="width:100%;margin-bottom:30px;" :disabled="disabledStatus" @click="handleLogin">
          登入
        </el-button>
      </el-form>
    </div>
    <div class="login-bottom">
      <p>Copyright © 2026 Java@AI Agent</p>
    </div>

    <el-dialog v-model="dialogVisible" title="用户注册" width="600px" append-to-body @close="resetRegisterForm">
      <el-form ref="registerForm" :model="registerForm" :rules="registerRules" label-width="80px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="账号" prop="username">
              <el-input v-model="registerForm.username" placeholder="请输入账号"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input type="password" v-model="registerForm.password" placeholder="请输入密码"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="registerForm.name" placeholder="请输入姓名"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年龄" prop="age">
              <el-input-number v-model="registerForm.age" :min="1" :max="150" placeholder="请输入年龄"></el-input-number>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="registerForm.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input type="textarea" :rows="3" v-model="registerForm.address" placeholder="请输入地址"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="resetRegisterForm">取 消</el-button>
          <el-button type="primary" @click="handleRegister" :loading="registering">注 册</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import leftImg from "@/assets/login/leftImg.png";

export default {
  name: "loginPage",
  data() {
    return {
      leftImg: leftImg,
      form: {
        account: "",
        password: "",
        captcha: "",
      },
      disabledStatus: true,
      captchaUrl: "http://localhost:8181/captcha",
      rules: {
        account: [
          { required: true, message: "请输入账号", trigger: "blur" },
          {
            min: 3,
            max: 30,
            message: "所有登入账号都为三位以上有效字符",
            trigger: "blur",
          },
        ],
        password: [
          { required: true, message: "请输入密码", trigger: "blur" },
          { min: 6, message: "密码最小为六位有效字符", trigger: "blur" },
        ],
        captcha: [
          { required: true, message: "请输入验证码", trigger: "blur" },
          { min: 4, max: 4, message: "验证码为4位字符", trigger: "blur" },
        ],
      },
      dialogVisible: false,
      registerForm: {
        username: "",
        password: "",
        name: "",
        age: 22,
        gender: "",
        address: "",
      },
      registerRules: {
        username: [
          { required: true, message: "请输入账号", trigger: "blur" },
          {
            min: 3,
            max: 20,
            message: "账号长度在 3 到 20 个字符",
            trigger: "blur",
          },
        ],
        password: [
          { required: true, message: "请输入密码", trigger: "blur" },
          { min: 6, message: "密码长度至少 6 位", trigger: "blur" },
        ],
        name: [{ required: true, message: "请输入姓名", trigger: "blur" }],
        age: [
          { required: true, message: "请输入年龄", trigger: "blur" },
          { type: "number", message: "年龄必须为数字值", trigger: "blur" },
        ],
        gender: [{ required: true, message: "请选择性别", trigger: "change" }],
        address: [{ required: true, message: "请输入地址", trigger: "blur" }],
      },
      // MODIFIED: 初始化注册状态标志
      registering: false,
    };
  },
  mounted() {
    this.butDisabledStats();
    this.refreshCaptcha();
  },
  watch: {
    "form.account": {
      handler() {
        this.butDisabledStats();
      },
      immediate: true,
    },
    "form.password": {
      handler() {
        this.butDisabledStats();
      },
      immediate: true,
    },
    "form.captcha": {
      handler() {
        this.butDisabledStats();
      },
      immediate: true,
    },
  },
  methods: {
    butDisabledStats() {
      const isAccountValid =
          this.form.account.length >= 3 && this.form.account.length <= 30;
      const isPwdValid = this.form.password.length >= 6;
      const isCaptchaValid = this.form.captcha.length === 4;
      this.disabledStatus = !(isAccountValid && isPwdValid && isCaptchaValid);
    },
    refreshCaptcha() {
      this.captchaUrl = `http://localhost:8181/captcha?timestamp=${new Date().getTime()}`;
    },
    handleLogin() {
      localStorage.removeItem('user')
      this.$refs.loginForm.validate(async (valid) => {
        if (valid) {
          this.$axios.post(
              "http://localhost:8181/login",
              {
                username: this.form.account,
                password: this.form.password,
                captcha: this.form.captcha,
              },
              {
                headers: { "Content-Type": "application/json;charset=utf-8" },
                withCredentials: true,
              }
          ).then((response) => {
            if (response.data.code == -1) {
              this.$message({
                message: response.data.msg,
                type: "error",
                showClose: true,
                duration: 3000,
              });
              this.refreshCaptcha();
            } else {
              this.$message({
                message: `欢迎回来，${response.data.data.username}`,
                type: "success",
                showClose: true,
                duration: 1000,
                onClose: () => {
                  localStorage.setItem(
                      "user",
                      JSON.stringify(response.data.data)
                  );
                  this.$router.push("/index");
                },
              });
            }
          });
        } else {
          this.$message({
            message: "请正确填写登录信息",
            type: "warning",
            showClose: true,
            duration: 3000,
          });
          return false;
        }
      });
    },
    openRegisterDialog() {
      this.dialogVisible = true;
    },
    resetRegisterForm() {
      this.dialogVisible = false;
      this.$nextTick(() => {
        if (this.$refs.registerForm) {
          this.$refs.registerForm.resetFields();
        }
      });
    },
    handleRegister() {
      // 禁用按钮，防止重复提交
      this.registering = true;
      this.$refs.registerForm.validate(async (valid) => {
        if (valid) {
          this.$axios.post(
              "http://localhost:8181/user/add",
              this.registerForm,
              {
                headers: { "Content-Type": "application/json;charset=utf-8" },
                withCredentials: true,
              }
          ).then((response) => {
            this.registering = false;
            if (response.data == true) {
              this.$message({
                message: response.data.message || "注册成功",
                type: "success",
              });
              // MODIFIED: 注册成功后，调用重置表单方法
              this.resetRegisterForm();
            } else {
              this.$message({
                message: response.data.message || "注册失败，请稍后重试",
                type: "error",
              });
            }
          });
        } else {
          this.$message({
            message: "请完整填写注册信息",
            type: "warning",
          });
          this.registering = false;
          return false;
        }
      });
    },
  },
};
</script>

<style>
/* 兼容-webkit内核浏览器光标颜色 */
@supports (-webkit-mask: none) and (not (cater-color: black)) {
  .login-right .el-input input {
    color: black;
  }
}

/* 外层容器样式 */
.login-right .el-input {
  display: inline-block;
  height: 47px;
  width: 85%;
}

/* input输入框核心样式 */
.login-right .el-input input {
  background: transparent;
  border: 0px;
  border-radius: 0px;
  padding: 12px 5px 12px 10px;
  color: black;
  height: 47px;
  caret-color: black;
}

/* 解决input自动填充背景色问题 */
.login-right .el-input input:-webkit-autofill {
  box-shadow: 0 0 0px 1000px #fff inset !important;
  -webkit-text-fill-color: black !important;
}

/* 表单项样式 */
.login-right .el-form-item {
  border-bottom: 1px solid #c4c4c4;
  background: transparent;
  color: #454545;
}
</style>
<style scoped>
/* 登录容器主样式 */
.login-container {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-image: url("../assets/login/backgroundImg.png");
  background-size: 100% 100%;
  display: flex;
}

.login-container .login-left {
  width: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.login-container .login-left img {
  width: 100%;
  display: inline-block;
}

.login-container .login-right {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 50%;
  min-height: 100%;
}

.login-container .login-right .el-button {
  font-size: large;
  font-weight: bold;
}

.login-container .login-bottom {
  position: absolute;
  bottom: 0px;
  right: 16px;
  color: black;
}

/* 右侧登录区独立样式 */
.login-right {
  min-height: 100%;
  width: 100%;
  overflow: hidden;
}

.login-right .login-form {
  position: relative;
  width: 520px;
  max-width: 100%;
  padding: 0 35px 0;
  margin: 0 auto;
  overflow: hidden;
}

.login-right .tips {
  color: #a4a4a4;
  margin-top: -12px;
  text-align: right;
  margin-bottom: 36px;
  cursor: pointer;
  transition: color 0.3s;
}

.login-right .tips:hover {
  color: #409eff;
}

.login-right .svg-container {
  padding: 6px 5px 6px 15px;
  color: #889aa4;
  vertical-align: middle;
  width: 30px;
  display: inline-block;
}

.login-right .title-container {
  position: relative;
}

.login-right .title-container .title {
  font-size: 26px;
  color: #494949;
  margin: 0px auto 40px auto;
  text-align: center;
  font-weight: bold;
}

.login-right .captcha-img {
  height: 32px;
  border-radius: 4px;
  margin-right: 5px;
  cursor: pointer;
}
</style>
