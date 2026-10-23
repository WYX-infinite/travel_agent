# TripGuard｜多约束旅行规划 Agent

一个基于 Java 21、Spring Boot、LangChain4j、MySQL、Redis 和 Vue 3 的旅行规划项目。系统把用户约束、结构化偏好、知识文档和受控数据源组装为有预算的模型上下文；模型只负责生成候选行程，Java 负责白名单、引用、路线、预算和时间规则校验，并在有界次数内自动修正。

> 验证快照：2026-10-15。本仓库的 40 条离线评测全部使用固定虚构 fixture、假模型和假数据源，不能解释为真实模型成功率或线上 SLA。另有一次经授权的小规模真实冒烟，详见“验证结果”。

## 主要能力

- JWT 登录、普通用户/管理员隔离，统一 `ApiResponse`。
- 管理员上传 PDF、DOCX、Markdown、TXT，Tika 解析、切片、Embedding、本地索引和来源元数据管理。
- 高德 POI/路线、Open-Meteo 天气的受控工具调用；Redis 只做可降级缓存。
- 显式输入优先的结构化长期偏好，支持查看、整体替换、清空和单次关闭。
- 固定分区的上下文预算与确定性裁剪；必要内容超限时在调用模型前失败。
- 严格 JSON 解析、POI ID/chunkId 白名单、Java 规则校验、最多 1 次结构重试和 2 次规则修正。
- 持久化异步任务、版本化行程、脱敏 Trace 和 SSE 断点续传。
- 40 条可重复离线评测以及显式隔离的真实服务冒烟 Profile。

## 架构速览

```text
Vue 3 / Apifox
      │ HTTP + JWT / SSE
      ▼
Spring MVC ── Auth / Admin Knowledge / Planning Tasks / Plans / Preferences
      │
      ├─ MySQL：用户、偏好、知识元数据、任务、版本、Trace
      ├─ Knowledge：Tika → chunks → DashScope embedding → 本地索引文件
      └─ Planning Orchestrator
           偏好合并 → 需求校验 → 知识检索 → 受控工具 → 上下文预算
           → DashScope chat → Java 校验 → 最多两次修正 → 版本/Trace
                 │
                 ├─ 高德：POI、地理编码、路线（GCJ-02）
                 ├─ Open-Meteo：天气（请求前转换为 WGS84）
                 └─ Redis：POI/路线/天气缓存；故障时回源或继续降级
```

完整说明见 [docs/architecture.md](docs/architecture.md) 和 [docs/data-sources.md](docs/data-sources.md)。

## 无 Docker 本地启动

### 1. 前置条件

- JDK 21
- MySQL 8.x
- Redis（可选加速依赖；真实冒烟会记录其可用性，但不可达时允许按生产语义回源）
- Node.js 与 npm
- DashScope API Key、高德 Web 服务 API Key（仅真实模型/真实数据源运行需要）

### 2. 初始化数据库

项目不会自动迁移数据库。全新数据库执行 `sql/schema.sql`；完整旧库执行 `sql/upgrade-from-legacy.sql`，不得对同一数据库连续执行两份脚本。先按 [docs/database-setup.md](docs/database-setup.md) 完成只读检查、备份和验收。

### 3. 配置边界

本地开发可新建被 Git 忽略的 `src/main/resources/application-local.yml`：

```yaml
travel:
  local-secrets:
    db-username: <local-user>
    db-password: <local-password>
    jwt-secret: <base64-secret-at-least-32-bytes>
    dashscope-api-key: <local-key>
    amap-web-api-key: <local-key>
```

部署环境必须使用环境变量，不把密钥放入镜像、仓库或启动参数：

| 变量 | 用途 |
|---|---|
| `TRAVEL_DB_USERNAME` / `TRAVEL_DB_PASSWORD` | MySQL |
| `TRAVEL_REDIS_HOST` / `TRAVEL_REDIS_PORT` / `TRAVEL_REDIS_PASSWORD` | Redis |
| `TRAVEL_JWT_SECRET` / `TRAVEL_JWT_ISSUER` | JWT |
| `DASHSCOPE_API_KEY` | Chat/Embedding |
| `AMAP_WEB_API_KEY` | 高德 Web 服务 |
| `TRAVEL_CHAT_MODEL` / `TRAVEL_EMBEDDING_MODEL` | 模型名覆盖 |

任何 `application-local.yml`、`.env`、Token、Cookie、密码、完整 Prompt、完整模型回复都不得提交。

