# 多约束旅行规划 Agent 设计规格

日期：2026-08-31  
状态：设计已确认，待用户审阅书面规格  
目标岗位：Java + AI 应用开发实习  
预计周期：6 周

## 1. 背景与现状

现有项目由 `travel_agent` 后端和 `travel_agent_web` 前端组成，已具备以下基础：

- Spring Boot 3.5、Java 21、LangChain4j、MyBatis-Plus 和 MySQL。
- Vue 3、Element Plus、登录、推荐、路线、收藏等页面。
- DashScope 聊天模型、Embedding 模型和流式输出配置。
- `SseEmitter` 流式返回、结构化 Java 对象输出和结果持久化。

当前 AI 主链路仍是四个独立 Prompt 调用：目的地推荐、流式旅行建议、文本转数据库对象和路线生成。代码尚未实现真正的知识库检索、来源引用、外部工具、会话状态、确定性规则校验、自动修正和评测。现有 `/route` 由模型估算车次、票价和时间，不适合作为可信旅行数据展示。

用户另有 `uushop` 微服务项目，已经能够展示传统 Java 后端和微服务能力。本项目不再拆分微服务，而集中展示 Java 在 RAG、工具编排、约束校验、异步任务、可观测性和 AI 评测中的应用。

## 2. 核心决策

项目保留旅行主题，但从“目的地推荐聊天应用”收敛为：

> 面向国内单个城市 1～5 日游的多约束旅行规划 Agent。

用户已确定目的地，提交日期、预算、人数、同行类型、兴趣和节奏等条件。系统检索可信城市资料，调用高德与天气服务，生成结构化行程，并使用 Java 规则校验地点真实性、时间、距离、预算和证据覆盖。存在冲突时，系统把明确的校验错误反馈给模型，最多自动修正两次。

首期采用普通 Java 显式编排，不引入 LangGraph4j，也不拆成多个 Agent。核心编排通过 `TravelPlanningOrchestrator` 完成，各节点保持独立接口，以便以后替换编排实现。

## 3. 项目目标

1. 生成 1～5 天的国内单城市结构化行程。
2. 每个地点必须来自本次高德 POI 查询结果。
3. 天气、地址、坐标、距离和通行时间保存数据源与查询时间。
4. 景区规则、城市提示等知识性结论必须引用管理员上传的可信文档。
5. 对估算内容明确标记，不把模型估算表述为实时事实。
6. 使用 Java 规则检测日期、时间、路线、强度和预算冲突。
7. 校验失败后携带具体错误自动修正，最多两次。
8. 保存初稿、修正版、最终版、验证结果和完整执行轨迹。
9. 使用 Redis 缓存外部接口结果和短期验证码。
10. 提供可重复的单元测试、接口测试、RAG 测试和 Agent 评测集。
11. 提供用户可控的结构化旅行偏好记忆，并在不发送完整历史对话的前提下复用于后续规划。

## 4. 非目标

六周首期不实现：

- 真实机票、火车票、酒店查询或预订。
- 跨城市交通规划和国际旅行。
- 小红书或其他平台爬取。
- 多 Agent、LangGraph4j、模型微调或代码执行沙箱。
- 微服务拆分、Docker、Kubernetes 和独立向量数据库。
- 自动抓取整个互联网或建立大规模旅行数据集。
- 对没有可靠来源的营业时间、票价、排名和评价作出断言。
- 复杂地图可视化；地图路线展示仅作为提前完成后的增强项。
- 保存完整聊天记录、根据模型推断自动修改长期偏好，或把历史行程全文作为长期记忆发送给模型。

## 5. 参考项目与借鉴边界

### 5.1 LangChain4j Examples

仓库：<https://github.com/langchain4j/langchain4j-examples>  
许可证：Apache-2.0

借鉴：

- LangChain4j 与 Spring Boot 的集成方式。
- RAG、结构化输出、工具组织、本地 EmbeddingStore 和测试方式。
- `AiServices`、模型适配和检索增强的职责划分。

边界：不迁移整个示例仓库；按照本项目的领域模型和分层重新实现。

### 5.2 TripStar-Java

