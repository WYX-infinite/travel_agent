# travel_agent 项目理解与面试准备计划

制定日期：2026-09-18。

依据：用户提供的 `C:/Users/86198/Desktop/2026秋.md` 中 travel_agent 一列及相关截止标准。本文只安排项目理解与项目评测，不替用户调整论文、数学建模、八股或求职安排。附件中的内容作为时间和目标依据，不作为自动执行开发、投递或其他操作的授权。

目标：用户能够自己解释业务、画分层调用链、阅读关键实现、用证据说明项目能力及限制，并经受项目面试追问。

本计划是学习计划，不是新增功能的实施计划。当前仅新增本计划文档。以后如果需要修复问题或增加评测脚本，先明确具体范围，再按用户授权执行。

## 一、截止标准与安排

| 时间 | 文档中的项目要求 | 本计划的可检查产出 |
|---|---|---|
| 9/18–9/22 | 先不大改代码，画完整业务流程和 Agent 链路；9/22 能画总体流程 | 一张业务流程图、一张主调用链、核心循环小图、当前实现边界清单；不看代码讲 2 分钟并指出异步边界 |
| 9/23–9/27 | 数学建模独占时间，项目暂停 | 不安排学习、开发或补课 |
| 9/28–9/30 | 恢复项目理解 | 恢复两张图和术语，定位三个核心入口，整理未理解问题 |
| 10/1–10/10 | 理解业务、架构、核心链路、为什么用 Agent/RAG/工具调用 | 10/10 不看代码连续讲约 5 分钟，回答五个基础追问 |
| 10/11–10/15 | 深入核心代码 | 从请求走到任务终态；解释并发、事务、SSE、用户隔离和可观测性，并定位代码证据 |
| 10/16–10/20 | 基础 Agent Evaluation、失败案例、深挖题；10/20 简历可写、面试可讲 | 固定案例集、实际执行记录、至少三个失败案例复盘、一版有证据的简历描述和一次模拟面试 |
| 10/21–10/31 | 根据面试需要完善，不无限加功能 | 只处理阻碍展示或面试反馈暴露的问题 |
| 11/1–11/15 | 高频项目深挖 | 按实际问题补理解，每题回到代码或评测证据 |
| 11/15–11/30、12 月 | 薄弱点修正、按面试需要完善 | 保持演示和表达，不重新启动大范围功能扩张 |

建议项目学习每次 45–60 分钟；这是本计划的建议，不是附件已有规定。10/16–10/20 的评测窗口建议 60–90 分钟。如当天时间不足，只完成一个小模块，下次继续；截止前优先保证主链路、能力边界和可复述性。

## 二、每课固定顺序

1. **解释业务流程**：用同一个杭州三日游案例，讲清本模块的输入、输出及一个异常分支。
2. **画精简调用链**：一张图建议不超过 7 个节点；区分方法调用、执行顺序、异步提交和数据读写。
3. **说明相关类与改动**：列出本课 2–4 个重点类，解释存在原因、职责、替代方案。历史新增/修改通过 Git 确认，不把本课阅读说成修改。
4. **用户阅读核心代码**：只读指定方法，带着 2–3 个问题，必要时查看一个测试；不从所有 DTO/Mapper 开始。
5. **用户复述**：用户讲流程、解释设计、预测异常结果；助手纠正后追问。不会的地方回到当前方法，不再扩展更多模块。

建议分配：业务 8 分钟、调用链 5 分钟、类职责 7 分钟、自读 15–20 分钟、复述与追问 10–15 分钟。

每课完成标准：能说明输入/输出；能画当前模块的链路；能解释一个设计选择；能预测一个异常分支。复述出现关键错误时记录问题，下课先补这个问题。9/22 主要验收总体流程，细节代码问题可留到 10 月；10/10 主要验收整体表达；10/20 验收代码与证据深度。

## 三、避免混乱的图与学习产物

