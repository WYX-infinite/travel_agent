# 系统架构

## 1. 目标与边界

系统解决的是“在明确事实边界内生成可审计旅行行程”，不是开放式聊天或自治浏览器。模型不能自行访问 URL、执行代码或决定任意工具；外部事实必须经过知识检索或受控工具进入本次白名单，最终版本必须经过 Java 规则校验。

## 2. 组件视图

```text
┌──────────────── Vue 3 ────────────────┐
│ 登录 │ 规划器 │ 任务进度 │ 行程/版本 │ 知识管理 │
└──────────────────┬────────────────────┘
                   │ REST + JWT / SSE
┌──────────────────▼────────────────────┐
│ Spring MVC                            │
│ AuthController                        │
│ TravelPlanningController / PlanController
│ UserTravelPreferenceController        │
│ KnowledgeAdminController              │
└──────┬───────────────┬────────────────┘
       │               │
       │               └─ Knowledge pipeline
       │                  upload → Tika → chunks
       │                  → embedding → local index
       ▼
PlanningTaskService (bounded executor)
       │
       ▼
TravelPlanningOrchestrator
  1 preference merge + requirement validation
  2 knowledge retrieval + controlled tool dispatch
  3 deterministic context assembly
  4 structured generation
  5 Java validation
  6 bounded repair + revalidation
  7 version, metrics and trace persistence
       │
       ├─ MySQL: authoritative persistence
       ├─ Redis: optional cache
       ├─ DashScope-compatible Chat/Embedding
       ├─ Amap POI/geocode/route
       └─ Open-Meteo weather
```

## 3. 模块职责

| 模块 | 职责 | 明确不负责 |
|---|---|---|
| `auth` | 验证码、BCrypt 登录、JWT 签发/解析、USER/ADMIN 隔离、ThreadLocal 清理 | OAuth、会话存储、客户端身份字段 |
| `knowledge.admin` | 上传校验、Tika 解析、文件安全、状态管理、切片预览、重建索引 | 抓取 `sourceUrl`、向用户公开磁盘路径 |
| `knowledge.ingest/index/retrieval` | 切片、Embedding、本地索引、ACTIVE/城市过滤、允许引用集合 | 把检索文本直接视为已证实的行程 |
| `tools` | 工具白名单、参数验证、调用上限、供应商适配、坐标转换、缓存 | 任意 URL、任意工具、模型直连供应商 |
| `planning.memory` | 四类结构化偏好、显式优先、快照和用户隔离 | 保存聊天全文、模型推断写回偏好 |
| `planning.context` | Token 安全估算、分区、确定性裁剪、POI/chunk 白名单 | 静默截断必要内容 |
| `planning.generation/repair` | 严格结构化生成、一次结构重试、单次修正端口 | 业务事实校验 |
| `planning.validation` | 日期、时间、开放时间、路线、预算、引用、适配性等规则 | 改写模型输出 |
| `planning.task/persistence` | 有界异步执行、状态机、版本和用户所有权 | 取消已运行外部调用 |
| `trace` | 固定步骤、耗时、计数、稳定错误码的脱敏审计 | 保存 Prompt、回复、密钥或自由文本异常 |
| `evaluation` | 固定离线 fixture 与显式真实冒烟分离 | 用 Mock 指标代表真实效果 |

## 4. 一次规划的时序

```text
Client        Task API       Executor       Orchestrator      Data/Model       MySQL/Trace
  | POST task    |              |                 |                |                |
  |------------->| freeze prefs |                 |                |                |
  | 202 PENDING  |--persist---->| schedule        |                |                |
  |<-------------|              |---------------->| validate       |                |
  | GET/SSE      |              |                 | retrieve/tools |--------------->|
  |------------->|<------------- trace/status ----|--------------->|                |
  | progress     |              |                 | context+model  |--------------->|
  |<-------------|              |                 | validate       |                |
  |              |              |                 | repair 0..2    |--------------->|
  |              |              |                 | save versions/metrics/trace ---->|
  | GET task     |              |<---------------- final outcome   |                |
  |------------->| COMPLETED / NEEDS_ADJUSTMENT / FAILED                            |
```