### 4. 启动后端

```powershell
$env:JAVA_HOME='<JDK 21 路径>'
.\mvnw.cmd spring-boot:run
```

后端默认监听 `http://localhost:8181`。默认 Profile 为 `local`；生产部署应显式设置 `SPRING_PROFILES_ACTIVE` 并提供环境变量。

### 5. 启动前端

```powershell
cd web
npm ci
npm run serve
```

开发服务器默认把 `/api` 代理到 `http://localhost:8181`；可用 `VUE_APP_API_TARGET` 覆盖。

## 规划执行流程

1. Controller 只从 JWT 上下文获取 `userId`，请求体不能指定身份。
2. `useSavedPreferences` 默认为 `true`；长期偏好只补全未提供字段，显式值或显式清空优先。读取失败不跨用户读取，并记录安全降级。
3. Java 先校验城市、日期、人数、预算和枚举；无效需求不调用模型。
4. 知识检索按城市和 ACTIVE 状态过滤；工具计划先经过白名单、参数、目标城市、URL 和调用次数校验。
5. 上下文按系统 Schema、硬约束、上一版本/问题、工具事实、偏好记忆、知识引用和可选描述分区。
6. 超预算时依次删除可选描述、记忆字段、低相关知识和低优先级非必需 POI；必要内容仍超限则返回 `CONTEXT_BUDGET_EXCEEDED`。
7. 模型输出经过严格 JSON 解析；解析失败最多额外重试 1 次。输出的 POI ID 与 chunkId 必须来自本次白名单。
8. Java 对每个可解析版本执行规则校验并保存版本；有 ERROR 时最多自动修正 2 次，每次重新执行全部规则。
9. 异步任务按 `PENDING → RUNNING → COMPLETED | NEEDS_ADJUSTMENT | FAILED` 流转，阶段写入脱敏 Trace，SSE 只推送持久化进度。

## 数据源和失败边界

- 高德负责 POI、地理编码和路线；供应商错误映射为稳定错误码，不把模型猜测当事实。
- Open-Meteo 负责天气；天气缺失可形成 warning/降级，不伪造天气。
- Redis 不是事实源。读取或写入失败时直接调用供应商；只有成功响应才缓存。
- 本地知识库只使用管理员上传且状态为 ACTIVE 的文档，`sourceUrl` 仅保存，不由服务端抓取。
- MySQL 是用户、任务、版本、知识元数据和 Trace 的持久化事实源；项目不自动执行 DDL。

详见 [docs/data-sources.md](docs/data-sources.md)。

## API 与 Apifox

- 人类可读接口说明：[docs/api.md](docs/api.md)
- OpenAPI 3.0：[docs/openapi/travel-agent.yaml](docs/openapi/travel-agent.yaml)
- Apifox 流程与断言：[docs/apifox-test-plan.md](docs/apifox-test-plan.md)
- 当前执行状态：[docs/apifox-test-report.md](docs/apifox-test-report.md)

把 OpenAPI YAML 导入 Apifox 后，按测试计划配置环境变量和四个场景。Swagger UI 不是本项目的必需依赖，也不能替代 Apifox 流程测试。

## 验证结果

