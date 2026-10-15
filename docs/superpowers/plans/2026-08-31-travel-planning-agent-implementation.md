# 多约束旅行规划 Agent 实施计划

> **执行要求：** 按任务顺序逐项实施，并使用复选框（`- [ ]`）跟踪进度。每个任务都必须先写失败测试，再完成最小实现，通过验证后再提交。若由 Codex 执行，必须使用 `subagent-driven-development`（推荐）或 `executing-plans` 流程。

**目标：** 将现有 Spring Boot 与 Vue 旅行应用升级为可信的单城市旅行规划 Agent，形成“可控偏好记忆、可追溯 RAG、受控旅行工具、确定性 Java 校验、有限次数修正、版本持久化、SSE 进度和评测”闭环。

**架构：** LangChain4j 负责模型调用、结构化输出、Embedding 和检索；普通 Java 编写的 `TravelPlanningOrchestrator` 负责编排顺序；`PreferenceMemoryService` 用确定性规则合并用户明确保存的结构化偏好；适配器隔离高德与 Open-Meteo；规则校验器保持确定性且禁止调用模型。MySQL 保存偏好、任务和版本等持久业务状态，Redis 保存短期缓存和验证码，Vue 应用消费偏好、任务、SSE、行程及管理端接口。

**技术栈：** Java 21、Spring Boot 3.5、LangChain4j、JJWT 0.13.0、Spring Security Crypto、MyBatis-Plus、MySQL、Redis、Apache Tika、Vue 3、Element Plus、SSE、JUnit 5、Spring MockRestServiceServer、Apifox。

---

## 一、计划边界、执行分工与仓库结构

本设计包含六个相互依赖的增量阶段，必须按顺序执行。每个阶段只有在测试通过并能演示对应行为后，才能进入下一阶段。

当前前后端都不是 Git 仓库。任务 1 将 `D:\wyx_learn_Java\travel_agent` 作为仓库根目录，把前端复制到 `web/`。在整合后的应用完成最终构建前，保留原始 `D:\wyx_learn_Java\travel_agent_web`，不移动、不删除。

### 1.1 执行分工

- **Codex 负责并由用户验收：** 任务 1～17 的全部实现，包括工程底座、安全、真实数据源、RAG、偏好记忆、上下文控制、生成与校验、修正编排、异步任务、SSE、前端和 Agent 评测。
- **任务 18 以 Codex 执行为主：** Codex 运行能够自动化的测试、联调与构建并编写项目文档、演示材料和简历初稿；用户审核真实数据来源、评测结论、页面效果和最终简历表述。
- **用户保留的必要操作：** 在本机填写或轮换个人账号密钥、批准需要确认的数据库操作、处理验证码或服务商控制台操作，并完成每阶段验收；真实值不得发送到聊天。
- Codex 可以修改计划明确列出的前后端文件。若实现中发现 API 契约、错误码或状态枚举确需变化，必须先说明原因并更新本文档，再修改两端，不能为了让测试通过而静默改变需求。
- DashScope、高德、MySQL 等真实凭据由用户直接填写到本机被 Git 忽略的 `application-local.yml`，不得发送到聊天。Codex 负责配置结构、模拟测试和在凭据就绪后的真实联调，用户只负责最终验收及必须由个人账号完成的 Key 申请或轮换。
- 阶段三演示文档由 Codex 按设计中的可信来源边界整理并记录标题、城市、发布机构、来源 URL、发布日期和类型；用户最终审核数据来源。测试夹具不得复制受版权保护的长篇正文。

为使前端不依赖后端完成进度，任务 14 完成后固定 API 契约；在此之前，Codex 使用与契约完全一致的 JSON fixture 和模拟 SSE 事件开发页面。联调时只替换请求实现，不修改页面领域模型。

每日执行按照 [2026-09-02 至 2026-10-18 每日执行表](2026-09-02-travel-agent-daily-execution-schedule.md) 推进。默认每天投入 2 小时，六周完成主范围，随后保留五天缓冲。每日只有在对应测试或构建证据通过后才能标记完成。

### 1.2 目标目录结构

```text
travel_agent/
├─ pom.xml
├─ src/main/java/com/wyx/travelagent/
│  ├─ TravelAgentApplication.java
│  ├─ common/              # API errors, clock, JSON contracts
│  ├─ auth/                # JWT登录、验证码、用户上下文和角色授权
│  ├─ knowledge/           # document ingest, index and retrieval
│  ├─ tools/               # provider ports, AMap/Open-Meteo adapters and cache
│  ├─ planning/            # requirements, orchestrator, generation and repair
│  └─ trace/               # task events and execution traces
├─ src/main/resources/
│  ├─ application.yml
│  ├─ application-local.yml.example
│  └─ prompts/
├─ src/test/java/com/wyx/travelagent/
├─ sql/                    # fresh-install and legacy-upgrade MySQL scripts
├─ web/                    # copied Vue 3 application
└─ docs/superpowers/
```

### 1.3 前后端固定契约

后端任务 14 完成时必须提供以下字段；Codex 将据此实现前端，不读取 Java 实体，也不依赖数据库字段。

获取验证码：

```http
GET /api/auth/captcha
```

```json
{
  "success": true,
  "data": {
    "captchaId": "9c4b47bb-8f1e-4a50-8d32-a1c862014cee",
    "imageDataUrl": "data:image/png;base64,iVBORw0KGgoAAA...",
    "expiresInSeconds": 300
  },
  "error": null
}
```

登录：

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "demo-user",
  "password": "用户输入的密码",
  "captchaId": "9c4b47bb-8f1e-4a50-8d32-a1c862014cee",
  "captchaCode": "4821"
}
```

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresAt": "2026-10-01T12:00:00",
    "user": {"id": 1, "name": "演示用户", "username": "demo-user", "role": "USER"}
  },
  "error": null
}
```

受保护接口统一携带：

```http
Authorization: Bearer <accessToken>
```

`GET /api/auth/me` 返回当前 `UserProfile`。首期不提供服务端 Token 撤销接口；用户退出时由前端删除 `sessionStorage` 中的 Token 和用户信息，已签发 JWT 最迟在默认 2 小时有效期结束后自然失效。

创建任务：

```http
POST /api/planning-tasks
Content-Type: application/json
```

```json
{
  "destinationCity": "杭州",
  "startDate": "2026-10-20",
  "endDate": "2026-10-21",
  "travelers": 2,
  "totalBudget": 3000,
  "companionType": "FRIENDS",
  "interests": ["HISTORY", "FOOD"],
  "pace": "MODERATE",
  "accommodationAddress": "杭州市上城区湖滨街道",
  "travelMode": "TRANSIT",
  "specialRequirements": "每天最晚20:00结束",
  "useSavedPreferences": true
}
```

当 `useSavedPreferences=true` 时，只用已保存偏好补全本次请求中未填写的兴趣、节奏、交通方式和住宿偏好；当前请求显式字段优先。目的地、日期、人数、预算、同行类型和特殊要求不得从长期记忆补全。

用户偏好：

```http
GET    /api/user/travel-preferences
PUT    /api/user/travel-preferences
DELETE /api/user/travel-preferences
```

```json
{
  "interests": ["HISTORY", "FOOD"],
  "pace": "MODERATE",
  "travelMode": "TRANSIT",
  "accommodationPreference": "地铁站步行十分钟内"
}
```

GET 只返回当前用户的已保存值；PUT 整体覆盖并返回 `updatedAt`；DELETE 成功后 GET 返回各偏好字段为空的对象。接口不接收 `userId`，用户身份只从 JWT 上下文获取。任何接口都不保存完整对话、历史行程全文或模型推断结果。

成功响应：

```json
{
  "success": true,
  "data": {
    "taskId": 101,
    "status": "PENDING",
    "createdAt": "2026-10-01T10:00:00"
  },
  "error": null
}
```

查询任务：

```http
GET /api/planning-tasks/{taskId}
```

```json
{
  "success": true,
  "data": {
    "taskId": 101,
    "status": "RUNNING",
    "currentStep": "VALIDATING_ITINERARY",
    "progressPercent": 75,
    "planId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-01T10:00:00",
    "updatedAt": "2026-10-01T10:00:12"
  },
  "error": null
}
```

SSE 接口：

```http
GET /api/planning-tasks/{taskId}/events
Accept: text/event-stream
```

```text
id: 18
event: planning-progress
data: {"eventId":18,"taskId":101,"step":"VALIDATING_ITINERARY","status":"RUNNING","message":"发现1个时间冲突，准备修正","occurredAt":"2026-10-01T10:00:12"}
```

