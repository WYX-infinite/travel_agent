# Vue 3 与 Element Plus 迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清除 Vue 2 插件残留，将项目稳定迁移为使用 Element Plus 的单实例 Vue 3 应用，并恢复 `localhost:8080` 页面渲染。

**Architecture:** 保留 Vue CLI 5、Vue Router 4、Vuex 4 和现有组件树。应用入口只创建一个 Vue 3 实例，Axios 与 Element Plus 分别作为 Vue 3 插件安装到该实例；静态回归测试负责阻止 Vue 2 API 和依赖再次进入启动链路。

**Tech Stack:** Vue 3、Vue CLI 5、Vue Router 4、Vuex 4、Element Plus、Axios、Node.js built-in test runner

---

## File Map

- Create `tests/vue3-bootstrap.test.cjs`: 检查依赖、插件和入口是否全部使用 Vue 3 约定。
- Modify `package.json`: 添加回归测试命令，移除 Vue 2 插件依赖，添加 Element Plus。
- Modify `package-lock.json`: 由 `npm install` 根据 `package.json` 生成一致的锁文件。
- Modify `src/plugins/element.js`: 提供 Element Plus 的 Vue 3 安装插件。
- Modify `src/plugins/axios.js`: 提供不依赖 Vue 2 全局构造器的 Axios 插件。
- Modify `src/main.js`: 创建、配置并挂载唯一的 Vue 3 应用实例。

### Task 1: 建立 Vue 3 启动链路回归测试

**Files:**
- Create: `tests/vue3-bootstrap.test.cjs`
- Modify: `package.json`

- [ ] **Step 1: 在 `package.json` 的 `scripts` 中添加测试命令**

```json
"scripts": {
  "serve": "vue-cli-service serve",
  "build": "vue-cli-service build",
  "test": "node --test tests/vue3-bootstrap.test.cjs"
}
```

- [ ] **Step 2: 创建会暴露当前 Vue 2 残留的测试**

```js
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const projectRoot = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

test('package dependencies are compatible with Vue 3', () => {
  const packageJson = JSON.parse(read('package.json'))
  const dependencies = packageJson.dependencies || {}
  const devDependencies = packageJson.devDependencies || {}

  assert.match(dependencies.vue, /^\^?3\./)
  assert.ok(dependencies['element-plus'])
  assert.equal(dependencies['element-ui'], undefined)
  assert.equal(devDependencies['vue-cli-plugin-element'], undefined)
  assert.equal(devDependencies['vue-cli-plugin-axios'], undefined)
})

test('main creates and mounts exactly one Vue application', () => {
  const main = read('src/main.js')
  const appCreations = main.match(/createApp\(App\)/g) || []

  assert.equal(appCreations.length, 1)
  assert.match(main, /app\.use\(store\)/)
  assert.match(main, /app\.use\(router\)/)
  assert.match(main, /app\.mount\(['"]#app['"]\)/)
})

test('plugins use Vue 3 installation APIs without Vue 2 globals', () => {
  const elementPlugin = read('src/plugins/element.js')
  const axiosPlugin = read('src/plugins/axios.js')
  const pluginSources = `${elementPlugin}\n${axiosPlugin}`

  assert.match(elementPlugin, /from ['"]element-plus['"]/) 
  assert.match(elementPlugin, /app\.use\(ElementPlus\)/)
  assert.match(axiosPlugin, /app\.config\.globalProperties\.\$axios/)
  assert.doesNotMatch(pluginSources, /Vue\.use|Vue\.prototype|Plugin\.install/)
})
```

- [ ] **Step 3: 运行测试并确认它因当前 Vue 2 残留而失败**

Run: `npm test`

Expected: FAIL；至少报告缺少 `element-plus`，并能报告当前入口创建了两个 `createApp(App)` 实例或插件仍含 Vue 2 API。

### Task 2: 将依赖切换到 Element Plus

**Files:**
- Modify: `package.json`
- Modify: `package-lock.json`

- [ ] **Step 1: 将 `package.json` 更新为 Vue 3 兼容依赖集合**