仓库：<https://github.com/LeeFly-cn/TripStar-Java>  
许可证：GPL-2.0

借鉴：

- 旅行需求到规划、审核和修正的业务流程。
- 地图与天气真实数据、Java 硬校验、进度通知和 AI Trace 思想。

边界：不复制 GPL-2.0 源码；不引入其小红书研究、Node.js 组件、Spring AI Alibaba 或完整基础设施。

### 5.3 Embabel Ragbot

仓库：<https://github.com/embabel/ragbot>  
许可证：Apache-2.0

借鉴：

- Apache Tika 文档解析、文档切片、本地持久化检索和无 Docker 知识库思想。

边界：不引入 Embabel；首期不照搬其 Lucene 实现，使用 LangChain4j 本地 EmbeddingStore。

### 5.4 Spring AI Playground

仓库：<https://github.com/spring-ai-community/spring-ai-playground>  
许可证：Apache-2.0

借鉴：

- 旅行工具白名单、工具轮数上限、失败降级、人工可见进度和执行 Trace。

边界：不引入 Spring AI，不迁移其通用 Playground 产品结构。

## 6. 总体架构

系统保留现有 Spring Boot、Vue 3、MySQL 和 LangChain4j。新增核心编排层：

```text
TravelPlanningController
          ↓
TravelPlanningOrchestrator
          ├─ RequirementValidator
          ├─ PreferenceMemoryService
          ├─ KnowledgeRetrievalService
          ├─ TravelToolPlanService
          ├─ TravelToolDispatcher
          ├─ ItineraryGenerator
          ├─ ItineraryValidator
          ├─ ItineraryRepairService
          └─ PlanningTraceService
```

编排器只负责步骤、状态和分支，不直接写 Prompt、操作数据库或解析第三方 JSON。

核心状态对象：

```java
class TravelPlanningContext {
    TravelRequirement requirement;
    AppliedPreferenceSnapshot appliedPreferences;
    List<KnowledgeReference> references;
    List<PlaceCandidate> places;
    WeatherResult weather;
    ItineraryPlan draftPlan;
    List<ValidationIssue> validationIssues;
    int retryCount;
    ItineraryPlan finalPlan;
}
```

主流程：

```text
读取用户明确保存的偏好
  → 合并本次输入并校验需求
  → 检索知识库
  → 查询天气
  → 生成受控工具计划
  → 搜索POI并查询必要路线
  → 生成行程草稿
  → Java规则校验
  → 有ERROR则最多修正两次
  → 保存最终方案、版本、指标和Trace
```

## 7. 用户输入与输出

### 7.1 输入字段

- 目的地城市：必填。
- 出行日期：必填，限制 1～5 天。
- 同行人数：必填。
- 总预算：必填。
- 同行类型：独自、情侣、亲子、朋友或老人。
- 兴趣偏好：美食、历史、自然、摄影、购物等，可多选。
- 行程节奏：轻松、适中或紧凑。
- 住宿位置：选填，作为每天路线起点。
- 主要交通：步行、公交、打车或自驾。
- 特殊要求：选填。
- 是否使用已保存偏好：默认开启，用户可对本次规划关闭。

首期使用结构化表单，不以完全开放式聊天采集关键约束。自然语言仅作为补充说明。

### 7.2 结构化偏好记忆

首期实现用户可控的长期偏好记忆，只保存用户在偏好设置中明确确认的字段：兴趣偏好、行程节奏、常用交通方式和住宿位置偏好。系统不记忆目的地、日期、人数、预算、同行类型，也不把自由文本“特殊要求”自动写入长期记忆，避免把一次性或敏感信息错误复用于其他行程。

规划请求中的显式字段始终覆盖已保存偏好。只有用户开启“使用已保存偏好”且本次请求未填写对应可选字段时，`PreferenceMemoryService` 才补全缺失项，并生成 `AppliedPreferenceSnapshot` 记录实际采用了哪些字段。该快照随规划任务保存，保证以后查看或重新规划时能够解释当时使用了什么偏好；修改长期偏好不会反向改写既有方案。