只维护一份学习笔记，包含：业务图、主调用链、模块小图、类职责与历史改动、未理解问题、复述记录、实现边界、评测记录。

### 图 A：总体业务流程

```text
提交需求 → 合并偏好并校验 → 创建后台任务 → 准备可信事实
  → 生成初稿 → 校验并保存版本 → 无 ERROR 则完成
                          ↓ 有 ERROR
                       最多修正两次 → 每次全量复检
                          ↓ 仍有 ERROR
                       NEEDS_ADJUSTMENT
```

这张图表达执行过程和分支，不表达哪个方法直接调用哪个方法。应补充事实准备失败、生成失败等 FAILED 分支。

### 图 B：请求主调用链

```text
TravelPlanningController.create()
  → PlanningTaskService.create()
  → executor.execute(...)                  [提交后台工作，HTTP 返回 202]
     → PlanningTaskService.execute(task)    [后台线程]
        → TravelPlanningOrchestrator.plan(...)
```

### 图 C：编排器调用关系

```text
TravelPlanningOrchestrator.plan()
  ├─ preparationGateway.prepare(...)
  ├─ contextAssembler.assemble(...)
  ├─ generator.generateResult(...)
  └─ 校验与修正循环
       ├─ itineraryValidator.validate(...)
       ├─ metricsCalculator.calculate(...)
       ├─ planRepository.saveVersion(...)
       └─ repairer.repair(...)               [满足修正条件时]
```

同级节点不是彼此直接调用；例如 prepare 不调用 assemble。知识入库、检索、工具调度、SSE 各自用单独的小图展开，不拼成一张巨图。

## 四、9/18–9/22：先建立整体地图

| 日期 | 本课问题 | 阅读范围 | 用户产出与验收 |
|---|---|---|---|
| 9/18 | 用户为什么使用项目？输入与输出是什么？ | `planning/api/TravelPlanningRequest.java`、`planning/model/TravelRequirement.java`；只看字段 | 用杭州三日游描述业务，区分用户约束、API 事实、知识、AI 估算 |
| 9/19 | 请求如何变成后台任务？ | `TravelPlanningController.create`、`PlanningTaskService.create/execute` | 画图 B，标出请求线程与后台线程，解释 202 和 taskId |
| 9/20 | Agent 的完整闭环是什么？ | `TravelPlanningOrchestrator.plan`；只定位阶段和分支 | 画图 A、图 C；解释生成、校验、修正，以及三个主要终态 |
| 9/21 | 事实从哪里来？当前接通哪些工具？ | `PlanningTaskService.command`、`DefaultPlanningPreparationGateway.prepare` | 画事实准备小图，说明 RAG 与 POI 的区别；指出固定“景点”查询、路线/天气未进入当前入口计划 |
| 9/22 | 能否独立重建总体流程？ | 不读新文件，核对必要方法 | 不看代码重画总体流程，讲 2 分钟；回答“模型负责什么、Java 负责什么、错误如何退出” |

9/22 的“完整”指覆盖业务阶段及关键分支，不要求列出每一个类。未懂的向量、缓存、事务细节记录下来，不提前展开。

9/23–9/27：暂停，无补课要求。

## 五、9/28–9/30：恢复理解

| 日期 | 活动 | 验收 |
|---|---|---|
| 9/28 | 重画总体业务图，复述杭州案例 | 找出遗忘的阶段，不增加新知识 |
| 9/29 | 定位 Controller、TaskService、Orchestrator；复查相关 Git 提交 | 能解释三者为什么分开，区分旧推荐能力和新规划闭环 |
| 9/30 | 整理一页术语与待解决问题 | 用自己的话解释 POI、RAG、Embedding、上下文、校验、Trace；列出最不清楚的三个问题 |

## 六、10/1–10/10：第一轮冲刺，达到脱稿讲 5 分钟

