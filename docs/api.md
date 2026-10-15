# API 使用说明

## 1. 约定

- 本地基址：`http://localhost:8181`
- JSON 响应包装：`{"success":true,"data":...,"error":null}` 或 `{"success":false,"data":null,"error":{...}}`
- 除 `GET /api/auth/captcha`、`POST /api/auth/login` 和 `OPTIONS` 外，所有 `/api/**` 都要求 `Authorization: Bearer <accessToken>`。
- `/api/admin/**` 还要求 JWT 中角色为 `ADMIN`；普通用户得到 HTTP 403 和 `FORBIDDEN`。
- 时间使用 ISO-8601；日期为 `YYYY-MM-DD`。
- 跨用户任务/行程使用 404，而不是暴露资源存在性。

错误结构：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "request validation failed",
    "fields": { "startDate": "..." }
  }
}
```

`fields` 可省略。客户端应判断 HTTP 状态和 `success/error.code`，不要依赖自由文本消息。

## 2. 认证

| 方法 | 路径 | 状态 | 说明 |
|---|---|---:|---|
| GET | `/api/auth/captcha` | 200 | 返回 `captchaId`、data URL 图片、300 秒有效期；响应 `no-store` |
| POST | `/api/auth/login` | 200/401 | 用户名、密码、验证码登录；验证码单次使用 |
| GET | `/api/auth/me` | 200/401 | 返回当前用户安全资料 |

登录请求：

```json
{
  "username": "<username>",
  "password": "<password>",
  "captchaId": "<captchaId>",
  "captchaCode": "<code>"
}
```

登录成功的 `data` 包含 `accessToken`、`tokenType=Bearer`、`expiresAt` 和 `user{id,name,username,role}`。不得把 Token 写入报告或 Git。

## 3. 规划任务

### `POST /api/planning-tasks`

创建异步任务，HTTP 202。身份只来自 JWT。

```json
{
  "destinationCity": "杭州",
  "startDate": "2026-10-20",
  "endDate": "2026-10-21",
  "travelers": 2,
  "totalBudget": 3000,
  "companionType": "COUPLE",
  "interests": ["HISTORY", "FOOD"],
  "pace": "MODERATE",
  "accommodationAddress": "西湖附近",
  "travelMode": "TRANSIT",
  "specialRequirements": "避免过早出发",
  "useSavedPreferences": true
}
```

枚举：

- `companionType`: `SOLO | COUPLE | FAMILY | FRIENDS | SENIORS`
- `interests`: `FOOD | HISTORY | NATURE | PHOTOGRAPHY | SHOPPING`
- `pace`: `RELAXED | MODERATE | COMPACT`
- `travelMode`: `WALKING | TRANSIT | TAXI | DRIVING`

偏好字段三态：字段缺失/`null` 表示允许长期偏好补全，空数组或空串表示本次显式清空，非空值表示显式覆盖。`useSavedPreferences` 缺失时默认为 true。

响应任务状态：`PENDING | RUNNING | COMPLETED | NEEDS_ADJUSTMENT | FAILED`。`COMPLETED` 时必须有 `planId`；终态进度为 100。

### `GET /api/planning-tasks/{taskId}`

查询当前用户任务。典型错误：401、`PLANNING_TASK_NOT_FOUND` 404。

### `GET /api/planning-tasks/{taskId}/events`

`text/event-stream`。可传 `Last-Event-ID` 非负整数断点续传。事件使用持久化 Trace ID，客户端收到终态进度后仍应再查询任务快照确认最终状态。

## 4. 行程

| 方法 | 路径 | 状态 | 说明 |
|---|---|---:|---|
| GET | `/api/plans` | 200 | 当前用户行程摘要 |
| GET | `/api/plans/{planId}` | 200/404 | 当前版本详情、采用偏好、warnings、metrics 和版本摘要 |
| GET | `/api/plans/{planId}/versions` | 200/404 | 全部完整版本，版本号升序 |
| POST | `/api/plans/{planId}/replan` | 202/404/409/503 | 用原请求和当前偏好创建新任务 |

质量比率统一包含 `numerator`、`denominator`、`percentage`；分母为零时 `percentage=null`。活动的 `address`、`nextSegment` 和 `evidence` 在当前持久化模型缺少事实时返回 `null`/空数组，不进行推断。

## 5. 长期偏好

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/travel-preferences` | 无记录时返回空 interests 和其余 null |
| PUT | `/api/user/travel-preferences` | 四字段整体替换；`null` 清除旧值 |
| DELETE | `/api/user/travel-preferences` | 幂等清空 |

PUT 只接受 `interests`、`pace`、`travelMode`、`accommodationPreference` 的业务语义；请求中的未知字段被忽略，但绝不能依赖它们。住宿偏好非空时最多 255 字符。API 不接收 `userId`。

## 6. 管理员知识库

基路径 `/api/admin/knowledge-documents`：

| 方法 | 子路径 | 状态 | 说明 |
|---|---|---:|---|
| GET | `` | 200 | 文档摘要列表 |
| POST | `` | 201 | multipart 上传并同步完成入库；成功响应状态为 ACTIVE |
| GET | `/{id}` | 200/404 | 安全详情 |
| GET | `/{id}/chunks?page=0&size=20` | 200/400/404 | 切片预览，size 最大 100 |
| PATCH | `/{id}/status` | 200/400/404 | 只接受 `ACTIVE` 或 `DISABLED` |
| DELETE | `/{id}` | 200/409/404 | 删除允许状态的文档与切片 |
| POST | `/reindex` | 200 | 以数据库 ACTIVE 白名单重建索引 |

上传 multipart 必填字段：

- `file`: PDF、DOCX、Markdown 或 TXT，默认不超过 10 MB
- `title`, `city`, `publisher`
- `sourceUrl`: HTTP/HTTPS 来源，仅保存不抓取
- `publishedAt`: ISO 日期
- `documentType`: `OFFICIAL_NOTICE | ATTRACTION_GUIDE | TRANSPORT_GUIDE | MANUAL_CURATION`

文档状态：`UPLOADED | INDEXING | ACTIVE | DISABLED | FAILED`。上传成功只在数据库切片和正式索引发布完成后返回 201。

## 7. 常见 HTTP/错误码

| HTTP | code | 含义 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | JSON、字段、日期、枚举或游标非法 |
| 400 | `UPLOAD_TOO_LARGE` | 文件超过配置上限 |
| 401 | `UNAUTHORIZED` | 缺少、无效或过期 Token；或登录失败 |
| 403 | `FORBIDDEN` | 非管理员访问管理接口 |
| 404 | `PLANNING_TASK_NOT_FOUND` / `PLAN_NOT_FOUND` | 不存在或不属于当前用户 |
| 409 | `PLANNING_TASK_ALREADY_ACTIVE` | 用户已有 PENDING/RUNNING 任务 |
| 503 | `PLANNING_QUEUE_FULL` | 有界执行队列已满 |
| 500 | `PREFERENCE_STORAGE_FAILED` | 偏好存储失败 |

知识上传/管理还会返回稳定业务错误码；调用方应以 OpenAPI 中的通用 `ErrorResponse` 解析。

## 8. 完整机器契约

以 [openapi/travel-agent.yaml](openapi/travel-agent.yaml) 为可导入契约。实现是事实源；修改 YAML 不能反向改变生产 API。每次改动后应执行 YAML 解析和 Apifox 导入检查。