```json
{
  "name": "travel_agent_web",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "serve": "vue-cli-service serve",
    "build": "vue-cli-service build",
    "test": "node --test tests/vue3-bootstrap.test.cjs"
  },
  "dependencies": {
    "axios": "^0.18.0",
    "core-js": "^3.8.3",
    "element-plus": "^2.11.0",
    "vue": "^3.2.13",
    "vue-router": "^4.0.3",
    "vuex": "^4.0.0"
  },
  "devDependencies": {
    "@vue/cli-plugin-babel": "~5.0.0",
    "@vue/cli-plugin-router": "~5.0.0",
    "@vue/cli-plugin-vuex": "~5.0.0",
    "@vue/cli-service": "~5.0.0"
  },
  "browserslist": [
    "> 1%",
    "last 2 versions",
    "not dead",
    "not ie 11"
  ]
}
```

- [ ] **Step 2: 重新解析依赖并更新锁文件**

Run: `npm install`

Expected: 命令退出码为 0，`package-lock.json` 中加入 Element Plus，并移除顶层 Element UI 与两个 Vue 2 CLI 插件。

- [ ] **Step 3: 检查 Vue 与组件库依赖树**

Run: `npm ls vue element-ui element-plus --depth=1`

Expected: 显示有效的 Vue 3 与 Element Plus；不出现 `invalid: "^2.5.17"`，也不显示顶层 `element-ui`。

### Task 3: 用 Vue 3 API 重写插件和应用入口

**Files:**
- Modify: `src/plugins/element.js`
- Modify: `src/plugins/axios.js`
- Modify: `src/main.js`

- [ ] **Step 1: 将 `src/plugins/element.js` 改为 Element Plus 安装插件**

```js
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

export default {
  install(app) {
    app.use(ElementPlus)
  }
}
```

- [ ] **Step 2: 将 `src/plugins/axios.js` 改为 Vue 3 Axios 插件**

```js
import axios from 'axios'

const http = axios.create({})

http.interceptors.request.use(
  config => config,
  error => Promise.reject(error)
)

http.interceptors.response.use(
  response => response,
  error => Promise.reject(error)
)

export { http }

export default {
  install(app) {
    app.config.globalProperties.axios = http
    app.config.globalProperties.$axios = http
  }
}
```

- [ ] **Step 3: 将 `src/main.js` 改为单实例启动链路**

```js
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import axiosPlugin from './plugins/axios'
import elementPlugin from './plugins/element'

const app = createApp(App)

app.use(store)
app.use(router)
app.use(axiosPlugin)
app.use(elementPlugin)
app.mount('#app')
```

- [ ] **Step 4: 运行回归测试并确认通过**

Run: `npm test`

Expected: 3 tests pass，0 tests fail。

- [ ] **Step 5: 搜索启动源码中的 Vue 2 API**

Run: `rg -n "Vue\\.use|Vue\\.prototype|Plugin\\.install|from ['\"]element-ui['\"]" src package.json`

Expected: 无匹配，`rg` 返回未找到结果的状态。

### Task 4: 构建并验证浏览器页面

**Files:**
- Verify only: `dist/`
- Verify only: development server at `http://localhost:8080/`

- [ ] **Step 1: 执行生产构建**

Run: `npm run build`

Expected: 命令退出码为 0，输出 `Build complete` 或等价成功信息，并生成 `dist` 资源。

- [ ] **Step 2: 启动开发服务器**

Run: `npm run serve`

Expected: 编译成功并显示 `App running at: http://localhost:8080/`。如果 8080 已被占用，先确认占用进程是否为本项目；复用本项目进程，否则使用启动日志给出的实际端口。

- [ ] **Step 3: 验证 HTTP 入口可访问**

Run: `Invoke-WebRequest -Uri 'http://localhost:8080/' -UseBasicParsing | Select-Object StatusCode`

Expected: `StatusCode` 为 `200`。

- [ ] **Step 4: 在浏览器中验证实际渲染结果**

Open: `http://localhost:8080/`

Expected: 页面显示 Vue 标志、欢迎文本和 Element Plus 按钮；页面不是空白，浏览器控制台没有阻止应用挂载的 `ReferenceError`、Vue 2 API 错误或模块加载错误。

- [ ] **Step 5: 停止本次验证启动的开发服务器**

在启动 `npm run serve` 的终端会话中发送 `Ctrl+C`，只停止本次验证进程，不结束用户原有的其他服务。

## Repository Note

`D:\wyx_learn_Java\travel_agent_web` 当前不在 Git 仓库中，因此本计划不包含提交命令。所有修改将通过文件差异和完整验证命令审查。