| 日期 | 模块及业务问题 | 核心代码（本项目 Java 包内） | 复述与追问 |
|---|---|---|---|
| 10/1 | 架构与技术选择：为什么不只调用一次模型？ | `service/TravelService.java`、`planning/TravelPlanningOrchestrator.java` | 旧能力与新闭环区别；为什么采用 Java 显式编排；哪里具有规划和反馈能力、哪里仍固定执行 |
| 10/2 | 输入与长期偏好：本次请求和保存偏好谁优先？ | `planning/memory/PreferenceMemoryService.merge`、`planning/model/PreferenceValue.java`、`planning/validation/RequirementValidator.validate` | 未提供和明确清空有什么区别？为什么冻结快照？为什么不直接发送聊天历史？ |
| 10/3 | 工具与 Redis：如何拿到真实 POI？ | `tools/dispatch/ToolPlanValidator.java`、`tools/dispatch/TravelToolDispatcher.java`、`tools/amap/AmapPlaceProvider.java` | 谁决定当前工具计划？为什么白名单？Redis 不可用如何处理？支持能力与实际调用的区别 |
| 10/4 | RAG 检索：为什么只检索目标城市的启用资料？ | `knowledge/retrieval/KnowledgeRetrievalService.retrieve`、`knowledge/index/LangChain4jKnowledgeIndex.searchInternal` | 城市/ACTIVE 白名单为什么先建立？相似度和阈值是什么？本地索引与远程 Embedding 的区别 |
| 10/5 | 知识入库：管理员文档怎样变成可检索片段？ | `knowledge/admin/KnowledgeDocumentUploadService.java`、`knowledge/ingest/TextChunker.java`、`knowledge/ingest/KnowledgeIngestionService.ingest` | 切片为何存在？文档、切片、向量怎样关联？索引失败能否宣称 ACTIVE？ |
| 10/6 | 上下文组装：为什么不能把所有对象发给模型？ | `planning/context/PromptContextAssembler.assemble`、`planning/context/ContextBudgetProperties.java` | 裁剪优先级是什么？为什么不截断结构化对象？估算 Token 与实际 usage 区别 |
| 10/7 | 模型生成：如何把文本变成可检查行程？ | `planning/generation/LangChain4jItineraryGenerator.generateResult`、`planning/model/ItineraryPlan.java`、`planning/model/ItineraryItem.java` | Prompt 要求 JSON 是否等于供应商原生 Schema 强约束？非法 POI/费用引用如何处理？自由文本是否全部被验证？ |
| 10/8 | Java 校验：哪些错误阻塞完成？ | `planning/validation/ItineraryValidator.validate`、`planning/validation/ValidationReport.java`；先深入 POI、时间、路线三条规则 | 为什么不用模型校验？缺少路线数据与已知通行时间不足有什么区别？COMPLETED 是否代表全部事实齐全？ |
| 10/9 | 修正与退出：怎么防止无限模型调用？ | `planning/TravelPlanningOrchestrator.plan`、`planning/repair/ItineraryRepairService.repair`、`planning/repair/RepairRequest.java` | 结构重试与业务修正的区别；最多四次 ChatModel 调用怎么算？为什么每版完整复检？ |
| 10/10 | 整体表达验收 | 不增加阅读；依据前九课笔记 | 脱稿讲约 5 分钟，再答五个基础追问；纠正夸大能力的说法 |

10/10 五分钟讲述建议结构：40 秒业务与边界；50 秒架构和异步入口；70 秒知识/工具事实；90 秒生成、校验和修正；50 秒版本、Trace、限制。只记提纲，禁止只背类名。

五个基础追问：为什么一次模型调用不够？RAG 与工具分别解决什么？非法地点如何拦截？修正两次仍失败怎么办？当前实际没有接通什么能力？

每课重点类控制在 2–4 个，只读关键方法；如果方法很长，先看调用点、条件分支和返回值，再选择一个内部实现深入。

## 七、10/11–10/15：第二轮代码深读