偏好只能通过确定性 Java 逻辑读取和合并，不调用模型提取或总结。系统提供查看、修改、清空接口和前端入口；普通用户只能操作自己的偏好。首期不保存完整聊天记录，不使用 LangChain4j `ChatMemory`，也不从历史行程中静默学习。任务内的 `TravelPlanningContext` 属于工作记忆，MySQL 中的任务、方案版本和 Trace 属于可追溯历史，两者均不等同于跨任务对话记忆。

### 7.3 输出内容

- 行程摘要、天气概况和预计总费用区间。
- 每天的时间线和活动安排。
- 地点名称、地址、坐标、坐标系、POI ID 和来源。
- 相邻地点之间的距离、预计通行时间和交通方式。
- 知识库引用、原文片段和来源 URL。
- 规则校验结果、风险提示和待确认事项。
- 方案状态、版本号和生成时间。

每个活动为结构化对象，而不是一段不可校验的文本：

```java
class ItineraryItem {
    LocalTime startTime;
    LocalTime endTime;
    String poiId;
    String poiName;
    String address;
    String activity;
    TravelSegment nextSegment;
    CostEstimate cost;
    List<EvidenceReference> evidence;
}
```

## 8. 数据真实性与证据规则

输出信息分为三类：

1. **API 事实数据**：POI、地址、经纬度、距离、通行时间和天气，保存提供方与查询时间。
2. **知识库数据**：景区规则、游览提醒、交通说明等，必须关联文档和切片。
3. **模型估算**：建议停留时间、餐饮费用区间等，前端必须显示“AI 估算”。

禁止模型自行生成：

- 不存在于本次工具结果的地点。
- 实时票价、酒店价格和余量。
- 没有来源的营业时间。
- 虚构评分、排名、评论和安全事实。
- 没有依据却精确到个位数的费用。

资料不足时输出“暂无可靠数据，建议出行前从官方渠道确认”。每个具体地点的 `poiId` 必须来自高德，每条知识性结论必须引用有效 `chunkId`，所有估算必须带估算标识。

系统不使用模型自报置信度，而计算：POI 来源覆盖率、路线验证覆盖率、知识引用覆盖率、预算数据完整率和规则校验通过率。

## 9. 知识库设计

### 9.1 数据来源

管理员可以上传 PDF、DOCX、Markdown 和 TXT。上传时必须填写：

- 文档标题。
- 适用城市。
- 发布机构。
- 来源 URL。
- 发布日期。
- 文档类型：官方公告、景区说明、交通指南或人工整理。

演示数据优先使用文旅局、景区官网和政府交通部门资料。人工整理资料必须列出参考来源。首期准备 2～3 个演示城市，每城 5～10 份可信文档。

### 9.2 入库流程

```text
上传与元数据校验
  → 文件类型、大小、哈希校验
  → Apache Tika提取正文
  → 清理页眉页脚和重复空白
  → 按标题与段落切片
  → DashScope text-embedding-v3 生成向量
  → 写入 LangChain4j 本地磁盘 EmbeddingStore
  → 先写 data/index/knowledge-index.json.tmp
  → 原子替换 data/index/knowledge-index.json
  → MySQL保存文档、切片和来源
  → 条件迁移为 ACTIVE
```

默认切片约 500～800 个中文字符，重叠约 100 字。最终参数由评测结果校准，而不是宣称为通用最佳值。

首期 Embedding 模型固定为 DashScope `text-embedding-v3`，通过远程 API 调用；“本地 EmbeddingStore”仅表示向量索引保存在项目本机磁盘，不表示在本机运行 Embedding 模型。首期不接入本地 Hugging Face、ONNX、DJL 或 Python 推理。正式索引文件固定为 `data/index/knowledge-index.json`，临时文件固定为 `data/index/knowledge-index.json.tmp`，两者均不得提交到 Git。更换 Embedding 模型后必须使用全部启用切片重建索引，禁止混用不同模型生成的向量。

文档状态固定为 `UPLOADED`、`INDEXING`、`ACTIVE`、`DISABLED`、`FAILED`。上传入库只执行 `UPLOADED → INDEXING → ACTIVE/FAILED`；管理员只可执行 `ACTIVE ↔ DISABLED`，不得使用 UPLOADED 冒充停用。

