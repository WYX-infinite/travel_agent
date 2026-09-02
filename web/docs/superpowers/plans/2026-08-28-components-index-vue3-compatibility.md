# Components 与 Index.vue Vue 3 Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清除 `src/components` 与 `src/views/Index.vue` 中的 Vue 2/Element UI 残留，使整个 Index 组件树可在 Vue 3 与 Element Plus 下独立编译和交互。

**Architecture:** 保留 Options API，只替换不兼容的图标、全局 Axios 和无效父子接口。使用独立测试入口挂载 Index 组件树，使未进入正式路由的页面也能接受构建和浏览器验证。

**Tech Stack:** Vue 3、Element Plus、Vue Router 4、Axios、Node.js built-in test runner、Vue CLI 5

---

## File Map

- Create `tests/components-index-vue3.test.cjs`: 扫描整个目标组件树的 Vue 3 兼容约束。
- Create `tests/fixtures/index-preview-main.js`: 独立挂载 Index 的验证入口。
- Modify `package.json`: 声明区域数据和 Element Plus 图标直接依赖。
- Modify `package-lock.json`: 锁定新增依赖。
- Modify `src/components/userIndex/HeroSection.vue`: 迁移图标并声明事件。
- Modify `src/components/userIndex/RecommendedSection.vue`: 使用应用 Axios 插件。
- Modify `src/components/userIndex/StatsShowcase.vue`: 删除错误的 Vue 2 注释。
- Modify `src/views/Index.vue`: 删除重复挂载点标识和无效父子绑定。
- Verify `src/components/Header.vue`, `src/components/Footer.vue`, `src/components/userIndex/DataShowcase.vue`: 已兼容 Vue 3，不修改。

### Task 1: 建立全组件树回归测试

**Files:**
- Create: `tests/components-index-vue3.test.cjs`

- [ ] **Step 1: 创建失败测试**

```js
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const projectRoot = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

const componentFiles = [
  'src/components/Header.vue',
  'src/components/Footer.vue',
  'src/components/userIndex/DataShowcase.vue',
  'src/components/userIndex/HeroSection.vue',
  'src/components/userIndex/RecommendedSection.vue',
  'src/components/userIndex/StatsShowcase.vue',
  'src/views/Index.vue'
]

const source = componentFiles.map(read).join('\n')
const hero = read('src/components/userIndex/HeroSection.vue')
const recommended = read('src/components/userIndex/RecommendedSection.vue')
const index = read('src/views/Index.vue')

test('component tree contains no Vue 2 or Element UI template APIs', () => {
  assert.doesNotMatch(source, /\.sync|\sslot=|slot-scope|el-icon-/)
  assert.doesNotMatch(source, /(^|[^.$\w])axios\.(get|post)\(/m)
})

test('HeroSection uses Element Plus icons and declares emitted events', () => {
  assert.match(hero, /from ['"]@element-plus\/icons-vue['"]/) 
  assert.match(hero, /emits:\s*\[['"]departure-change['"]\]/)
  for (const icon of ['Location', 'Place', 'Search', 'DataAnalysis']) {
    assert.match(hero, new RegExp(`<${icon}\\s*/>`))
  }
})

test('RecommendedSection uses the installed Axios plugin', () => {
  assert.match(recommended, /this\.\$axios\.get\(/)
})

test('Index uses only child interfaces that are actually implemented', () => {
  assert.doesNotMatch(index, /id="app"/)
  assert.doesNotMatch(index, /:user-name|:user-stats|@search|:recommended-items|@view/)
})

test('runtime dependencies are declared directly', () => {
  const packageJson = JSON.parse(read('package.json'))
  assert.ok(packageJson.dependencies['@element-plus/icons-vue'])
  assert.ok(packageJson.dependencies['element-china-area-data'])
})
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `npm test`

Expected: 新测试因 `el-icon-*`、裸 `axios.get`、无效 Index 绑定和缺少直接依赖而失败；已有测试继续通过。

### Task 2: 声明组件直接依赖

**Files:**
- Modify: `package.json`
- Modify: `package-lock.json`

- [ ] **Step 1: 安装运行时依赖**

Run: `npm install @element-plus/icons-vue@^2.3.2 element-china-area-data`

Expected: 退出码为 0，两个包出现在 `dependencies`，锁文件同步更新。

- [ ] **Step 2: 检查依赖树**

Run: `npm ls @element-plus/icons-vue element-china-area-data --depth=0`

Expected: 两个包都作为项目顶层依赖列出且无 `invalid` 或 `missing`。

### Task 3: 迁移 HeroSection.vue

**Files:**
- Modify: `src/components/userIndex/HeroSection.vue`

- [ ] **Step 1: 替换模板中的旧图标**

```vue
<el-icon><Location /></el-icon>
<el-icon><Place /></el-icon>
<template #prefix>
  <el-icon><Search /></el-icon>