### 状态机

```text
PENDING ──claim──> RUNNING ──> COMPLETED
                          ├──> NEEDS_ADJUSTMENT
                          └──> FAILED
```

终态不可重新进入 `RUNNING`；重复写自身状态允许幂等。`COMPLETED` 必须关联正 `planId`。

## 5. 偏好记忆与隐私

长期偏好只保存 `interests`、`pace`、`travelMode`、`accommodationPreference`，按 `user_id` 主键隔离。合并规则：

1. `useSavedPreferences=false` 时不读取/应用长期偏好。
2. 请求字段显式值优先。
3. 显式空值表示清空本次字段，不由长期偏好补回。
4. 仅未提供字段可被长期偏好补全。
5. 任务创建时冻结采用快照，后续偏好修改不改变已创建任务。
6. `specialRequirements`、日期、人数、预算和同行类型从不由长期偏好补全。

偏好 API 不接收或返回 `userId`。跨用户任务和行程统一表现为 not found，避免资源枚举。

## 6. 上下文预算与确定性裁剪

默认上限来自 `application.yml`：模型窗口 32768、估算输入 16000、预留输出 4000、安全余量 2000；知识分区 4000、工具事实 6000、记忆 500 Token；最多 20 个 POI 和 5 个知识切片。

必要内容包括系统 Schema、显式硬约束、修正问题和 required POI，不会被裁剪。可选内容按固定顺序删除：

```text
optional description
  → saved-memory fields（按字段确定顺序）
  → low-relevance knowledge chunks
  → low-priority non-required POIs
  → CONTEXT_BUDGET_EXCEEDED
```

裁剪以完整结构化元素为单位，不截断 JSON；同样输入得到同样边界和原因列表。估算器是保守本地估算，不等同供应商 tokenizer。

## 7. 工具、缓存与事实边界

允许工具：`SEARCH_POI`、`GEOCODE`、`WALKING_ROUTE`、`DRIVING_ROUTE`、`TRANSIT_ROUTE`、`WEATHER_FORECAST`。校验器限制城市一致、参数名/类型、POI 数量、调用上限并拒绝 URL/主机参数。

Redis key 由规范化参数和 SHA-256 摘要组成。只缓存成功响应；缓存读写异常被吞掉并回源，供应商失败不写缓存。缓存命中元数据进入工具执行结果和脱敏 Trace。

## 8. 生成、引用和修正

- 初稿只能返回一个符合固定 Schema 的 JSON 对象；无效结构最多额外调用一次。
- 本轮允许的 POI ID 和知识 chunkId 由裁剪后上下文产生，生成结果不得引用其他 ID。
- 每个可解析版本先持久化，再执行全量规则结论；WARNING 不阻断完成，ERROR 触发修正。
- 规则修正最多 2 次，每次仅发送结构化问题和允许事实，并重新跑全部规则。
- 初稿结构重试与规则修正独立，理论上单任务最多 4 次模型调用。

## 9. Trace 与可观测性

步骤固定为 `REQUIREMENT`、`KNOWLEDGE_RETRIEVAL`、`TOOL_EXECUTION`、`PREPARATION`、`GENERATION`、`VALIDATION`、`REPAIR`、`COMPLETION`。状态只使用稳定枚举和错误码。记录耗时、Token 计数、修正次数、引用/缓存计数等白名单字段；不记录完整 Prompt、完整回复、API Key、Authorization、用户自由文本或堆栈。

SSE 使用持久化 Trace 作为重放源，`Last-Event-ID` 是排他游标；客户端断开不取消后台任务。

## 10. 部署与运行边界

- 本项目不依赖 Docker，也不自动执行数据库 DDL。
- 本地密钥放入被忽略的 `application-local.yml`；部署用环境变量。
- 上传文件和索引默认位于 `data/`，不进入 Git。
- 单机 JSON 索引和有界线程池适合课程/作品集规模，未验证多实例一致性、高并发、灾备和 SLA。
