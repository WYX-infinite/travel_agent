# Login.vue Vue 3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将登录注册页面迁移为可正常交互的 Vue 3 与 Element Plus 页面。

**Architecture:** 保留现有单文件组件和 Options API，只替换不兼容的模板指令、插槽、组件属性与 Axios 访问方式。通过静态回归测试和真实浏览器点击验证迁移结果。

**Tech Stack:** Vue 3、Element Plus、Axios、Node.js built-in test runner

---

### Task 1: 添加迁移回归测试

**Files:**
- Create: `tests/login-vue3.test.cjs`
- Modify: `package.json`

- [ ] 将测试命令改为：

```json
"test": "node --test tests/*.test.cjs"
```

- [ ] 创建 `tests/login-vue3.test.cjs`：

```js
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const login = fs.readFileSync(
  path.resolve(__dirname, '../src/views/Login.vue'),
  'utf8'
)

test('Login.vue uses Vue 3 and Element Plus template APIs', () => {
  assert.match(login, /<el-dialog[\s\S]*v-model="dialogVisible"/)
  assert.match(login, /<template #suffix>/)
  assert.match(login, /<template #footer>/)
  assert.match(login, /show-password/)
  assert.match(login, /<el-radio value="男">/)
  assert.match(login, /<el-radio value="女">/)
  assert.doesNotMatch(login, /\.sync|\sslot=|el-icon-/)
})

test('Login.vue sends requests through the installed Axios plugin', () => {
  const pluginCalls = login.match(/this\.\$axios\.post\(/g) || []

  assert.equal(pluginCalls.length, 2)
  assert.doesNotMatch(login, /(^|[^.$\w])axios\.post\(/m)
})
```

- [ ] 测试同时禁止 `visible.sync`、`slot="..."`、`el-icon-*`、单选框 `label` 和裸 `axios.post`。
- [ ] 运行 `npm test`，确认测试因现有 Vue 2 代码而失败。

### Task 2: 迁移 Login.vue

**Files:**
- Modify: `src/views/Login.vue`

- [ ] 将密码输入框改为以下代码，并删除 `passwordType` 数据和 `clickEyes` 方法：

```vue
<el-input
  v-model="form.password"
  placeholder="请输入密码"
  type="password"
  show-password
></el-input>
```

- [ ] 将验证码后缀改为：

```vue
<template #suffix>
  <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img"/>
</template>
```

- [ ] 将对话框开始标签改为：

```vue
<el-dialog v-model="dialogVisible" title="用户注册" width="600px" append-to-body @close="resetRegisterForm">
```

- [ ] 将页脚改为：

```vue
<template #footer>
  <span class="dialog-footer">
    <el-button @click="resetRegisterForm">取 消</el-button>
    <el-button type="primary" @click="handleRegister" :loading="registering">注 册</el-button>
  </span>
</template>
```

- [ ] 将单选框改为：

```vue
<el-radio value="男">男</el-radio>
<el-radio value="女">女</el-radio>
```

- [ ] 将登录和注册请求中的两个 `axios.post(...)` 都改为 `this.$axios.post(...)`。
- [ ] 运行 `npm test`，确认全部测试通过。

### Task 3: 完整验证

**Files:**
- Verify: `src/views/Login.vue`

- [ ] 运行 `npm run build`，要求退出码为 0。
- [ ] 启动 `npm run serve` 并打开 `http://localhost:8080/`。
- [ ] 点击“注册账号”，确认“用户注册”对话框可见且含取消、注册按钮。
- [ ] 检查浏览器控制台无阻止页面交互的错误。
- [ ] 停止本次验证启动的开发服务器。

## Repository Note

当前项目不是 Git 仓库，因此不执行提交、分支或 worktree 操作。