### 9.3 检索与引用

1. 按目的地城市和文档启用状态过滤。
2. 向量检索 Top 8 候选片段。
3. 使用评测集校准的相关性阈值过滤。
4. 最多向模型提供 5 个片段。
5. 模型输出使用的 `chunkId`。
6. 后端校验 `chunkId` 是否属于本次检索结果。

无有效片段时不得生成知识性结论。MySQL 的城市与 ACTIVE 切片联表查询始终是检索白名单事实源；即使磁盘索引短暂残留旧向量，DISABLED 文档也不得进入检索结果。删除、停用、启用或手动重建时，使用数据库当前 ACTIVE 全集原子替换索引；状态变更后的重建失败必须条件恢复原状态并再次重建，补偿失败显式返回，不得假成功。

## 10. 受控工具调用

### 10.1 工具白名单

```java
enum TravelToolType {
    SEARCH_POI,
    GEOCODE,
    WALKING_ROUTE,
    DRIVING_ROUTE,
    TRANSIT_ROUTE,
    WEATHER_FORECAST
}
```

模型只生成结构化 `ToolPlan`，Java 校验后由 `TravelToolDispatcher` 执行。模型不能构造任意 URL，也不能访问白名单之外的能力。

限制包括：

- 工具中的城市必须与用户目的地一致。
- POI 查询关键词最多 5 组。
- 每组 POI 最多保留 10 条。
- 每次任务有总工具调用上限。
- 所有参数执行长度、格式和枚举校验。

### 10.2 统一返回模型

```java
class ToolResult<T> {
    boolean success;
    String provider;
    LocalDateTime retrievedAt;
    T data;
    String errorCode;
    String errorMessage;
}
```

第三方 JSON 只存在于基础设施适配层。业务层只依赖 `PlaceCandidate`、`TravelSegment` 和 `WeatherResult` 等内部模型。

### 10.3 坐标系边界与转换

WGS84 是覆盖全球的地理坐标基准，不代表某个具体地区；GPS 与 Open-Meteo 天气接口使用 WGS84。高德在中国大陆返回和接收 GCJ-02 坐标。内部模型禁止使用无法判断坐标系的裸 `longitude`、`latitude` 组合，统一定义：

```java
enum CoordinateSystem {
    WGS84,
    GCJ02
}

record GeoPoint(
    double longitude,
    double latitude,
    CoordinateSystem coordinateSystem
) {}
```

- `AmapPlaceProvider` 返回的 `PlaceCandidate.location` 必须标记为 `GCJ02`。
- `AmapRouteProvider` 只使用 GCJ-02 坐标；输入不是 GCJ-02 时返回 `COORDINATE_SYSTEM_UNSUPPORTED`，禁止静默混用。
- `OpenMeteoWeatherProvider` 发起请求前必须获得 WGS84：输入为 WGS84 时直接使用；输入为中国大陆范围内的 GCJ-02 时，通过本地纯 Java `Gcj02ToWgs84Converter` 转换一次；中国大陆范围之外不执行 GCJ-02 偏移算法。
- 转换器不得调用新的第三方服务，不得修改原始 `PlaceCandidate`，不得对已经是 WGS84 的坐标重复转换。
- 经度必须位于 `[-180, 180]`，纬度必须位于 `[-90, 90]`；坐标系缺失或数值非法时返回稳定错误码，不向外部接口发送请求。
- 工具 Trace 记录原始坐标系和实际请求坐标系，便于排查位置偏移；不得把坐标系转换结果描述成新的 POI 数据来源。

### 10.4 Redis 缓存

项目连接用户虚拟机中的 Redis，不要求 Docker。使用 Spring Data Redis 和 JSON 序列化：

```text
travel:poi:{city}:{keywordHash}       24小时
travel:geocode:{addressHash}          7天
travel:route:{originHash}:{destinationHash}:{mode}  1小时
travel:weather:{city}:{date}          30分钟
```

- 只缓存成功结果。
- 长参数转哈希，不直接写入 Key。
- Trace 记录命中、未命中和真实接口耗时。
- Redis 失败时绕过缓存并调用外部接口，不使规划任务失败。
- 首期不实现 Caffeine 与 Redis 两级缓存。