任务状态固定为 `PENDING`、`RUNNING`、`COMPLETED`、`NEEDS_ADJUSTMENT`、`FAILED`。前端对未知状态显示“未知任务状态”，禁止把未知状态当作完成。

行程详情：

```http
GET /api/plans/{planId}
```

`data` 至少包含 `planId`、`status`、`currentVersion`、`destinationCity`、`startDate`、`endDate`、`summary`、`weather`、`appliedPreferences`、`days`、`warnings`、`metrics` 和 `versions`。`appliedPreferences` 包含 `enabled` 以及实际从长期记忆采用的字段名和值，不返回偏好修改历史；`days[].items[]` 至少包含：

```json
{
  "startTime": "09:00",
  "endTime": "11:30",
  "poiId": "B0FFG12345",
  "poiName": "示例地点",
  "address": "高德返回地址",
  "activity": "游览",
  "cost": {"min": 0, "max": 0, "evidenceType": "API_FACT", "estimated": false},
  "nextSegment": {"mode": "TRANSIT", "distanceMeters": 5200, "durationMinutes": 35, "verified": true},
  "evidence": [{"type":"DOCUMENT","documentTitle":"文档标题","publisher":"发布机构","sourceUrl":"https://example.org/source","chunkId":88,"quote":"与本次安排直接相关的短引用"}]
}
```

