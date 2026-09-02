# Vue 3 与 Element Plus 迁移设计

## 背景

项目核心依赖已经是 Vue 3、Vue Router 4 和 Vuex 4，但同时安装并初始化了仅支持 Vue 2 的 `element-ui`、`vue-cli-plugin-element` 和 `vue-cli-plugin-axios`。入口代码还混用了 Vue 2 的 `Vue.use`、`Vue.prototype` 与 Vue 3 的 `createApp`，并创建了两个应用实例。这些入口阶段的运行时错误会阻止应用挂载，导致浏览器显示空白页面。

## 目标

- 项目保持为 Vue 3 项目。
- 使用支持 Vue 3 的 Element Plus 替代 Element UI。
- 保留现有 Vue CLI、Vue Router、Vuex、Axios、页面和路由结构。
- `localhost:8080` 能正常渲染应用页面。
- 生产构建成功，依赖树中不再存在 Element UI 对 Vue 2 的冲突要求。

## 不在范围内

- 不迁移到 Vite。
- 不重做页面视觉设计。
- 不修改后端接口或业务功能。
- 不进行与启动问题无关的重构。

## 设计

### 依赖

移除 `element-ui`、`vue-cli-plugin-element` 和 `vue-cli-plugin-axios`，添加 `element-plus`。保留 Vue 3、Vue Router 4、Vuex 4、Axios 和现有 Vue CLI 5 构建链路。

### 应用入口

`src/main.js` 只调用一次 `createApp(App)`。同一个应用实例依次安装 Vuex、Vue Router、Axios 插件和 Element Plus，最后挂载到 `#app`。

### Element Plus

`src/plugins/element.js` 导出接收 Vue 3 应用实例的安装函数。该函数通过 `app.use(ElementPlus)` 注册组件，并加载 Element Plus 样式，不再导入默认 `Vue` 或调用 Vue 2 的 `Vue.use`。

### Axios

`src/plugins/axios.js` 保留 Axios 实例和拦截器，但删除 Vue 2 插件生成器遗留的 `Plugin.install`、`Vue.use` 和 `Vue.prototype`。安装函数通过 `app.config.globalProperties` 暴露 `$axios`，同时保留一个统一的 Axios 实例供后续模块直接导入。

### 页面与路由

现有 `App.vue` 中的 `<el-button>` 由 Element Plus 接管。现有 Router、Vuex、视图和组件结构不改变。

## 错误处理

迁移不吞掉启动错误。Axios 响应拦截器继续拒绝错误，让调用方决定如何展示。构建或运行时出现的模块解析错误将直接作为验证失败处理。

## 验证

1. 修改前的回归检查应能识别 Vue 2 专用依赖或入口中的 Vue 2 API，证明当前状态不满足 Vue 3 要求。
2. 更新依赖后运行 `npm ls vue element-ui element-plus`，确认 Vue 版本有效且不再安装 Element UI。
3. 运行 `npm run build`，要求退出码为 0。
4. 启动开发服务器，访问 `http://localhost:8080/`，确认页面内容和 Element Plus 按钮已渲染，且没有阻止挂载的浏览器运行时错误。

## 成功标准

- 应用仅创建一个 Vue 3 实例。
- 源码中不存在启动链路上的 `Vue.use` 或 `Vue.prototype`。
- Element Plus 正常注册并渲染现有 Element 组件。
- 依赖检查、生产构建和本地页面验证全部通过。