### 10.5 密钥管理

敏感配置采用双模式：本地开发从被 Git 忽略的 `application-local.yml` 读取，部署环境优先从环境变量读取。仓库只提交 `application-local.yml.example`，不得提交真实值：

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: ${DASHSCOPE_API_KEY:${travel.local-secrets.dashscope-api-key:}}
amap:
  api-key: ${AMAP_WEB_API_KEY:${travel.local-secrets.amap-web-api-key:}}
travel:
  jwt:
    secret: ${TRAVEL_JWT_SECRET:${travel.local-secrets.jwt-secret:}}
```

模型名称和 Base URL 可在本地配置中切换；API Key、数据库密码和 JWT 密钥不得提交。日志和 Trace 不记录含 Key 的完整 URL。
由于现有配置文件曾直接保存真实密钥，如果旧 Key 曾进入 Git 历史或远程仓库，实施时还必须在模型服务控制台轮换，不能只把旧值移动到本地配置文件。

## 11. 行程生成与 Java 校验

### 11.1 上下文预算与组装

模型调用前必须由 Java `PromptContextAssembler` 组装上下文，禁止 Controller、编排器或 Prompt 模板直接拼接完整对象。项目使用独立于模型宣传上限的保守预算，默认配置为：

```yaml
travel:
  ai:
    context:
      model-context-window-tokens: 32768
      max-estimated-input-tokens: 16000
      reserved-output-tokens: 4000
      safety-margin-tokens: 2000
      max-knowledge-tokens: 4000
      max-tool-fact-tokens: 6000
      max-memory-tokens: 500
      max-poi-count: 20
      max-knowledge-chunks: 5