统一错误响应：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "request validation failed",
    "fields": {"startDate": "出发日期不能为空"}
  }
}
```

知识库上传使用 `multipart/form-data`，字段固定为 `file`、`title`、`city`、`publisher`、`sourceUrl`、`publishedAt`、`documentType`。冻结后的管理端后端契约为：

```http
POST   /api/admin/knowledge-documents
GET    /api/admin/knowledge-documents
GET    /api/admin/knowledge-documents/{id}
GET    /api/admin/knowledge-documents/{id}/chunks?page=0&size=20
PATCH  /api/admin/knowledge-documents/{id}/status
DELETE /api/admin/knowledge-documents/{id}
POST   /api/admin/knowledge-documents/reindex
```

列表项固定包含 `id`、`title`、`city`、`publisher`、`sourceUrl`、`publishedAt`、`documentType`、`status`、`chunkCount`、`createdAt`、`updatedAt` 和脱敏 `failureReason`；单次聚合查询必须包含零切片文档，并按 `createdAt DESC, id DESC` 排序。详情在此基础上只增加 `originalFilename`、`contentType`、`fileSize` 和 `sha256`。列表和详情均不得公开 `storedFilename`、服务器路径、正文、向量或堆栈。

切片响应使用 `items/page/size/totalElements/totalPages`，默认 `0/20`、最大 `size=100`，按 `chunkIndex, id` 排序，项目只含 `id/documentId/chunkIndex/content/createdAt`。状态请求为 `{"status":"ACTIVE"}` 或 `{"status":"DISABLED"}`；只允许 `ACTIVE ↔ DISABLED`，相同目标不重复写数据库但仍按 ACTIVE 全集重建对账，`UPLOADED/INDEXING/FAILED` 均返回冲突。状态改变后使用数据库当前 ACTIVE 切片全集重建索引；失败时条件恢复原状态并再次按恢复后的 ACTIVE 全集重建，任何补偿失败必须显式返回错误，禁止假成功。

删除仅允许 `DISABLED` 和 `FAILED`，`ACTIVE` 必须先停用，`INDEXING` 也拒绝。删除前先按数据库 ACTIVE 全集重建索引，以清除极端补偿失败遗留的非 ACTIVE 索引项；随后只可用受控 `uploadRoot` 与数据库服务端文件名解析单个文件，先原子移入 `.deleting`、再短事务条件删除数据库行。数据库失败时原子移回，提交后的最终清理失败只留下不含路径的安全运维日志。重建接口只读取数据库 ACTIVE 文档与切片，空集合也发布空索引，并只返回 `activeDocumentCount/chunkCount/rebuiltAt`。所有端点继续由后端 `/api/admin/**` ADMIN 校验保护。

包迁移完成后，不得同时保留 `com.test` 和 `com.wyx.travelagent` 两套生产代码。首期不得引入 LangGraph4j、独立向量数据库、Docker 或其他后端服务。

## 阶段一：仓库、配置、数据库与安全

### 任务 1：整合项目并建立版本控制

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/web/**` from `travel_agent_web`, excluding `node_modules` and `dist`
- 新建： `travel_agent/.gitignore`
- 新建： `travel_agent/docs/superpowers/specs/2026-08-31-travel-planning-agent-design.md`
- 新建： `travel_agent/docs/superpowers/plans/2026-08-31-travel-planning-agent-implementation.md`

- [ ] **步骤 1：确认源路径与目标路径**

在 `D:\wyx_learn_Java` 中运行：

```powershell
Resolve-Path -LiteralPath '.\travel_agent'
Resolve-Path -LiteralPath '.\travel_agent_web'
Test-Path -LiteralPath '.\travel_agent\web'
```

预期：两个源路径都能解析，最后一条命令返回 `False`。

- [ ] **步骤 2：复制前端并排除生成目录**

使用 PowerShell `Copy-Item` 复制源文件。如果新复制的目录中存在 `web\dist`，只删除这个新副本；删除前验证其绝对路径以 `D:\wyx_learn_Java\travel_agent\web` 开头，禁止操作原始前端目录。

- [ ] **步骤 3：添加仓库忽略规则**

创建以下 `.gitignore`：

```gitignore
target/
web/node_modules/
web/dist/
.idea/
*.iml
.env
application-local.yml
data/index/
data/uploads/
logs/
```

- [ ] **步骤 4：把已批准规格和本计划复制到仓库**

把 `D:\wyx_learn_Java\docs\superpowers` 中已批准的规格和计划复制到 `D:\wyx_learn_Java\travel_agent\docs\superpowers`，保留文件名。

- [ ] **步骤 5：初始化 Git 并创建基线提交**

```powershell
git init
git add .
git commit -m "chore: establish travel agent monorepo"
```

预期：产生一个包含后端、前端、规格和计划的根提交；任何包含密钥的本地配置和生成产物都未被跟踪。

### 任务 2：固定依赖版本并建立本地/部署双模式敏感配置

**负责人：** Codex 执行，用户验收；涉及个人账号的密钥轮换和真实凭据填写由用户在本机完成，真实值不得发送到聊天或写入命令输出。

**涉及文件：**
- 修改： `travel_agent/pom.xml`
- 修改： `travel_agent/.gitignore`
- 修改： `travel_agent/src/main/resources/application.yml`
- 新建： `travel_agent/src/main/resources/application-local.yml.example`
- 本地生成但不提交： `travel_agent/src/main/resources/application-local.yml`
- 新建： `travel_agent/src/test/java/com/wyx/travelagent/ConfigurationSafetyTest.java`

- [ ] **步骤 1：编写当前必然失败的配置安全测试**

```java
class ConfigurationSafetyTest {
    @Test
    void committedConfigurationSupportsLocalFallbackWithoutLiteralSecrets() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String gitignore = Files.readString(Path.of(".gitignore"));

        assertThat(yaml).contains(
            "${DASHSCOPE_API_KEY:${travel.local-secrets.dashscope-api-key:}}",
            "${AMAP_WEB_API_KEY:${travel.local-secrets.amap-web-api-key:}}",
            "${TRAVEL_JWT_SECRET:${travel.local-secrets.jwt-secret:}}"
        );
        assertThat(yaml).doesNotContain("api-key: sk-");
        assertThat(gitignore).contains("application-local.yml");
    }
}
```

- [ ] **步骤 2：运行测试并确认当前 YAML 导致失败**

```powershell
.\mvnw.cmd -Dtest=ConfigurationSafetyTest test
```

预期：测试失败，因为当前配置仍含直接配置值、缺少本地回退表达式，或 `.gitignore` 尚未正确忽略本地配置。

- [ ] **步骤 3：添加受统一版本管理的 Spring 依赖**

添加由 Spring Boot 管理的 validation、data-redis、cache 和 actuator starter；增加 `tika-core`、`tika-parsers-standard-package`、测试范围 H2，以及只提供 BCrypt 的 `spring-security-crypto`。不引入 Flyway、`spring-boot-starter-security` 和 `spring-security-test`。JWT 使用官方模块化依赖：编译期 `jjwt-api:0.13.0`，运行期 `jjwt-impl:0.13.0` 与 `jjwt-jackson:0.13.0`。保留 Java 21 与 Spring Boot 3.5.9。两个 LangChain4j starter 统一使用 `langchain4j.version=1.15.0`，Tika 使用 `tika.version=3.2.3`；不得混用旧的 `1.1.0-beta7` API。

- [ ] **步骤 4：建立“本地配置文件 + 部署环境变量”的双模式配置**

提交到仓库的 `application.yml` 不保存真实凭据。部署环境变量优先；未设置环境变量时，本地 `local` Profile 从被忽略的 `application-local.yml` 读取。模型名称也允许在本地文件中调整，切换 Key 或模型只需修改该文件并重启后端。

`application.yml` 使用以下结构：

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    username: ${TRAVEL_DB_USERNAME:${travel.local-secrets.db-username:root}}
    password: ${TRAVEL_DB_PASSWORD:${travel.local-secrets.db-password:}}
  data:
    redis:
      host: ${TRAVEL_REDIS_HOST:127.0.0.1}
      port: ${TRAVEL_REDIS_PORT:6379}

langchain4j:
  open-ai:
    chat-model:
      base-url: ${TRAVEL_AI_BASE_URL:${travel.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}}
      api-key: ${DASHSCOPE_API_KEY:${travel.local-secrets.dashscope-api-key:}}
      model-name: ${TRAVEL_CHAT_MODEL:${travel.ai.chat-model:qwen-plus}}
      max-tokens: ${TRAVEL_MODEL_MAX_OUTPUT_TOKENS:4000}
    embedding-model:
      base-url: ${TRAVEL_AI_BASE_URL:${travel.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}}
      api-key: ${DASHSCOPE_API_KEY:${travel.local-secrets.dashscope-api-key:}}
      model-name: ${TRAVEL_EMBEDDING_MODEL:${travel.ai.embedding-model:text-embedding-v3}}
    streaming-chat-model:
      base-url: ${TRAVEL_AI_BASE_URL:${travel.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}}
      api-key: ${DASHSCOPE_API_KEY:${travel.local-secrets.dashscope-api-key:}}
      model-name: ${TRAVEL_CHAT_MODEL:${travel.ai.chat-model:qwen-plus}}
      max-tokens: ${TRAVEL_MODEL_MAX_OUTPUT_TOKENS:4000}

amap:
  base-url: https://restapi.amap.com
  api-key: ${AMAP_WEB_API_KEY:${travel.local-secrets.amap-web-api-key:}}

travel:
  jwt:
    secret: ${TRAVEL_JWT_SECRET:${travel.local-secrets.jwt-secret:}}
    issuer: travel-planning-agent
    access-token-ttl: 2h
```

`application-local.yml.example` 只提交字段和安全占位值：

```yaml
travel:
  ai:
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    chat-model: qwen-plus
    embedding-model: text-embedding-v3
  local-secrets:
    dashscope-api-key: replace-with-your-local-key
    amap-web-api-key: replace-with-your-local-key
    jwt-secret: replace-with-at-least-32-random-bytes
    db-username: root
    db-password: replace-with-your-local-password
```

Codex 执行时根据 example 生成 `application-local.yml` 的结构，但不编造真实凭据。用户只在本机填写真实值；该文件不得加入 Git。模型名称和 Base URL 不是秘密，可以按本地需要修改。

- [ ] **步骤 5：验证本地配置确实未被 Git 跟踪**

```powershell
git check-ignore -v src/main/resources/application-local.yml
git ls-files -- src/main/resources/application-local.yml
```

预期：第一条命令显示命中的忽略规则，第二条命令没有输出。禁止使用 `git add -f` 强制提交该文件。

- [ ] **步骤 6：轮换曾写入旧配置的 DashScope Key**

如果旧 Key 曾进入 Git 历史或远程仓库，用户必须在 DashScope 控制台撤销并新建 Key，再把新值直接填入本机 `application-local.yml`；Codex 不读取、不回显完整密钥。若用户确认旧 Key 从未离开本机且从未进入 Git 历史，则记录该确认，但仍不得把旧值纳入首次提交。

- [ ] **步骤 7：运行安全测试与依赖构建**

```powershell
.\mvnw.cmd -Dtest=ConfigurationSafetyTest test
.\mvnw.cmd -DskipTests package
```

预期：两条命令退出码均为 0。

- [ ] **步骤 8：提交变更并复核提交内容**

```powershell
git add pom.xml .gitignore src/main/resources/application.yml src/main/resources/application-local.yml.example src/test/java
git diff --cached --name-only
git commit -m "chore: secure configuration and add platform dependencies"
```

预期：暂存文件列表不包含 `application-local.yml`，提交中不存在真实 API Key、JWT 密钥或数据库密码。

### 任务 3：迁移 Java 包名并建立统一 API 错误契约

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/TravelAgentApplication.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/common/api/ApiResponse.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/common/api/ApiError.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/common/api/GlobalExceptionHandler.java`
- 迁移并修改： all retained `travel_agent/src/main/java/com/test/**`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/common/api/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：在实现处理器前编写参数校验失败测试**

使用 `MockMvc` 和测试接口，断言返回 HTTP 400 以及以下结构：

```json
{
  "success": false,
  "data": null,
  "error": {"code": "VALIDATION_ERROR", "message": "request validation failed"}
}
```

- [ ] **步骤 2：运行单项测试**

```powershell
.\mvnw.cmd -Dtest=GlobalExceptionHandlerTest test
```

预期：测试失败，因为统一响应类型尚未创建。

- [ ] **步骤 3：实现不可变统一响应记录类型**

```java
public record ApiError(String code, String message, Map<String, String> fields) {}

public record ApiResponse<T>(boolean success, T data, ApiError error) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
```

- [ ] **步骤 4：把保留代码迁移到 `com.wyx.travelagent`**

同步修改包声明、Mapper XML 类型、组件扫描和测试。只有当 `rg "com\.test" src` 不再返回生产代码引用后，才能删除旧 `com/test` 源码目录。

- [ ] **步骤 5：运行全部后端测试**

```powershell
.\mvnw.cmd test
```

预期：输出 `BUILD SUCCESS`。

- [ ] **步骤 6：提交变更**

```powershell
git add src pom.xml
git commit -m "refactor: establish application package and API errors"
```

### 任务 4：初始化业务表并实现 JWT 登录鉴权

**负责人：** Codex 执行，用户验收。执行数据库脚本前，Codex 必须先只读检查现有结构并完成可恢复备份；如果无法明确判断是全新数据库、旧库还是部分升级状态，则停止操作并请用户确认。

**涉及文件：**
- 新建： `travel_agent/sql/schema.sql`
- 新建： `travel_agent/sql/upgrade-from-legacy.sql`
- 新建： `travel_agent/docs/database-setup.md`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/AuthController.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/JwtProperties.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/JwtTokenService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/JwtAuthenticationInterceptor.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/WebMvcAuthenticationConfiguration.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/CurrentUser.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/CurrentUserContext.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/Role.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/CaptchaService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/auth/UserProfile.java`
- 修改： `travel_agent/src/main/java/com/wyx/travelagent/entity/User.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/auth/AuthControllerTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/auth/JwtTokenServiceTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/auth/JwtAuthenticationInterceptorTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/auth/CaptchaServiceTest.java`

- [ ] **步骤 1：编写当前必然失败的权限测试**

断言缺少 Bearer Token、签名错误、过期和签发者错误的 Token 返回 401；`USER` 角色访问 `/api/admin/knowledge-documents` 返回 403；合法 `ADMIN` Token 放行；`/api/auth/me` 永远不包含密码字段。

- [ ] **步骤 2：编写当前必然失败的验证码隔离测试**

```java
@Test
void captchasAreIsolatedByIdAndConsumedOnce() {
    service.save("captcha-a", "1234");
    assertThat(service.verifyAndDelete("captcha-b", "1234")).isFalse();
    assertThat(service.verifyAndDelete("captcha-a", "1234")).isTrue();
    assertThat(service.verifyAndDelete("captcha-a", "1234")).isFalse();
}
```

- [ ] **步骤 3：运行单项测试并确认失败**

```powershell
.\mvnw.cmd -Dtest=AuthControllerTest,JwtTokenServiceTest,JwtAuthenticationInterceptorTest,CaptchaServiceTest test
```

- [ ] **步骤 4：编写可审查的 MySQL 初始化与升级脚本**

`sql/schema.sql` 用于全新数据库，创建现有用户表、旧推荐表以及 `user_travel_preference`、`knowledge_document`、`knowledge_chunk`、`planning_task`、`travel_plan`、`travel_plan_version`、`planning_trace`。`sql/upgrade-from-legacy.sql` 只用于现有本地数据库：为用户表增加 `role VARCHAR(16) NOT NULL DEFAULT 'USER'`，并创建新的 Agent 业务表。两份脚本均不得包含 `DROP DATABASE`、`DROP TABLE`、`TRUNCATE` 或真实账号密码。

`user_travel_preference` 使用 `user_id` 作为主键和外键，每名用户最多一行；只保存兴趣枚举集合、节奏、交通方式、住宿偏好和更新时间，不保存目的地、日期、人数、预算、同行类型、自由文本特殊要求或聊天原文。为知识文档、切片、规划任务、行程、行程版本和 Trace 建立必要的外键，并为用户、城市、状态、方案、任务和创建时间建立索引。JSON 列只保存请求、偏好枚举集合、行程、校验结果和指标载荷，能够结构化查询的状态、城市、用户 ID 和时间必须使用独立列。

`docs/database-setup.md` 明确区分两条路径：全新数据库只执行 `schema.sql`；已有旧表的本地数据库只执行 `upgrade-from-legacy.sql`，禁止两份脚本连续执行。升级前先使用 MySQL Workbench 或 `mysqldump` 做完整备份，再核对用户表实际名称、主键和现有列；如果数据库处于部分升级状态则停止，不猜测执行。项目不接入 Flyway，也不在应用启动时自动修改数据库结构。

- [ ] **步骤 5：实现 JWT 签发、解析与角色鉴权**

使用 `BCryptPasswordEncoder` 校验密码。`JwtTokenService` 使用 JJWT 0.13.0 和 Spring 最终解析出的 `travel.jwt.secret` 构造 HMAC Key：部署时优先读取 `TRAVEL_JWT_SECRET`，本地开发读取被 Git 忽略的 `application-local.yml`；密钥解码后的长度不得低于 HS256 要求。签发包含 `sub`、`role`、`iss=travel-planning-agent`、`iat` 和 `exp` 的 Token，默认有效期为 2 小时。`JwtAuthenticationInterceptor` 统一解析 `Authorization: Bearer`，只放行验证码与登录；`/api/admin/**` 要求 `ADMIN`，其他 `/api/**` 要求已登录。登录结果返回 `UserProfile(id, name, username, role)`，不得返回实体对象或密码哈希。

- [ ] **步骤 6：实现用户上下文清理和验证码隔离**

拦截器把 `CurrentUser(userId, username, role)` 放入 `CurrentUserContext`，并在 `afterCompletion` 中无条件执行 `clear()`。异步规划任务必须显式传递并持久化 `userId`，禁止在线程池中读取请求线程的 ThreadLocal。验证码使用 `travel:captcha:{captchaId}`，TTL 为 5 分钟并在验证后删除。首期不维护 JWT 黑名单；前端退出仅清除本地登录状态，后端依靠 `exp` 控制 Token 到期。

- [ ] **步骤 7：运行测试并验证本地数据库结构**

Codex 先根据 `docs/database-setup.md` 对目标数据库做只读检查：空数据库选择“全新安装”，存在完整旧用户表且尚无 Agent 业务表时选择“旧库升级”，部分升级或结构不一致时停止并请用户确认。确认路径后先完成可恢复备份，再只执行对应的一份 SQL。执行后查询 `information_schema.tables`、`information_schema.columns` 和 `information_schema.statistics`，确认七张 Agent 业务表、用户角色列、外键和关键索引均存在，再运行：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

预期：数据库结构核对通过、测试通过、应用能够连接已有数据库并正常启动；验证后停止进程。测试或启动失败时不得通过删除、清空或重建现有数据库绕过问题。

- [ ] **步骤 8：提交变更**

```powershell
git add src pom.xml sql docs/database-setup.md
git commit -m "feat: add schema and jwt authentication"
```

## 阶段二：真实数据源、受控工具与 Redis 缓存

### 任务 5：定义数据源端口与统一工具结果

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/ToolResult.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/CoordinateSystem.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/GeoPoint.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/PlaceCandidate.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/TravelSegment.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/TravelMode.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/model/WeatherResult.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/port/PlaceProvider.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/port/RouteProvider.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/port/WeatherProvider.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/model/ToolResultTest.java`

- [ ] **步骤 1：编写序列化与失败契约测试**

每个成功结果必须包含 `provider`、`retrievedAt` 和数据；每个失败结果必须包含稳定错误码且不得携带数据。增加坐标模型测试：经纬度越界、非有限数值或坐标系为空时构造失败；合法地点必须显式携带 `WGS84` 或 `GCJ02`，禁止使用无法判断坐标系的两个裸 `double`。

- [ ] **步骤 2：实现记录类型和数据源端口**

```java
public interface PlaceProvider {
    ToolResult<List<PlaceCandidate>> search(String city, String keyword, int limit);
    ToolResult<PlaceCandidate> geocode(String city, String address);
}

public interface RouteProvider {
    ToolResult<TravelSegment> route(PlaceCandidate from, PlaceCandidate to, TravelMode mode);
}

public interface WeatherProvider {
    ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate);
}
```

坐标类型固定为：

```java
public enum CoordinateSystem {
    WGS84,
    GCJ02
}

public record GeoPoint(
    double longitude,
    double latitude,
    CoordinateSystem coordinateSystem
) {
    public GeoPoint {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range");
        }
        Objects.requireNonNull(coordinateSystem, "coordinateSystem");
    }
}
```

`PlaceCandidate` 使用 `GeoPoint location` 保存位置。任务五只定义坐标语义，不实现坐标转换。

- [ ] **步骤 3：运行单项测试并提交**

```powershell
.\mvnw.cmd -Dtest=ToolResultTest test
git add src
git commit -m "feat: define travel provider contracts"
```

### 任务 6：实现高德与 Open-Meteo 适配器及契约测试

**负责人：** Codex 执行，用户验收。真实高德联调前，用户只需在本机 `application-local.yml` 填写 Web 服务 Key，不得把 Key 发送到聊天或提交到 Git。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/amap/AmapProperties.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/amap/AmapPlaceProvider.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/amap/AmapRouteProvider.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/geo/Gcj02ToWgs84Converter.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/weather/OpenMeteoWeatherProvider.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/config/ProviderConfiguration.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/amap/AmapPlaceProviderTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/amap/AmapRouteProviderTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/geo/Gcj02ToWgs84ConverterTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/weather/OpenMeteoWeatherProviderTest.java`

- [ ] **步骤 1：编写 MockRestServiceServer 测试**

覆盖成功、服务方错误状态、POI 空列表、坐标格式错误、超时映射，以及日期超出 Open-Meteo 返回范围。超出范围时必须返回 `WEATHER_DATE_UNAVAILABLE`，禁止使用历史天气冒充预报。

坐标契约必须额外覆盖：高德 POI 坐标标记为 `GCJ02`；高德路线只接收 GCJ-02；中国大陆范围内的 GCJ-02 在调用 Open-Meteo 前转换为 WGS84；已经是 WGS84 的输入保持不变；大陆范围外不应用 GCJ-02 偏移；非法或缺失坐标系返回 `COORDINATE_SYSTEM_UNSUPPORTED` 或 `INVALID_COORDINATE`，且不发送 HTTP 请求。使用固定坐标 fixture 和明确误差范围测试转换，不调用在线坐标转换服务。

这里测试的是后端内部 `RestClient` 对高德和 Open-Meteo 的请求构造、响应解析及错误映射，不启动旅行 Agent 服务，也不调用真实外部 API。Apifox 负责已启动应用的业务流程和 OpenAPI 契约测试，不能替代本步骤。

- [ ] **步骤 2：运行测试并确认适配器尚未实现**

```powershell
.\mvnw.cmd -Dtest=AmapPlaceProviderTest,AmapRouteProviderTest,Gcj02ToWgs84ConverterTest,OpenMeteoWeatherProviderTest test
```

- [ ] **步骤 3：使用 `RestClient` 实现适配器**

显式设置连接和读取超时，在适配器边界完成第三方响应转换。规划代码不得依赖高德 DTO，日志不得输出含 API Key 的请求 URL。

`Gcj02ToWgs84Converter` 使用本地、确定性的纯 Java 算法，不新增外部 API。`AmapPlaceProvider` 保留高德原始 GCJ-02 坐标，`AmapRouteProvider` 使用该坐标请求路线；`OpenMeteoWeatherProvider` 只在自己的边界把 GCJ-02 转为 WGS84，并把转换后的经纬度发送给 Open-Meteo。原始 `PlaceCandidate` 不得被覆盖，防止之后调用高德路线时误用 WGS84。

- [ ] **步骤 4：运行单项测试和全部测试**

```powershell
.\mvnw.cmd -Dtest=AmapPlaceProviderTest,AmapRouteProviderTest,Gcj02ToWgs84ConverterTest,OpenMeteoWeatherProviderTest test
.\mvnw.cmd test
```

- [ ] **步骤 5：提交变更**

```powershell
git add src
git commit -m "feat: integrate map route and weather providers"
```

### 任务 7：增加 Redis 缓存与有限制的工具调度器

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/cache/TravelCacheConfiguration.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/TravelToolType.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/ToolAction.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/ToolPlan.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/ToolPlanValidator.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/ToolExecutionPolicy.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/ValidatedToolPlan.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/ToolExecutionBundle.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/tools/dispatch/TravelToolDispatcher.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/dispatch/ToolPlanValidatorTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/tools/cache/TravelCacheTest.java`

- [ ] **步骤 1：编写表驱动的校验器测试**

拒绝目的地不一致、POI 关键词超过 5 组、每组结果超过 10 条、不支持的工具名、非法交通方式、任意 URL，以及总调用次数超过配置上限的计划。

- [ ] **步骤 2：编写缓存行为测试**

断言路线缓存的标准化哈希 Key 包含交通方式；失败结果不得缓存；Redis 异常时直接调用真实数据源，而不是让规划任务失败。

- [ ] **步骤 3：实现工具计划校验器和调度器**

```java
public ToolExecutionBundle execute(ToolExecutionPolicy policy, ToolPlan plan) {
    ValidatedToolPlan valid = validator.validate(policy, plan);
    return executor.execute(valid);
}
```

在执行结果中保留各数据源调用次数，供 Trace 与评测统计。

- [ ] **步骤 4：配置缓存名称和 TTL**

使用 JSON 序列化，TTL 分别为 `poi=24h`、`geocode=7d`、`route=1h`、`weather=30m`；只缓存成功的 `ToolResult`。

- [ ] **步骤 5：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=ToolPlanValidatorTest,TravelCacheTest test
.\mvnw.cmd test
git add src
git commit -m "feat: add controlled travel tools and redis caches"
```

## 阶段三：文档管理与可追溯 RAG

### 任务 8：实现文档元数据、上传校验与 Tika 解析

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/model/KnowledgeDocument.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/model/KnowledgeChunk.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/ingest/DocumentParser.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/ingest/TikaDocumentParser.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/ingest/TextChunker.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/admin/KnowledgeAdminController.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/knowledge/ingest/TikaDocumentParserTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/knowledge/ingest/TextChunkerTest.java`

- [ ] **步骤 1：添加小型 PDF、DOCX、Markdown 和 TXT 测试文件**

测试文件包含标题、重复空白和一条已知来源语句，不使用受版权保护的长篇正文。

- [ ] **步骤 2：编写解析器与切片边界测试**

要求正文完成标准化、标题得到保留、目标切片长度为 500～800 字、重叠约 100 字、切片顺序稳定，并能通过文件哈希检测重复文档。

- [ ] **步骤 3：实现解析与上传校验**

Allow only the four approved types, enforce the configured size limit, require title/city/publisher/source URL/published date/type, and store files beneath a configured upload root using generated filenames.

- [ ] **步骤 4：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=TikaDocumentParserTest,TextChunkerTest test
git add src
git commit -m "feat: parse and chunk sourced knowledge documents"
```

### 任务 9：构建磁盘持久化 Embedding 索引与安全入库生命周期

**负责人：** Codex 执行，用户验收。真实向量联调前，用户只需在本机 `application-local.yml` 填写 DashScope API Key，不得把 Key 发送到聊天或提交到 Git。

**涉及文件：**
- 修改： `travel_agent/src/main/resources/application.yml`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeIndex.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexProperties.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndex.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeQuery.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeMatch.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/ingest/KnowledgeIngestionService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/repository/KnowledgeDocumentMapper.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/repository/KnowledgeChunkMapper.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/knowledge/ingest/KnowledgeIngestionServiceTest.java`

- [ ] **步骤 1：使用假 Embedding 模型编写生命周期测试**

验证 `UPLOADED → INDEXING → ACTIVE`、失败转为 `FAILED`、重复哈希拒绝、停用或删除后重建，以及启动时从磁盘加载。持久化测试必须使用临时测试目录，并明确断言先写 `knowledge-index.json.tmp`、成功后原子替换 `knowledge-index.json`；失败时保留上一份完整正式索引，未完整写入的切片不得参与检索。

- [ ] **步骤 2：实现索引端口**

```java
public interface KnowledgeIndex {
    void addAll(List<KnowledgeChunk> chunks);
    List<KnowledgeMatch> search(KnowledgeQuery query, int maxResults);
    void rebuild(List<KnowledgeChunk> activeChunks);
}
```

索引路径通过配置绑定，但默认文件名固定：

```yaml
travel:
  knowledge:
    index-file: ${TRAVEL_KNOWLEDGE_INDEX_FILE:data/index/knowledge-index.json}
```

`KnowledgeIndexProperties` 将该值解析为 `Path`。正式索引文件为 `travel_agent/data/index/knowledge-index.json`，同目录临时文件为 `travel_agent/data/index/knowledge-index.json.tmp`。启动时只加载正式文件；孤立的 `.tmp` 文件不得覆盖已存在的正式索引。`data/index/` 已由任务一加入 `.gitignore`。

- [ ] **步骤 3：实现入库事务边界**

先保存元数据并标记为 `INDEXING`，再解析并通过 DashScope `text-embedding-v3` 生成向量，把完整新索引写入 `knowledge-index.json.tmp`，成功后使用原子移动替换 `knowledge-index.json`，最后标记为 `ACTIVE`。如果文件系统不支持原子移动，则本次更新失败并保留旧正式索引，不使用复制后删除等可能暴露半成品的方式。失败时记录脱敏原因，任何未完整入库的切片都不得参与检索。

首期只使用任务二配置的 DashScope `text-embedding-v3`，不读取本地 Hugging Face 模型，不增加 ONNX Runtime、DJL 或 Python 服务。这里的“本地”只指索引文件保存在本机磁盘。更换 `TRAVEL_EMBEDDING_MODEL` 后必须先停止写入，再用全部启用切片重建 `knowledge-index.json`；不同模型生成的向量禁止写入同一索引。

- [ ] **步骤 4：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=KnowledgeIngestionServiceTest test
.\mvnw.cmd test
git add src
git commit -m "feat: persist local knowledge embeddings"
```

### 任务 10：实现按城市过滤的检索与引用校验

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/retrieval/KnowledgeRetrievalService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/retrieval/KnowledgeReference.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/knowledge/retrieval/CitationValidator.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/knowledge/retrieval/KnowledgeRetrievalServiceTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/knowledge/retrieval/CitationValidatorTest.java`

- [ ] **步骤 1：编写检索隔离测试**

准备杭州和上海切片。杭州查询最多返回 8 个启用状态的杭州候选切片，最终引用最多 5 条；停用文档和上海文档不得出现。

- [ ] **步骤 2：编写伪造引用测试**

拒绝不属于本次任务检索结果的 `chunkId`；删除无效引用并记录 `INVALID_CITATION`，没有证据的知识结论不得标记为已引用。

- [ ] **步骤 3：实现检索和引用校验**

相似度阈值必须可配置，不预设所谓“通用最优值”；阶段六使用评测集校准，并把最终取值与评测结果一并记录。

- [ ] **步骤 4：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=KnowledgeRetrievalServiceTest,CitationValidatorTest test
git add src
git commit -m "feat: add city-scoped rag and verified citations"
```

## 阶段四：行程生成、确定性校验、修正与持久化

### 任务 11：定义规划领域模型、偏好记忆与需求校验

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/TravelRequirement.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/TravelRequirementInput.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/ResolvedTravelRequirement.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/TravelPlanningContext.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/memory/UserTravelPreference.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/memory/AppliedPreferenceSnapshot.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/memory/PreferenceMemoryService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/memory/UserTravelPreferenceRepository.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/ItineraryPlan.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/ItineraryDay.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/model/ItineraryItem.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/validation/RequirementValidator.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/validation/RequirementValidatorTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/memory/PreferenceMemoryServiceTest.java`

- [ ] **步骤 1：编写参数化需求校验测试**

覆盖城市为空、人数为零、预算非正数、结束日期早于开始日期、超过 5 天、不支持的节奏或交通方式，以及合法的一日游请求。`TravelRequirementInput` 保留尚未合并的可空偏好字段和 `useSavedPreferences`，不能仅凭合并后的非空值判断来源；`ResolvedTravelRequirement` 同时返回不可变的有效需求和应用快照。

- [ ] **步骤 2：实现不可变领域模型和校验器**

日期、时间和金额分别使用 `LocalDate`、`LocalTime`、`BigDecimal`；节奏、交通方式和兴趣使用枚举，不使用自由字符串。`ItineraryItem.poiId` 必填。偏好合并完成后才对有效 `TravelRequirement` 执行完整校验；`TravelPlanningContext` 保存有效需求和 `AppliedPreferenceSnapshot`，但不保存完整聊天记录。

- [ ] **步骤 3：先编写偏好合并与用户隔离测试**

覆盖：未保存偏好时原样返回；开启记忆时只补全缺失的兴趣、节奏、交通和住宿偏好；本次显式字段覆盖历史值；`useSavedPreferences=false` 时完全不读取；目的地、日期、人数、预算、同行类型和特殊要求永远不从记忆补全；清空后不再命中；Repository 查询和更新必须使用当前 JWT 用户 ID，不能接收客户端传入的其他用户 ID。

- [ ] **步骤 4：实现确定性的结构化偏好记忆**

`PreferenceMemoryService` 只负责读取、校验、整体覆盖、清空和合并结构化字段，不调用聊天模型，不分析历史方案，也不自动学习。合并结果必须返回 `AppliedPreferenceSnapshot`，记录采用字段及其来源；该快照随 `planning_task.input_json` 持久化，长期偏好后续变化不能改写旧任务。数据库读取失败时返回“未应用记忆”的结果，由编排器继续使用本次请求并记录 Trace。

- [ ] **步骤 5：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=RequirementValidatorTest,PreferenceMemoryServiceTest test
git add src
git commit -m "feat: add validated planning contracts and preference memory"
```

### 任务 12：实现结构化行程生成与确定性校验

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 修改： `travel_agent/src/main/resources/application.yml`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/context/ContextBudgetProperties.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/context/ContextBudgetReport.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/context/PromptContext.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/context/PromptContextAssembler.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/context/ConservativeTokenEstimator.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/generation/ItineraryGenerator.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/generation/LangChain4jItineraryGenerator.java`
- 新建： `travel_agent/src/main/resources/prompts/itinerary-generate.txt`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/validation/ValidationIssue.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/validation/ValidationReport.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/validation/ItineraryRule.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/validation/ItineraryValidator.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/validation/ItineraryValidatorTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/context/PromptContextAssemblerTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/generation/LangChain4jItineraryGeneratorTest.java`

- [ ] **步骤 1：先于生成器编写行程校验测试**

构造固定测试方案，分别包含未知 POI、天数错误、活动重叠、通行时间不足、每日强度过高、费用上限超预算、营业时间未知和户外天气风险；严格断言每个问题属于 `ERROR` 还是 `WARNING`。

- [ ] **步骤 2：使用纯 Java 实现规则校验**

```java
public ValidationReport validate(TravelPlanningContext context, ItineraryPlan plan) {
    return rules.stream()
        .flatMap(rule -> rule.validate(context, plan).stream())
        .collect(ValidationReport.collector());
}
```

任何校验规则都不得调用聊天模型。

- [ ] **步骤 3：先编写上下文预算和裁剪测试**

构造五日行程、50 个候选 POI、完整路线与天气数据、5 个最大长度知识切片、结构化偏好快照和超长可选描述。断言组装结果不超过 `max-estimated-input-tokens=16000`，最终 POI 不超过 20 个、知识片段不超过 5 个，偏好分区不超过 500 Token；系统 Schema、用户显式硬约束和必要事实始终保留，记忆派生的可选偏好、低相关知识和未入选 POI 按稳定顺序裁剪，偏好修改历史、第三方原始 JSON、完整历史对话和 Trace 不得出现。必需内容单独超过预算时返回 `CONTEXT_BUDGET_EXCEEDED`，且 Stub ChatModel 调用次数为 0。

- [ ] **步骤 4：实现可配置的 Java 上下文组装器**

在 `application.yml` 中增加：

```yaml
travel:
  ai:
    context:
      model-context-window-tokens: ${TRAVEL_MODEL_CONTEXT_WINDOW_TOKENS:32768}
      max-estimated-input-tokens: ${TRAVEL_MAX_INPUT_TOKENS:16000}
      reserved-output-tokens: ${TRAVEL_MODEL_MAX_OUTPUT_TOKENS:4000}
      safety-margin-tokens: ${TRAVEL_CONTEXT_SAFETY_MARGIN_TOKENS:2000}
      max-knowledge-tokens: ${TRAVEL_MAX_KNOWLEDGE_TOKENS:4000}
      max-tool-fact-tokens: ${TRAVEL_MAX_TOOL_FACT_TOKENS:6000}
      max-memory-tokens: ${TRAVEL_MAX_MEMORY_TOKENS:500}
      max-poi-count: ${TRAVEL_MAX_PROMPT_POI_COUNT:20}
      max-knowledge-chunks: 5
```

启动时校验三个总预算字段不超过窗口。`32768` 只是项目保守默认值；真实联调或切换模型前必须按服务商当前文档调整，不得把该值描述为模型官方上限。`PromptContextAssembler` 只能接收内部领域对象，按“系统约束与 Schema → 本次显式硬约束 → 修正问题 → POI/路线/天气事实 → 实际采用的结构化偏好 → 知识切片 → 可选描述”的顺序组装。它只接收 `AppliedPreferenceSnapshot`，不读取偏好表或偏好历史；本次请求显式字段不可被记忆覆盖。`ConservativeTokenEstimator` 只提供确定性的安全估算，类名、报告和文档不得宣称其等同 Qwen 官方 Tokenizer。`ContextBudgetReport` 保存估算输入量、各分区用量、保留/裁剪数量和裁剪原因，其中单独记录偏好分区用量和被裁剪的记忆字段。

- [ ] **步骤 5：添加结构化行程生成 Prompt**

Prompt 只能接收 `PromptContextAssembler` 产出的预算内 `PromptContext`，禁止直接传递 `TravelPlanningContext` 或第三方响应；模型必须返回 POI ID，禁止只写自造名称；每笔费用必须标记为 `FACT`、`DOCUMENT` 或 `AI_ESTIMATE`。

- [ ] **步骤 6：使用 Stub ChatModel 测试生成结果解析**

合法 JSON 必须映射为 `ItineraryPlan`；只有无害的未知字段可以忽略；缺少必填 ID 或 JSON 格式错误时返回稳定的生成错误码。

- [ ] **步骤 7：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=ItineraryValidatorTest,PromptContextAssemblerTest,LangChain4jItineraryGeneratorTest test
.\mvnw.cmd test
git add src
git commit -m "feat: generate and validate structured itineraries"
```

### 任务 13：实现有限次数修正、编排、版本与 Trace

**负责人：** Codex 执行，用户验收。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/repair/ItineraryRepairService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/repair/RepairRequest.java`
- 新建： `travel_agent/src/main/resources/prompts/itinerary-repair.txt`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/TravelPlanningOrchestrator.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/persistence/TravelPlanRepository.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/trace/PlanningTraceService.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/TravelPlanningOrchestratorTest.java`

- [ ] **步骤 1：编写编排器场景测试**

覆盖以下场景：初稿直接通过；初稿失败但第一次修正通过；两次修正仍失败并返回 `NEEDS_ADJUSTMENT`；POI 数据源失败时在生成前停止；天气失败时继续但不输出天气结论；Redis 失败不影响任务；无效引用被删除并记录 Trace；必需上下文超限时返回 `CONTEXT_BUDGET_EXCEEDED` 且不调用模型；连续两次修正不会累积前一次 Prompt、原始回复或完整 Trace；偏好命中时快照进入任务上下文；显式请求覆盖偏好；关闭记忆时不查询偏好；偏好存储失败时只使用本次请求继续执行。

- [ ] **步骤 2：把修正输入实现为结构化事实**

```java
public record RepairRequest(
    TravelRequirement immutableRequirement,
    ItineraryPlan previousPlan,
    List<ValidationIssue> issues,
    List<PlaceCandidate> allowedPlaces,
    List<KnowledgeReference> allowedReferences
) {}
```

修正调用仍必须经过 `PromptContextAssembler`。它只保留不可修改需求、上一版结构化方案、当前 `ValidationIssue`，以及受影响日期相关的 POI、路线、天气和引用；不得加入前几轮自然语言 Prompt 或模型原始回复。

- [ ] **步骤 3：实现不可绕过的最多两次修正限制**

保存每个生成版本和每份校验报告；修正后重新执行全部规则，禁止相信模型声称“已经修复”。

- [ ] **步骤 4：记录 Trace 步骤和客观指标**

记录状态、耗时、缓存命中、数据源调用次数、模型返回的实际 input/output Token、Java 估算输入量、各上下文分区用量、实际采用及裁剪的偏好字段、裁剪的 POI/知识数量、重试次数、来源覆盖率、路线覆盖率、预算完整率和规则通过率。天气工具 Trace 还必须记录原始坐标系与实际请求坐标系，用于定位坐标偏移或重复转换；只保存脱敏摘要，不保存 API Key、完整 Prompt、完整模型回复、偏好修改历史或历史对话。

- [ ] **步骤 5：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=TravelPlanningOrchestratorTest test
.\mvnw.cmd test
git add src
git commit -m "feat: orchestrate bounded planning repair and trace"
```

## 阶段五：异步 API、SSE 与 Vue 页面

### 任务 14：增加偏好接口、可持久化规划任务与 SSE 进度

**负责人：** Codex 执行，用户验收；完成后冻结前后端 API 契约。

任务十四冻结门禁补丁同时补齐 1.3 节的知识库管理后端契约；管理查询、状态机、删除和重建使用独立 `KnowledgeAdministrationService` 与 `KnowledgeAdministrationPersistence`，不堆入上传入库服务。

**涉及文件：**
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/task/PlanningTaskService.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/task/PlanningTask.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/task/PlanningTaskStatus.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/task/PlanningTaskExecutorConfiguration.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/api/TravelPlanningController.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/api/PlanController.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/api/UserTravelPreferenceController.java`
- 新建： `travel_agent/src/main/java/com/wyx/travelagent/planning/api/UserTravelPreferenceRequest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/api/TravelPlanningControllerTest.java`
- 测试： `travel_agent/src/test/java/com/wyx/travelagent/planning/api/UserTravelPreferenceControllerTest.java`

- [ ] **步骤 1：编写 API 所有权和状态测试**

POST 返回 `202 Accepted` 和任务 ID；GET 只能返回当前用户任务，其他用户访问返回 404；每个用户同时只能有一个活动任务；SSE 断开后后台任务继续运行；完成状态中返回已保存的行程 ID。创建任务的 `useSavedPreferences` 默认值为 `true`，但服务端必须保留客户端显式传入的 `false`。

- [ ] **步骤 2：编写并实现偏好 CRUD 接口**

测试 GET、PUT、DELETE 只能操作 JWT 当前用户；请求体不接受 `userId`；非法兴趣、节奏、交通枚举和超过 255 字符的住宿偏好返回字段级校验错误；PUT 整体覆盖，DELETE 可幂等调用；接口响应不得包含聊天历史、历史行程或其他用户数据。Controller 只负责鉴权上下文和 DTO 映射，读写与合并规则统一委托 `PreferenceMemoryService`。

- [ ] **步骤 3：实现有界任务执行器**

配置适合当前电脑的小型固定线程池和有界队列；任务过多时返回 `PLANNING_QUEUE_FULL`，禁止无限创建线程。

- [ ] **步骤 4：实现服务启动时的遗留任务处理**

把服务重启前遗留的 `RUNNING` 任务标记为 `FAILED`，错误码为 `SERVER_RESTARTED`；禁止静默恢复执行到一半的模型或工具序列。

- [ ] **步骤 5：根据持久化 Trace 实现 SSE 事件**

发送需求校验、检索、工具、生成、校验、修正、完成和失败事件。重连时读取当前任务状态与已持久化 Trace，不依赖仅存在内存中的 Emitter。

- [ ] **步骤 6：运行测试并提交**

```powershell
.\mvnw.cmd -Dtest=TravelPlanningControllerTest,UserTravelPreferenceControllerTest test
.\mvnw.cmd test
git add src
git commit -m "feat: expose preference memory and asynchronous planning"
```

### 任务 15：用旅行规划与详情页面替换旧推荐流程

**负责人：** Codex（仅修改 `web/**`）。

**涉及文件：**
- 新建： `travel_agent/web/src/api/planning.js`
- 新建： `travel_agent/web/src/api/preferences.js`
- 新建： `travel_agent/web/src/views/Planner.vue`
- 新建： `travel_agent/web/src/views/PlanDetail.vue`
- 新建： `travel_agent/web/src/components/planning/PlanningProgress.vue`
- 新建： `travel_agent/web/src/components/planning/DayTimeline.vue`
- 新建： `travel_agent/web/src/components/planning/EvidenceList.vue`
- 修改： `travel_agent/web/src/router/index.js`
- 测试： `travel_agent/web/tests/planner-contract.test.cjs`

- [ ] **步骤 1：编写前端契约测试**

断言表单提交城市、日期范围、人数、总预算、同行类型、兴趣、节奏、住宿位置、交通方式、特殊要求和 `useSavedPreferences`；偏好存在时只预填尚未被用户编辑的字段，关闭开关后不再应用；断言详情页能区分 API 事实、文档证据和 AI 估算。

- [ ] **步骤 2：运行测试并确认失败**

```powershell
npm test
```

在 `travel_agent\web` 中运行。预期：新契约测试失败，因为对应模块尚未创建。

- [ ] **步骤 3：实现旅行规划页面**

使用 Element Plus 结构化表单，加载当前用户偏好并提供“使用已保存偏好”开关。偏好只预填空字段，用户后续输入不被异步返回覆盖；提交时发送 `useSavedPreferences`，并在摘要中标明实际采用的偏好字段。偏好接口失败只显示非阻塞提示，不妨碍用户手动填写和创建任务。创建任务后订阅进度；断线重连后查询任务状态；只有 `COMPLETED` 才跳转 `/plans/:id`。若状态为 `NEEDS_ADJUSTMENT`，展示冲突和调整建议。

- [ ] **步骤 4：实现行程详情页面**

Render daily timelines, route verification, weather availability, cost evidence type, citations with source links, warnings, objective coverage metrics, and version selector. Never display unknown opening hours as “all day”.

- [ ] **步骤 5：运行测试和生产构建**

```powershell
npm test
npm run build
```

预期：全部 Node 测试通过，Vue 生产构建退出码为 0。

- [ ] **步骤 6：提交变更**

```powershell
git add web
git commit -m "feat: add travel planner and sourced itinerary views"
```

### 任务 16：增加行程历史、偏好设置、知识库管理和前端权限处理

**负责人：** Codex（仅修改 `web/**`）。

任务十四补丁已经提供全部知识库管理后端接口；任务十六不得再修改后端契约或 Java 代码，只消费 1.3 节冻结 DTO。

**涉及文件：**
- 新建： `travel_agent/web/src/views/MyPlans.vue`
- 新建： `travel_agent/web/src/views/PreferenceSettings.vue`
- 新建： `travel_agent/web/src/views/KnowledgeAdmin.vue`
- 新建： `travel_agent/web/src/api/knowledge.js`
- 修改： `travel_agent/web/src/views/Login.vue`
- 修改： `travel_agent/web/src/router/index.js`
- 修改： `travel_agent/web/src/plugins/axios.js`
- 测试： `travel_agent/web/tests/admin-auth-contract.test.cjs`

- [x] **步骤 1：编写角色和资源所有权界面测试**

断言登录成功后只把 `accessToken` 和 `UserProfile` 写入 `sessionStorage`，不保存密码或偏好副本；Axios 请求拦截器统一附加 `Authorization: Bearer <token>`；偏好设置要求登录；管理路由要求 `role=ADMIN`；普通用户看不到管理入口；后端 401 时清除登录状态并跳转登录页，403 时保留登录状态并显示无权限提示。

- [x] **步骤 2：实现“我的行程”页面**

列表展示状态、城市、日期、当前版本、创建时间、覆盖率指标和重新规划操作。版本比较必须展示校验结果变化，不能只显示原始 JSON。

- [x] **步骤 3：实现偏好设置页面**

展示并编辑兴趣、节奏、常用交通方式和住宿位置偏好；说明不会保存日期、预算、同行信息、特殊要求或聊天记录。保存前由用户主动确认，清空操作二次确认并调用 DELETE；成功后刷新服务端数据，不把偏好长期复制到 `localStorage` 或 `sessionStorage`。

- [x] **步骤 4：实现知识库管理页面**

文件、标题、城市、发布机构、来源 URL、发布日期和文档类型均为必填项。页面展示 `UPLOADED`、`INDEXING`、`ACTIVE`、`DISABLED`、`FAILED` 状态，以及有界切片预览、启停、删除和重建索引操作；只有 ACTIVE 与 DISABLED 可互相切换，删除前必须先停用，管理员权限最终由后端判定。

- [x] **步骤 5：分别完成前端验证与后端契约确认**

```powershell
npm test
npm run build
```

Codex 在 `web` 目录运行上述前端命令。用户在仓库根目录运行 `.\mvnw.cmd test`，并确认任务、行程、知识库和认证接口仍符合 1.3 节契约。任一侧失败都不能进入提交步骤。

- [x] **步骤 6：提交变更**

```powershell
git add web
git commit -m "feat: add plan history preference settings and knowledge administration"
```

## 阶段六：评测、加固、文档与简历证据

### 任务 17：增加可重复执行的 Agent 评测集

**负责人：** Codex 执行，用户审核评测用例、指标口径和实际报告。

**涉及文件：**
- 新建： `travel_agent/src/test/resources/evaluation/travel-cases.json`
- 新建： `travel_agent/src/test/java/com/wyx/travelagent/evaluation/TravelAgentEvaluationTest.java`
- 新建： `travel_agent/src/test/java/com/wyx/travelagent/evaluation/EvaluationReportWriter.java`
- 新建： `travel_agent/docs/evaluation-method.md`

- [x] **步骤 1：添加 30～50 条明确评测用例**

用例同时包含合法方案以及预算过低、日期缺失、亲子、老人、雨天户外活动、POI 距离过远、知识库为空、数据源错误、伪造 POI ID、伪造 chunkId、非法 JSON、两次修正失败、偏好命中、显式输入覆盖偏好、关闭记忆、清空记忆、用户偏好隔离、偏好存储不可用、最大五日/50 POI/5 知识切片/500 Token 偏好分区的边界上下文，以及必需内容超过预算等场景。每条用例都明确声明预期状态和必须出现的错误码。

- [x] **步骤 2：优先实现确定性离线评测**

CI 使用固定 fixture、假数据源和假模型，计算虚构 POI 拦截率、引用覆盖率、路线覆盖率、规则通过率、修正成功率、平均修正次数、偏好合并正确率、用户隔离、缓存行为、任务状态准确率和上下文裁剪确定性。真实冒烟测试额外记录估算输入 Token 与服务端实际 input Token 的偏差，任何简历数据都只能引用实际报告。

- [x] **步骤 3：增加需单独启动的真实冒烟测试 Profile**

真实联调测试必须显式启用真实测试 Profile，并通过部署环境变量或被 Git 忽略的 `application-local.yml` 提供凭据；普通 `mvn test` 永远不运行这组测试。它只对文档中声明的小规模用例调用 DashScope、高德、Open-Meteo、MySQL 和 Redis。

- [x] **步骤 4：运行确定性评测**

```powershell
.\mvnw.cmd -Dtest=TravelAgentEvaluationTest test
```

预期：在 `target/evaluation/` 下生成报告，所有用例的预期状态和错误码断言均通过。

- [x] **步骤 5：提交变更**

```powershell
git add src/test docs
git commit -m "test: add reproducible travel agent evaluation"
```

### 任务 18：最终验证、项目文档和基于实测的简历描述

**负责人：** Codex 负责执行验证、整理证据并编写材料；用户提供个人账号侧必要操作，审核真实数据来源、评测结果和最终简历描述。

**涉及文件：**
- 修改： `travel_agent/README.md`
- 新建： `travel_agent/docs/architecture.md`
- 新建： `travel_agent/docs/data-sources.md`
- 新建： `travel_agent/docs/api.md`
- 新建： `travel_agent/docs/openapi/travel-agent.yaml`
- 新建： `travel_agent/docs/apifox-test-plan.md`
- 新建： `travel_agent/docs/demo-script.md`
- 新建： `travel_agent/docs/resume-notes.md`

- [x] **步骤 1：运行完整后端验证**

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

预期：两条命令退出码均为 0，核心测试没有被跳过。

- [x] **步骤 2：运行完整前端验证**

```powershell
npm ci
npm test
npm run build
```

在 `travel_agent\web` 中运行。预期：依赖安装、测试和生产构建全部成功。

- [x] **步骤 3：执行文档规定的真实冒烟测试**

状态（2026-10-15）：经用户两次明确授权完成最终重跑，Failsafe 1/1 通过；两个真实规划场景均为 `COMPLETED`，共 3 次模型调用、1 次自动修正、2 条 DOCUMENT 引用，POI/路线/天气调用为 3/1/1。Redis 不可达且缓存命中为 0，按设计回源；场景仍有费用证据、费用完整性、开放时间和路线未验证 warning。受控路线失败来自 loopback 注入，不表示高德真实故障。

使用一个杭州两日游请求、一个路线数据源失败场景、一条文档引用，以及一个需要自动修正的方案。记录真实调用次数、耗时、版本、引用、缓存命中和最终状态，禁止记录密钥。

- [ ] **步骤 4：使用 Apifox 完成契约与业务流程测试**

状态（2026-10-15）：OpenAPI、四条流程、断言和报告模板已完成；当前环境没有可控的 Apifox 项目/客户端会话，因此实际导入与运行仍待用户执行。

把 `docs/openapi/travel-agent.yaml` 导入 Apifox，至少建立四个场景：`验证码→登录→查询当前用户`、`管理员登录→上传文档→等待ACTIVE→预览切片`、`用户登录→创建规划→查询状态→读取行程`、`普通用户访问管理接口返回403`。为状态码、`ApiResponse`、必填字段、枚举、Token 传递和最终任务状态添加断言，并保存实际运行报告。Swagger UI 只作为可选的交互文档，不作为这组回归测试的替代品。

- [x] **步骤 5：根据实际行为编写项目文档**

README 必须包含无 Docker 启动方式、本地忽略配置与部署环境变量的使用边界、数据来源边界、架构、规划流程、结构化偏好记忆及隐私边界、上下文预算与裁剪顺序、故障行为、截图、评测方法、真实评测结果、已知限制，以及四个参考仓库和许可证说明。

- [x] **步骤 6：只使用真实结果编写简历描述**

写入真实测试用例数量和实测指标，禁止编造成功率。将 RAG 证据、受控工具、Java 校验与修正、Trace 和评测等核心 AI 贡献，与普通 CRUD 和界面工作分开描述。

- [x] **步骤 7：检查仓库整洁与密钥泄露**

```powershell
git status --short
git ls-files | Select-String -Pattern 'application-local|\.env$|target/|node_modules/|web/dist/'
```

预期：最终提交前工作区干净，第二条命令没有找到被跟踪的密钥配置或生成目录。

执行说明（2026-10-15）：未跟踪的 `data/`、两份既有计划文档和 `src/main/java/com/wyx/BCryptGenerator.java` 属于用户已有文件，本任务不修改、不暂存；检查命中的 `application-local.yml.example` 是无真实值的安全模板。任务 18 文件未发现密钥模式，也未包含 `application-local.yml` 中的任何实际配置值。

- [x] **步骤 8：提交达到发布条件的文档**

```powershell
git add README.md docs
git commit -m "docs: publish travel agent architecture and evaluation"
```

## 七、阶段验收门槛

- 阶段一：后端构建成功、SQL 初始化或旧库升级路径验证通过（含用户偏好表）、敏感配置未进入 Git、JWT 签名/过期/签发者校验通过、ThreadLocal 能在请求后清理、管理员权限测试通过。
- 阶段二：数据源契约测试覆盖成功和失败，高德 GCJ-02 与 Open-Meteo WGS84 的边界和转换测试通过，Redis 是可降级依赖，任意工具无法执行。
- 阶段三：管理员可以导入带来源文档，城市隔离有效，伪造引用会被拒绝。
- 阶段四：所有 POI 均来自工具，Java 能按“本次显式输入优先”合并偏好并保存应用快照，能捕获硬冲突，上下文预算和确定性裁剪测试通过，必需内容超限时不会调用模型，最多两次修正生效，版本和 Trace 已持久化。
- 阶段五：用户可以查看、修改、清空或对单次任务关闭偏好记忆，可以提交、观察、重新打开并检查行程，管理员可以管理文档，前后端构建均通过。
- 阶段六：确定性评测通过，真实冒烟证据已记录，文档可复现，简历描述只使用实测指标。