</template>
<el-icon><Search /></el-icon>
<el-icon><Location /></el-icon>
<el-icon><DataAnalysis /></el-icon>
```

按原出现顺序分别替换 `el-icon-location-outline`、`el-icon-place`、输入框内 `el-icon-search`、按钮内 `el-icon-search`、`el-icon-location` 和 `el-icon-data-analysis`。

- [ ] **Step 2: 导入并注册图标，声明事件**

```js
import { DataAnalysis, Location, Place, Search } from '@element-plus/icons-vue'
import userIndexImg from '@/assets/userIndex/background.png'
import { regionData } from 'element-china-area-data'

export default {
  name: 'HeroSection',
  components: {
    DataAnalysis,
    Location,
    Place,
    Search
  },
  emits: ['departure-change'],
```

保留该对象后续现有 `props`、`data`、`computed`、`watch`、生命周期和 `methods` 内容。

### Task 4: 迁移 Axios、Index 接口与注释

**Files:**
- Modify: `src/components/userIndex/RecommendedSection.vue`
- Modify: `src/components/userIndex/StatsShowcase.vue`
- Modify: `src/views/Index.vue`

- [ ] **Step 1: 修改推荐请求**

```js
this.$axios
  .get(`http://localhost:8181/recommended?msg=${requestMessage}`)
  .then((response) => {
    this.loading = false
    if (response.data && Array.isArray(response.data)) {
      this.recommendedItems = response.data
    }
  })
```

- [ ] **Step 2: 将 Index 模板简化为真实子组件接口**

```vue
<template>
  <div class="travel-platform" style="margin-top: 70px">
    <HeroSection />
    <RecommendedSection />
    <DataShowcase />
    <StatsShowcase />
  </div>
</template>
```

- [ ] **Step 3: 将 Index 脚本简化为组件注册**

```js
import HeroSection from '@/components/userIndex/HeroSection.vue'
import RecommendedSection from '@/components/userIndex/RecommendedSection.vue'
import DataShowcase from '@/components/userIndex/DataShowcase.vue'
import StatsShowcase from '@/components/userIndex/StatsShowcase.vue'

export default {
  name: 'TravelPlatformHome',
  components: {
    HeroSection,
    RecommendedSection,
    DataShowcase,
    StatsShowcase
  }
}
```

- [ ] **Step 4: 删除 StatsShowcase 中错误的 Vue 2 注释**

保留以下实际代码，不增加替代注释：

```js
handleExplore() {
  this.$router.push('/suggest')
}
```

- [ ] **Step 5: 运行测试并确认 GREEN**

Run: `npm test`

Expected: 所有测试通过，0 项失败。

### Task 5: 独立编译与浏览器验证

**Files:**
- Create: `tests/fixtures/index-preview-main.js`
- Generated: `dist-index-preview/`

- [ ] **Step 1: 创建 Index 测试入口**

```js
import { createApp } from 'vue'
import Index from '../../src/views/Index.vue'
import router from '../../src/router'
import axiosPlugin from '../../src/plugins/axios'
import elementPlugin from '../../src/plugins/element'

const app = createApp(Index)

app.use(router)
app.use(axiosPlugin)
app.use(elementPlugin)
app.mount('#app')
```

- [ ] **Step 2: 独立生产构建 Index 组件树**

Run: `npx vue-cli-service build --dest dist-index-preview tests/fixtures/index-preview-main.js`

Expected: 退出码为 0，构建完整覆盖 Index 及其四个子组件。

- [ ] **Step 3: 启动独立预览**

Run: `npx vue-cli-service serve --port 8080 tests/fixtures/index-preview-main.js`

Expected: 编译成功并显示 `http://localhost:8080/`。

- [ ] **Step 4: 浏览器检查**

打开 `http://localhost:8080/`，确认首页四个区块渲染、Element Plus 图标可见，控制台无 Vue 2 API、缺失依赖或 extraneous attributes/listeners 错误。

- [ ] **Step 5: 停止预览服务并清理生成目录**

停止本次启动的 Node 开发服务器。解析 `dist-index-preview` 的绝对路径并确认它位于项目根目录内且不是链接后，删除该生成目录；测试入口保留用于后续验证。

## Repository Note

当前项目不是 Git 仓库，因此计划不包含提交、分支或 worktree 操作。