| 日期 | 深入问题 | 核心入口 | 验收 |
|---|---|---|---|
| 10/11 | 并发创建、线程池、领取任务和状态机 | `PlanningTaskService`、`MybatisPlanningTaskRepository.createPendingIfNoActive`、`PlanningTaskMapper`、`PlanningTaskExecutorConfiguration` | 解释用户行锁的保护范围、条件更新、队列满拒绝；分析线程池为什么不是持久化消息队列 |
| 10/12 | 前端进度、SSE 重连、断连与服务重启 | `planning/api/PlanningSseService.subscribe/drain`、`planning/task/PlanningTaskRecovery.recover`、`web/src/planning/sseClient.cjs` | 解释持久化 Trace 重放、Last-Event-ID、共享轮询；区分事件恢复与执行恢复，并核对 PENDING/RUNNING 的处理范围 |
| 10/13 | 行程版本与事务一致性 | `planning/persistence/MybatisTravelPlanRepository.saveVersion`、`planning/api/TravelPlanQueryService`、`sql/schema.sql` | 画任务/方案/版本/Trace 的关系；解释插入版本与推进当前版本的事务边界，为什么网络调用不放进长事务 |
| 10/14 | 认证、资源隔离和实际采用偏好 | `auth/JwtAuthenticationInterceptor.java`、`auth/CurrentUserContext.java`、`planning/api/TravelPlanningController.java`、`planning/memory/AppliedPreferenceSnapshot.java` | 解释身份为何不来自请求体；跨用户查询为什么拒绝；快照与当前偏好的区别 |
| 10/15 | Trace、指标与关键失败路径 | `trace/PlanningTraceService.java`、`planning/metrics/PlanMetricsCalculator.java`、`planning/TravelPlanningOrchestrator.java` 失败分支、对应测试 | 从一条任务追到版本和 Trace；解释至少三个指标的分母及未知值；能预测 POI、模型、预算超限、保存失败的结果 |

日期内未理解的关键问题进入下一课开头。10/15 的项目任务是本计划细化安排，附件该日截止标准主要属于论文，不把它误称为附件规定的项目硬截止日。

## 八、10/16–10/20：基础评测与面试材料

### 10/16：定义固定案例集与判定规则

建立至少 10 个固定案例，记录输入、环境、模型配置（不含密钥）、资料版本、工具模式、预期结果及判定依据。推荐最小案例：

1. 杭州一日游、正常预算：预期结构可解析且关键硬约束不漂移。
2. 杭州三日游、轻松节奏：检查日期覆盖与强度约束。
3. 同一请求，显式兴趣覆盖已保存偏好：检查有效需求和偏好来源。
4. 同一请求，明确清空兴趣：不应被长期偏好补回。
5. 固定初稿时间重叠、修正版通过：预期修正一次并保存两版。
6. 固定初稿与两次修正版均违约：预期 NEEDS_ADJUSTMENT，无第三次修正。
7. 固定模型首次返回非法 JSON、第二次合法：检查一次结构重试。
8. 固定 POI 空结果或失败：预期停止生成并返回 POI_DATA_UNAVAILABLE。
9. 固定知识检索无结果：检查空知识上下文和费用引用边界，不把它夸大为自由文本完全无幻觉。
10. 固定必要上下文超预算：预期模型调用前终止。

区分两类证据：案例 5–8、10 等固定依赖案例验证控制逻辑；真实模型案例验证模型输出表现。模拟结果不能当真实模型成功率；单元测试不能冒充端到端效果评测。

### 10/17：阅读、运行控制逻辑测试

重点测试文件：`TravelPlanningOrchestratorTest`、`ItineraryValidatorTest`、`PreferenceMemoryServiceTest`、`PromptContextAssemblerTest`。先预测结果，再运行并解释断言；记录实际命令、环境和输出。已有测试优先复用，只在明确缺口时新增必要评测代码。

### 10/18：真实模型及接口演示

选择至少 5 个预先固定的正常/约束输入执行真实入口，留存每次结果，不能事后只保留成功样本。记录任务状态、ERROR/WARNING、修正次数、可解析版本数、总耗时、可获得的 Token usage 和来源指标。