2026-10-15 在 Windows、JDK 21 上执行：

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
cd web
npm ci
npm test
npm run build
```

- 后端：446/446 通过，失败 0、错误 0、跳过 0；`clean test` 与 `package` 均成功。
- 前端：43/43 通过，失败 0、跳过 0；生产构建成功，有 3 个 bundle 体积告警。
- `npm ci` 审计报告 43 个传递/直接依赖问题（20 moderate、21 high、2 critical）；本次未执行可能破坏 Vue 3 兼容性的强制升级。
- 普通 Maven 测试未选择 `TravelAgentRealSmokeIT`，因此未调用 DashScope、高德或 Open-Meteo；部分 Spring 上下文测试连接了本机 MySQL。

本次重新生成的确定性离线报告位于：

- `target/evaluation/travel-agent-offline-evaluation.json`
- `target/evaluation/travel-agent-offline-evaluation.md`

| 指标 | 分子/分母 | 公式结果 |
|---|---:|---:|
| 虚构 POI 拦截率 | 2/2 | 100.00% |
| 引用覆盖率 | 4/15 | 26.67% |
| 路线覆盖率 | 2/2 | 100.00% |
| 规则通过率 | 106/112 | 94.64% |
| 修正成功率 | 2/3 | 66.67% |
| 平均修正次数 | 5/3 | 1.67 |
| 偏好合并正确率 | 7/7 | 100.00% |
| 用户隔离结果 | 2/2 | 100.00% |
| 缓存行为 | 3/3 | 100.00% |
| 任务状态准确率 | 40/40 | 100.00% |
| 上下文裁剪确定性 | 5/5 | 100.00% |
| 空行程引用覆盖率 | 0/0 | N/A |

公式和用例边界见 [docs/evaluation-method.md](docs/evaluation-method.md)。这些比例仅描述 40 条固定 fixture 的确定性行为。

### 真实冒烟状态

**已于 2026-10-15 经凭据/配额所有者明确授权运行，并通过 1/1 Failsafe 测试。** 使用 `qwen3.7-plus`、`text-embedding-v4`、高德 Web 服务与 Open-Meteo；本次只是两个固定场景的一次冒烟，不是成功率或 SLA。

| 场景/汇总 | 状态/版本 | 模型调用 | 实际 Token（入/出） | 修正 | DOCUMENT 引用 | 耗时 |
|---|---|---:|---:|---:|---:|---:|
| 杭州两日 + 浙江省博物馆引用 | COMPLETED / 1 | 1 | 2600 / 3634 | 0 | 1 | 79,665 ms |
| 自动修正触发 | COMPLETED / 2 | 2 | 4858 / 7289 | 1 | 1 | 139,446 ms |
| 合计 | 2 个场景 | 3 | 7458 / 10,923 | 1 | 2 | 219,111 ms |

连通性检查得到 3 个真实 POI、2 日天气、5 条允许知识引用和一条已验证高德路线；总供应商调用计数为 POI 3、路线 1、天气 1。Redis 本次不可达，缓存命中为 0，系统按设计直接回源。受控路线失败通过不可达 loopback 注入得到 `PROVIDER_TIMEOUT`，没有把它描述为真实高德故障。

两个场景虽为 `COMPLETED`，仍包含 `COST_EVIDENCE_UNVERIFIED`、`COST_INFORMATION_INCOMPLETE`、`OPENING_HOURS_UNKNOWN`、`ROUTE_NOT_VERIFIED`；因此不能表述为零告警或所有路线均已验证。脱敏报告位于 `target/evaluation/travel-agent-real-smoke.json`，不会提交到 Git。

复现会再次产生真实调用并写入任务、版本和 Trace，执行前仍须重新确认配额和数据写入：

```powershell
$env:TRAVEL_REAL_SMOKE_ENABLED='true'
.\mvnw.cmd -Preal-smoke verify
```

## 演示与截图

演示脚本见 [docs/demo-script.md](docs/demo-script.md)。当前没有提交截图；需要在实际运行后截取登录、规划进度、行程详情、知识文档 ACTIVE/切片页和真实测试报告，且必须隐藏 Token、账号、路径和用户数据。不得用设计稿或伪造成功页替代。

## 已知限制

- 固定离线 fixture 不是统计抽样，不能推出真实模型泛化率、路线准确率、引用质量或线上 SLA。
- `ConservativeTokenEstimator` 不是供应商官方 tokenizer。
- 活动详情当前没有持久化完整地址、下一段路线和证据元数据，对应 API 字段会诚实返回 `null` 或空数组。
- 本地 JSON EmbeddingStore 适合单机演示，不等同于并发生产向量数据库。
- Redis 可降级；本次真实冒烟记录为不可达且缓存命中为 0，未覆盖真实缓存命中链路。
- 前端 bundle 偏大，且依赖审计仍有 43 个待评估问题。
- Apifox 尚未在本环境实际执行；真实冒烟只有一次小样本，不能外推稳定性或成功率。

## 参考项目与许可证

以下信息来自仓库已有设计材料；本项目按自身领域模型重写，不复制第三方源码：

| 参考仓库 | 许可证 | 借鉴边界 |
|---|---|---|
| [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) | Apache-2.0 | Spring Boot 集成、RAG、结构化输出和测试思路 |
| [TripStar-Java](https://github.com/LeeFly-cn/TripStar-Java) | GPL-2.0 | 只参考规划/审核/修正流程，不复制 GPL 源码 |
| [embabel/ragbot](https://github.com/embabel/ragbot) | Apache-2.0 | Tika、切片和本地知识库思路，不引入 Embabel |
| [spring-ai-playground](https://github.com/spring-ai-community/spring-ai-playground) | Apache-2.0 | 工具白名单、失败降级、进度和 Trace 思路，不引入 Spring AI |