```

`max-estimated-input-tokens + reserved-output-tokens + safety-margin-tokens` 不得超过配置的窗口值。`32768` 是本项目的保守配置基线，不是对模型官方上限的声明；切换模型或模型版本前，必须核对服务商文档并把 `model-context-window-tokens` 设置为不高于实际支持值。由于首期不引入 Qwen 专用本地 Tokenizer，Java 的输入计数是保守估算而不是官方精确值；每次真实调用以后，以模型服务返回的 input/output Token usage 为准写入 Trace，并在评测阶段校准估算系数。

上下文按以下优先级保留：

1. 系统约束和结构化输出 Schema。
2. 本次请求中用户明确填写的字段。
3. 修正调用中的 `ValidationIssue`。
4. 已批准 POI、相关路线和天气事实。
5. 实际采用的结构化偏好快照。
6. 按相关度排序且引用可验证的知识切片。
7. 可选描述和解释性文字。

超出预算时先移除可选描述，再删除未被本次请求显式确认的记忆派生偏好、低相关知识片段和未入选行程的低优先级候选 POI；路线和天气只传内部 DTO 的必要字段，不传第三方原始 JSON。偏好分区不得超过 `max-memory-tokens`，只传实际采用的结构化字段，不传偏好修改历史。不得从中间截断 JSON、日期、金额或引用对象。系统不向模型发送完整历史对话、完整文档、完整 Trace 或前几次 Prompt；重新规划只使用结构化需求、当时的偏好快照和指定方案版本。若系统约束、用户硬约束和必需事实本身已经超过预算，则返回 `CONTEXT_BUDGET_EXCEEDED`，不得继续调用模型。

### 11.2 结构化生成与确定性校验

模型只能从本次允许的 POI 列表中选择地点，并输出结构化 `ItineraryDraft`。

`ItineraryValidator` 使用确定性规则：

1. `poiId` 必须属于本次高德结果。
2. 行程天数必须与日期范围一致。
3. 活动时间不得重叠，结束时间必须晚于开始时间。
4. 相邻活动的时间差必须覆盖通行时间和缓冲时间。
5. 轻松、适中、紧凑分别限制每日活动强度。
6. 预算使用费用区间上限保守校验；数据不全时返回未完全验证警告。
7. 只有存在可靠营业时间时才执行营业时间校验。
8. 恶劣天气与户外安排冲突时生成警告。

问题分级：

```java
enum IssueSeverity {
    ERROR,
    WARNING
}
```

`ERROR` 必须修正，`WARNING` 可以随方案返回但必须展示。

## 12. 自动修正机制

修正服务接收：原草稿、结构化错误列表、允许使用的 POI、不可修改的用户约束和可信上下文。错误必须包含代码、日期、活动、事实值和所需调整量。每次修正均重新组装独立的预算内上下文，不拼接上一次 Prompt、模型原始回复或完整 Trace；优先只保留受影响日期、相关 POI、路线、天气和引用。

修正后的方案必须重新执行全部 Java 规则，不信任模型声称“已经修复”。最多修正两次。

两次后仍有 `ERROR`：

- 任务状态改为 `NEEDS_ADJUSTMENT`。
- 不把方案标记为完成。
- 返回具体冲突和可调整建议。
- 保存失败版本和 Trace。

## 13. 数据库设计

首期核心表：

### 13.1 `user`

在现有表增加 `role`，密码改为 BCrypt 哈希。角色限定为 `USER` 和 `ADMIN`。

### 13.2 `knowledge_document`

保存标题、城市、发布机构、来源 URL、发布日期、文件信息、哈希、状态和上传时间。

### 13.3 `knowledge_chunk`

保存文档 ID、切片序号、正文和 EmbeddingStore 中的标识。

### 13.4 `planning_task`

保存用户、原始输入 JSON、合并后的有效需求、实际采用的偏好快照、当前步骤、任务状态、失败原因、关联行程和时间戳。任务创建后冻结快照，之后修改长期偏好不会改变旧任务及其方案。

### 13.5 `travel_plan`

保存用户、城市、日期、当前版本、状态和时间戳，用于列表查询。

### 13.6 `travel_plan_version`

保存版本号、结构化行程 JSON、校验 JSON、指标 JSON 和创建时间。嵌套的每日行程使用 JSON 字段，避免过度拆表。

### 13.7 `planning_trace`

保存任务步骤、状态、耗时、脱敏输入摘要、输出摘要和错误信息。

### 13.8 `user_travel_preference`

每名用户最多一行，保存兴趣枚举集合、偏好节奏、常用交通方式、住宿位置偏好和更新时间。`user_id` 同时作为主键和指向用户表的外键。长期偏好更新只影响之后创建的任务；每个任务实际采用的字段另存为偏好快照。表中不保存聊天原文、模型总结、目的地、日期、人数、预算或完整历史行程。

## 14. API 设计

### 14.1 规划任务

```http
POST /api/planning-tasks
GET  /api/planning-tasks/{taskId}
GET  /api/planning-tasks/{taskId}/events
```

POST 立即返回任务 ID。后台执行规划，SSE 推送检索、工具、生成、校验和修正进度。SSE 断开不取消任务，用户可重新查询状态和最终结果。

### 14.2 行程

```http
GET  /api/plans
GET  /api/plans/{planId}
GET  /api/plans/{planId}/versions
POST /api/plans/{planId}/replan
```

普通用户只能访问自己的任务和行程。

### 14.3 知识库管理

```http
POST   /api/admin/knowledge-documents
GET    /api/admin/knowledge-documents
GET    /api/admin/knowledge-documents/{id}
GET    /api/admin/knowledge-documents/{id}/chunks
PATCH  /api/admin/knowledge-documents/{id}/status
DELETE /api/admin/knowledge-documents/{id}
POST   /api/admin/knowledge-documents/reindex
```

所有管理接口在后端执行 `ADMIN` 权限校验，`USER` 访问统一返回 403。列表和详情仅返回业务安全字段，不暴露服务端存储文件名、绝对路径或向量；详情额外返回切片数。切片预览按 `page=0&size=20` 分页，`size` 最大为 100，并按 `chunk_index` 稳定排序。

状态接口只接受 `ACTIVE` 与 `DISABLED`；同状态请求不重复写数据库，但返回成功前仍以 ACTIVE 全集重建索引完成对账。状态切换成功后以数据库当前 ACTIVE 全集原子重建索引，重建失败时条件恢复原状态并再次重建。删除仅允许 `DISABLED` 或 `FAILED` 文档，先按 ACTIVE 全集重建以清除可能遗留的非 ACTIVE 索引项，再将受控文件原子移动至上传根目录内的隔离区并短事务删除数据库记录；数据库删除失败时恢复文件，提交后的隔离文件清理失败只记录安全日志，不把已提交删除伪装成失败。手动重建返回活动文档数、切片数和完成时间；空库必须成功清空索引。状态、删除和手动重建在单实例内串行执行。

### 14.4 用户偏好记忆

```http
GET    /api/user/travel-preferences
PUT    /api/user/travel-preferences
DELETE /api/user/travel-preferences
```

GET 返回当前用户明确保存的结构化偏好；PUT 执行枚举、长度和所有权校验后整体覆盖；DELETE 清空当前用户偏好。创建规划任务时使用 `useSavedPreferences` 明确本次是否读取偏好。接口不得提供其他用户 ID 参数，也不得由模型自动调用修改。

## 15. 认证与权限修复

现有登录存在明文密码比较、验证码全局共享和返回完整用户实体的问题。首周必须修复。项目不引入完整 Spring Security，采用 JJWT、自定义 Spring MVC `HandlerInterceptor` 与轻量 `spring-security-crypto`：

- 登录使用 POST JSON，密码使用 BCrypt 保存和验证。
- JWT 通过标准 `Authorization: Bearer <token>` 传递，包含 `sub`、`role`、`iss`、`iat` 和 `exp`，默认有效期为 2 小时。
- JWT HMAC 密钥来自部署环境变量或被 Git 忽略的本地配置文件，不硬编码到已跟踪文件，不记录完整 Token。
- 单一 `JwtAuthenticationInterceptor` 保护全部 `/api/**`，只放行验证码和登录接口；`/api/admin/**` 要求 `ADMIN`。
- 当前用户写入 `CurrentUserContext`，拦截器在 `afterCompletion` 中无条件清理 ThreadLocal；异步规划显式传递用户 ID。
- 验证码按随机 `captchaId` 隔离，保存在 Redis 5 分钟并在验证后删除。
- 首期不维护服务端 JWT 黑名单。用户退出时前端删除本地 Token 和用户信息，已签发 Token 在 `exp` 到达前仍可能有效；这是当前范围内接受的限制。
- 登录响应返回 Access Token 与 `UserProfileVO`，不包含密码或密码哈希。
- Vue 仅把 Token 与用户资料保存在 `sessionStorage`，Axios 统一添加 Bearer Token。
- 认证失败、越权和资源不属于当前用户时返回统一错误结构。

## 16. 前端设计

保留并改造现有页面：

1. **旅行规划页**：结构化输入、任务进度、失败提示和结果摘要。
2. **行程详情页**：按天时间线、地点、路线、天气、费用、引用和警告。
3. **我的行程**：历史方案、状态、版本比较和重新规划。
4. **偏好设置**：查看、修改和清空长期偏好；规划页显示本次是否使用以及实际应用了哪些偏好。
5. **知识库管理**：文档上传、来源填写、处理状态、切片预览、启停、删除和重建索引。

原 `/suggest` 改造成规划页，`/routePlan` 改造成行程详情页，`/collect` 改造成我的行程。`/recommended` 降级为非核心辅助入口。

## 17. 异常与降级策略

任务状态：`PENDING`、`RUNNING`、`COMPLETED`、`NEEDS_ADJUSTMENT` 和 `FAILED`。

| 故障 | 行为 |
|---|---|
| 天气失败 | 继续生成，不输出天气建议，显示天气不可用 |
| 日期超出天气预报范围 | 不用历史平均值冒充预报，标记为暂无该日期预报 |
| 知识库无结果 | 只使用 API 事实，不生成知识性结论 |
| 路线失败 | 保留地点，标记距离和时间未验证 |
| POI 搜索失败 | 停止生成，禁止模型自行补造地点 |
| 模型非法结构 | 携带解析错误重试一次，仍失败则结束 |
| Java 校验失败 | 最多修正两次，仍失败则 `NEEDS_ADJUSTMENT` |
| 工具调用达上限 | 使用已有数据或返回数据不足 |
| Redis 失败 | 绕过缓存，直接调用外部服务 |
| 偏好读取失败 | 只使用本次请求继续规划，并在 Trace 中记录记忆未应用 |
| 知识库状态切换后索引重建失败 | 条件恢复原状态并按恢复后的 ACTIVE 全集再次重建；补偿失败时返回明确错误，禁止假成功 |
| 知识文档删除的数据库步骤失败 | 将隔离文件恢复到原受控位置；恢复失败时返回明确的补偿失败错误 |
| 知识文档删除提交后的隔离文件清理失败 | 数据库删除仍视为成功，隔离文件留待清理，并且日志不得泄露存储路径 |

外部调用设置连接和读取超时，最多重试一次。每名用户同时只允许一个规划任务，后台使用受限线程池。服务重启后，将遗留 `RUNNING` 任务标记为失败并允许重新执行。

## 18. 测试与评测

### 18.1 单元测试

- 日期、预算、时间重叠、路线缓冲和行程强度。
- POI 白名单、引用 ID 和修正次数上限。
- Redis Key、TTL 和缓存绕过。
- 证据覆盖率与预算完整率计算。
- 偏好合并优先级、关闭记忆、清空记忆和用户隔离。

### 18.2 接口测试

使用 MockWebServer 模拟高德、Open-Meteo 和模型接口的成功、超时、空结果、限流和非法响应。普通自动化测试不调用真实模型或真实外部 API。

### 18.3 RAG 测试

- 正确城市和文档召回。
- 其他城市文档隔离。
- 引用和切片匹配。
- 无相关资料时拒绝补造。

### 18.4 Agent 评测集

准备 30～50 条任务，覆盖预算过低、日期缺失、亲子或老人、雨天户外、地点过远、无知识结果、工具空结果、虚构 POI ID、偏好命中、显式输入覆盖偏好、关闭偏好和偏好清空等情况。

记录：虚构 POI 拦截率、知识引用覆盖率、路线验证覆盖率、规则通过率、任务成功率、平均修正次数、模型耗时、缓存命中率、Token 用量、上下文裁剪数量和估算/实际输入 Token 偏差。

## 19. 六周实施范围

### 第 1 周：底座与安全

- 本地忽略配置与部署环境变量、登录修复、数据库变更。
- 用户偏好表和所有权边界。
- DTO、Context、编排接口和统一错误结构。
- 移除模型虚构路线的旧核心逻辑。

### 第 2 周：真实工具

- 高德地理编码、POI、路线和 Open-Meteo。
- Redis 缓存、ToolPlan 白名单、调用限制和模拟测试。

### 第 3 周：知识库

- 管理上传、Tika、切片、Embedding、本地索引、检索和引用。

### 第 4 周：Agent 核心

- 结构化偏好合并、结构化行程、Java 校验、两次修正、版本和真实性指标。

### 第 5 周：任务与前端

- 异步任务、SSE、规划页、详情页、我的行程、偏好设置和知识库页面。

### 第 6 周：质量与求职材料

- 评测集、失败案例修复、README、架构图、API 文档、演示视频、截图、简历描述和面试问题。

## 20. 完成标准

项目只有同时满足以下条件才算首期完成：

1. 可从结构化表单创建规划任务并看到真实进度。
2. 所有地点均通过高德 POI ID 校验。
3. 知识性结论均有有效文档引用，估算均有标识。
4. 时间和路线冲突能被 Java 检测并触发修正。
5. 两次修正失败后停止，不发布含硬错误的方案。
6. Redis、天气或路线服务单独失败时按设计降级。
7. 管理员能上传、查看、停用和删除文档。
8. 普通用户不能访问管理接口或其他用户行程。
9. 自动化测试覆盖核心规则和外部失败场景。
10. 评测结果、数据来源和已知限制写入 README。
11. 用户可以查看、修改、清空或对单次任务关闭偏好记忆，且当前请求能覆盖历史偏好。
12. 知识库上传、列表、详情、切片预览、状态、删除和重建接口均受 ADMIN 保护；DISABLED 文档被数据库 ACTIVE 白名单排除，状态与删除失败补偿均有自动化测试。