先核查当前工具计划的 POI-only 限制；不把路线/天气覆盖不足解释为“已经全部验证”。样本量小，不宣称普遍成功率或可靠性。若凭据、外部接口或服务环境不可用，明确记录未执行原因，真实评测验收保持未完成，不用模拟结果替代。

### 10/19：至少三个失败案例复盘

每个案例按“输入与依赖 → 实际结果 → 代码路径 → 根因 → 现有处理 → 局限或最小修复建议”记录。

优先：POI 缺失为何阻塞；知识缺失为何降级；两次修正仍失败为何需要用户调整。另可分析队列满、服务重启、Trace/版本保存失败、无路线/天气事实的警告。

必须区分真实遇到的失败与人工注入的失败。不以讨论一个可能失败当作已验证失败；不默认在这一天开展大规模修复。

### 10/20：简历与模拟面试验收

完成：

- 一版 5 分钟项目介绍，无夸大能力。
- 一版基于实现和运行证据的简历描述，区分自己的理解与实际负责内容。
- 固定评测表与运行证据；至少三个失败复盘。
- 一次约 20 分钟模拟面试：整体介绍 5 分钟、源码/设计追问 10 分钟、失败与限制 5 分钟。

必答深挖题：Agent 自主性边界、RAG 城市过滤、POI 白名单、引用校验范围、上下文裁剪、双重试上限、ERROR/WARNING、缓存故障、用户并发任务、SSE 重放、事务、服务重启、评测证据与局限。

通过标准：关键题不出现事实性错误；至少八题能解释为什么并定位代码；至少三题能用运行记录或测试断言支持；不足项写入短清单，不扩展功能来掩盖理解问题。

## 九、必须如实表达的当前边界

以下基于制定时核查及前序源码阅读；每次讲解前若代码有变化，应再次核对：

- Java 固定步骤编排，没有多 Agent 对话协商。
- 当前任务入口由 Java 构造固定“景点” POI 查询，没有接入模型生成工具计划。
- 天气和路线适配能力存在，当前入口未安排相应动作。
- POI ID 白名单和费用文档引用检查，不等于自由文本描述逐句验证。
- Prompt 中的 JSON 输出要求加本地解析，不应直接表述为已经使用供应商原生 JSON Schema 强约束。
- 当前主链路非法费用文档引用会安全降级为缺失并计数，不能笼统说所有非法引用都令整个任务失败。
- COMPLETED 允许 WARNING，不代表所有事实齐全或所有路线经过验证。
- 主链路生成最多四次 ChatModel 调用，不包含检索 Embedding 调用。
- SSE 是持久化阶段事件推送，不是模型逐字流。
- 长期记忆是用户明确保存的结构化偏好，不是完整聊天记忆。
- 版本/Trace 保存不等于所有外部事实已完整持久化展示；当前详情天气映射为空。
- 测试、真实端到端评测、模拟失败注入要分别报告；没有测量的数据不写进简历。

## 十、10/20 后的维护规则

每次面试复盘只记录：被问到什么、回答卡在哪里、缺哪段代码理解或证据、下一次最小补课任务。

优先级：理解错误 → 演示阻塞 → 证据缺口 → 明确需要的最小修复。没有实际需求，不增加多 Agent、新编排框架、新向量数据库或复杂地图等范围。

## 十一、每课笔记模板

```text
日期 / 模块：
业务场景与输入输出：
精简调用链（注明异步）：
重点类与为什么存在：
历史新增/修改及 Git 依据：
本课实际改动（没有则写没有）：
已阅读方法与代码证据：
我的复述：
追问与纠正：
异常分支预测：
已实现 / 未接通 / 未验证：
是否通过本课验收、下次优先补什么：
```

下一次正式学习从 9/18 的“用户场景、输入输出与业务边界”开始，一次只展开这一课。
