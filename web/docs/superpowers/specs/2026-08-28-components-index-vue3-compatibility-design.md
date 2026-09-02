# Components 与 Index.vue Vue 3 兼容迁移设计

## 目标

将 `src/components` 下全部组件及 `src/views/Index.vue` 调整为 Vue 3 与 Element Plus 兼容代码，同时保留 Options API、现有页面布局、接口地址和交互行为。

## 现状

- `Header.vue`、`Footer.vue`、`DataShowcase.vue` 和 `StatsShowcase.vue` 的 Options API 可直接运行在 Vue 3。
- `HeroSection.vue` 仍使用 Element UI 的 `el-icon-*` 类，且未声明其自定义事件。
- `RecommendedSection.vue` 使用未导入的全局 `axios`。
- `Index.vue` 向子组件传入了子组件未声明且未使用的 props/listeners，并重复使用根挂载点的 `id="app"`。
- `HeroSection.vue` 导入的 `element-china-area-data` 未列入项目依赖；Element Plus 图标包目前只是传递依赖。
- `Index.vue` 当前未被路由导入，因此普通应用构建不会覆盖这些组件，需要独立编译验证。

## 设计

### HeroSection.vue

使用 `@element-plus/icons-vue` 的 `Location`、`Place`、`Search` 和 `DataAnalysis` 图标组件替换旧 `el-icon-*` 标签，在 `components` 中注册图标，并声明 `emits: ["departure-change"]`。搜索、最近出发地、级联选择器和外链行为保持不变。

### RecommendedSection.vue

将 `axios.get(...)` 改为应用 Axios 插件提供的 `this.$axios.get(...)`。不改变推荐接口、数据结构和刷新逻辑。

### Index.vue

移除重复的 `id="app"`。删除 HeroSection 和 RecommendedSection 不支持的 props/listeners，并删除只为这些无效绑定服务的父级数据与方法。页面仍按 Hero、推荐、数据、统计四段顺序渲染。

### 其他组件

`Header.vue`、`Footer.vue` 和 `DataShowcase.vue` 已兼容 Vue 3，不做无意义重写。`StatsShowcase.vue` 仅删除将 `this.$router` 错称为 Vue 2 专用 API 的注释；Vue 3 Options API 仍支持 `this.$router`。

### 依赖

将 `@element-plus/icons-vue` 和 `element-china-area-data` 声明为直接运行时依赖，确保独立安装后 HeroSection 可以解析全部导入。

## 验证

1. 先增加失败的静态回归测试，覆盖目标文件中的旧图标、裸 Axios、Vue 2 专用模板语法、事件声明和无效 Index 绑定。
2. 运行完整 `npm test`。
3. 创建 `tests/fixtures/index-preview-main.js`，只用于把 `Index.vue` 挂载到测试页面；使用该入口运行 Vue CLI 构建，确保普通路由未引用时也能覆盖整个组件树。
4. 使用 `vue-cli-service serve tests/fixtures/index-preview-main.js` 启动测试预览，检查页面渲染与控制台；不永久修改路由范围。

## 非目标

- 不将 Options API 重写为 Composition API。
- 不调整路由表。
- 不重做视觉设计或业务流程。
- 不修改后端接口。
