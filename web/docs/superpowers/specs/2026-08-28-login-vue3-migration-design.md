# Login.vue Vue 3 迁移设计

## 目标

将 `src/views/Login.vue` 中残留的 Vue 2 与 Element UI 写法迁移为 Vue 3 与 Element Plus 写法，同时保持页面布局、表单字段、校验规则、后端地址和登录注册流程不变。

## 迁移范围

- 使用 `v-model="dialogVisible"` 控制 Element Plus 对话框。
- 将 `slot="suffix"` 和 `slot="footer"` 改为 Vue 3 具名插槽语法。
- 使用 Element Plus 输入框的 `show-password` 能力替代旧图标类与手动密码类型切换。
- 将 Element Plus 单选框的选中值从 `label` 迁移到 `value`。
- 将裸 `axios.post` 改为应用已注册的 `this.$axios.post`。
- 保留 Options API；Options API 在 Vue 3 中仍受支持。

## 验证

静态回归测试禁止上述 Vue 2 写法再次出现。生产构建必须成功。浏览器中点击“注册账号”后必须出现“用户注册”对话框，控制台不得出现阻止交互的错误。

