# Apifox 契约与业务流程测试计划

## 1. 当前状态

OpenAPI 资产已准备：`docs/openapi/travel-agent.yaml`。当前自动化执行环境没有可控制的 Apifox 客户端/项目和用户账号上下文，因此本文件只定义可重复步骤；**不代表四个流程已在 Apifox 通过**。实际结果填写到 `docs/apifox-test-report.md`。

## 2. 导入与环境

1. 在 Apifox 新建项目，导入 `docs/openapi/travel-agent.yaml`，选择覆盖/同步现有同路径接口。
2. 新建本地环境：`baseUrl=http://localhost:8181`。
3. 仅在 Apifox 私有环境中保存以下变量，不导出值：

| 变量 | 说明 |
|---|---|
| `userUsername` / `userPassword` | 普通用户 |
| `adminUsername` / `adminPassword` | 管理员 |
| `userToken` / `adminToken` | 从登录响应临时写入 |
| `captchaId` / `captchaCode` | 当前验证码；code 需要人工识图输入 |
| `documentId` | 上传响应保存 |
| `taskId` / `planId` | 规划流程保存 |

4. 测试文档使用无敏感信息的小型 Markdown/TXT，元数据使用可公开来源。不要上传真实用户材料。
5. 报告导出前清理 Token、密码、Cookie、验证码图片、完整用户数据和本机绝对路径。

## 3. 通用断言

每个 JSON 接口至少断言：

- HTTP 状态码与 OpenAPI 一致。
- Body 是对象，且存在布尔 `success`、`data`、`error`。
- 成功：`success === true && error === null`。
- 失败：`success === false && data === null && error.code` 为非空字符串。
- 必填字段存在且类型正确。
- 枚举值位于声明集合，不把未知值当成功状态。
- 日志和报告中没有 accessToken、密码或 Cookie。

Apifox 后置脚本按实际响应路径保存变量，例如 `pm.environment.set("userToken", response.data.accessToken)`；脚本名称可能随 Apifox 版本不同，必要时用其可视化断言实现同等检查。

## 4. 场景 A：验证码 → 登录 → me

1. `GET /api/auth/captcha`
   - 断言 200、`success=true`。
   - `captchaId` 非空；`imageDataUrl` 以 `data:image/png;base64,` 开头；`expiresInSeconds=300`。
   - 响应头包含禁止缓存语义。
2. 人工读取图片，将 `captchaId/captchaCode` 放入私有环境。
3. `POST /api/auth/login`
   - 断言 200；`tokenType=Bearer`；`accessToken` 非空但不打印。
   - `user.role` 为 `USER` 或 `ADMIN`；保存 `userToken`。
4. `GET /api/auth/me`，Header `Authorization: Bearer {{userToken}}`
   - 断言 200；返回 username 与登录用户一致；不含密码。
5. 负例：不带 Token 调用 `/me`，断言 401、`error.code=UNAUTHORIZED`。

## 5. 场景 B：管理员上传 → ACTIVE → 切片

1. 以管理员重复场景 A，保存 `adminToken` 并断言 `role=ADMIN`。
2. `POST /api/admin/knowledge-documents`，Bearer `adminToken`，multipart：
   - `file`: 小型公开 Markdown/TXT
   - `title`, `city=杭州`, `publisher`, `sourceUrl`, `publishedAt`, `documentType`
3. 断言 201、`data.id` 为正整数、`data.status=ACTIVE`、SHA-256 为 64 位十六进制；保存 `documentId`。
4. `GET /api/admin/knowledge-documents/{{documentId}}`
   - 断言 200、状态 ACTIVE、`chunkCount > 0`，不含磁盘绝对路径或向量。
5. 若部署实现未来改成异步入库，则有界轮询详情至 ACTIVE/FAILED；当前实现成功上传已是 ACTIVE，不需要无限轮询。
6. `GET /api/admin/knowledge-documents/{{documentId}}/chunks?page=0&size=20`
   - 断言 200；分页字段类型正确；`items[0].documentId` 等于 `documentId`；存在非空 `content`。

## 6. 场景 C：普通用户创建任务 → 终态 → 行程

1. 普通用户登录并保存 `userToken`。
2. `POST /api/planning-tasks` 使用未来的杭州两日请求。
   - 断言 202；`taskId > 0`；初始状态 `PENDING`；进度 0。
3. 每 1–2 秒 `GET /api/planning-tasks/{{taskId}}`，最多等待环境约定超时（建议 10 分钟）。
   - 中间状态只能是 `PENDING` 或 `RUNNING`。
   - 终态只能是 `COMPLETED | NEEDS_ADJUSTMENT | FAILED` 且 `progressPercent=100`。
4. 只有 `COMPLETED` 且 `planId` 为正整数时保存 `planId` 并调用 `GET /api/plans/{{planId}}`。
5. 行程详情断言：城市/日期与请求一致；`days` 日期位于范围内；metrics 含分子分母；warnings severity 为 `ERROR|WARNING`；引用/路线缺失时必须是 null/空，不接受伪造字符串。
6. 调用 `/versions`，断言版本号正整数且升序。
7. 如果任务为 `NEEDS_ADJUSTMENT` 或 `FAILED`，按真实结果记录稳定错误码，不能把它改写为通过。

## 7. 场景 D：普通用户访问管理员接口

1. 使用 `userToken` 调用 `GET /api/admin/knowledge-documents`。
2. 断言 HTTP 403。
3. 断言 `success=false`、`data=null`、`error.code=FORBIDDEN`、消息不泄露内部配置。
4. 继续调用 `/api/auth/me`，确认 403 没有清除合法普通用户会话。

## 8. 额外契约负例

- 无 Token、空 Bearer、过期 Token：401。
- 倒置日期、非正人数/预算、未知枚举：400 `VALIDATION_ERROR`。
- 非法 `Last-Event-ID`：400。
- 未知 task/plan 或其他用户资源：404。
- 同一用户已有活动任务时重复创建：409。
- chunks `size > 100`：400。
- 非管理员上传：403；超大或不支持文件：400。

## 9. 证据要求

实际执行后保存脱敏报告到 `docs/apifox-test-report.md`，至少包含执行时间、环境、OpenAPI 文件提交哈希、四个场景的步骤数/通过数/失败数、HTTP 状态和稳定错误码。截图必须来自真实 Apifox 运行，裁掉 Token、密码、Cookie、验证码、路径和真实用户数据。
