# Agent 评测方法

## 1. 评测边界

任务十七提供两套明确分离的检查：

- `DETERMINISTIC_OFFLINE`：默认执行。读取 `src/test/resources/evaluation/travel-cases.json`，只使用固定虚构 fixture、假模型、假数据源和内存缓存；复用生产代码中的需求校验器、行程校验器、引用白名单、偏好合并、上下文组装、工具调度和指标计算器。
- `REAL_SMOKE`：必须显式启用。用于检查已配置的 DashScope、高德、Open-Meteo、MySQL、Redis 可用性和知识检索链路。Redis 不可达时按生产语义回源并在报告中记录。它不是默认测试，也不计入离线指标。

数据集包含 40 条具有唯一 ID 的固定用例。覆盖合法方案、低预算、缺失日期、亲子、老人、雨天户外、路线时间不足、空知识库、三类数据源故障、伪造 POI、伪造 chunkId、非法 JSON、有界结构重试、两次修正失败、偏好命中/覆盖/关闭/清空/隔离/存储故障、五日输入、50 个候选 POI、5 个知识切片、500 Token 偏好分区、必需上下文超限、缓存命中/未命中/故障降级和零分母指标。

## 2. fixture 与真实数据的边界

`travel-cases.json` 顶层标记 `fixtureDataClass=SYNTHETIC_FICTIONAL_ONLY`。离线评测中的城市、名称、POI ID、坐标、费用、天气、知识正文、模型文本和供应商返回值均为虚构测试数据，不代表真实地点、真实供应商质量或真实模型效果。

离线报告只能说明固定输入下的确定性规则、边界和编排行为是否符合预期。不得将它描述为真实端到端成功率、真实路线准确率、真实引用质量、真实模型遵循率或线上 SLA。真实冒烟报告与离线报告使用不同文件名和 `evaluationMode`。

## 3. 指标公式

每个指标都在报告中保存 `numerator`、`denominator`、`formula` 和显示值。除平均修正次数外，比率显示为百分比；分母为零时显示 `N/A`，不显示 0% 或 100%。

| 指标 | 公式 |
|---|---|
| 虚构 POI 拦截率 | 被 `UNKNOWN_POI` 拦截的伪造 POI 尝试数 / 全部伪造 POI 尝试数 |
| 引用覆盖率 | 具有 `FACT` 或有效 `DOCUMENT` 证据的行程条目数 / 全部行程条目数 |
| 路线覆盖率 | 具有已验证路线事实的相邻移动数 / 全部相邻移动数 |
| 规则通过率 | 未产生问题的规则数 / 实际执行的确定性规则数 |
| 修正成功率 | 经至少一次修正后完成的用例数 / 需要修正的用例数 |
| 平均修正次数 | 修正尝试总数 / 需要修正的用例数 |
| 偏好合并正确率 | 合并结果及来源符合预期的偏好用例数 / 全部偏好用例数 |
| 用户隔离结果 | 只读取到目标用户偏好的隔离用例数 / 全部隔离用例数 |
| 缓存行为 | 命中、未命中或故障降级符合预期的用例数 / 全部缓存用例数 |
| 任务状态准确率 | 实际状态和错误/诊断码均符合预期的用例数 / 全部用例数 |
| 上下文裁剪确定性 | 重复组装得到相同结果且满足边界的用例数 / 全部上下文边界用例数 |

`emptyPlanCitationCoverage` 是零分母审计夹具：空行程的“有来源条目数 / 条目数”为 `0/0`，报告必须显示 `N/A`。

## 4. 运行离线评测

在仓库根目录使用 JDK 21：

```powershell
.\mvnw.cmd -Dtest=TravelAgentEvaluationTest test
```

输出固定写入：

- `target/evaluation/travel-agent-offline-evaluation.json`
- `target/evaluation/travel-agent-offline-evaluation.md`

`target/` 被 Git 忽略。重复运行相同代码和 fixture 时，案例顺序、指标顺序及报告内容相同；报告不使用系统时间、随机数或网络结果。

## 5. 启用真实冒烟

普通 `mvn test` 使用 Surefire，类名为 `TravelAgentRealSmokeIT` 的真实冒烟不会被发现。Maven Profile `real-smoke` 会跳过重复的普通单测，只通过 Failsafe 选择真实冒烟；全量单测必须先按 README 单独执行：

```powershell
$env:TRAVEL_REAL_SMOKE_ENABLED='true'
.\mvnw.cmd -Preal-smoke verify
```

真实凭据只能来自部署环境变量，或来自 Git 忽略的 `src/main/resources/application-local.yml`。不要把凭据写进测试、命令参数、报告或已跟踪配置。真实冒烟会产生真实外部请求，运行前应确认供应商配额、本地 MySQL 状态和 Redis 可用性；Redis 不可达不阻断冒烟，但会增加回源等待并使缓存命中为零。

真实冒烟会检查 MySQL、记录 Redis 可用性、调用真实高德 POI/路线、Open-Meteo 两日天气和 ACTIVE 杭州知识检索，然后通过生产编排器运行杭州两日规划。首个场景要求最终 `COMPLETED`，安排浙江省博物馆并持久化至少一个允许的 DOCUMENT chunkId；如首个场景没有发生修正，会追加一个修正触发场景并要求至少一次修正。路线失败使用指向不可达 loopback 的受控适配器实例验证安全错误映射，不把它描述为高德真实故障。

测试需要本地数据库至少有一个用户，并预先导入一份可被“浙江省博物馆 免费参观 开放时间 官方”检索命中的 ACTIVE 浙江省博物馆知识文档。测试会新增规划任务、行程版本和脱敏 Trace，不执行删除或清空。

真实冒烟报告为 `target/evaluation/travel-agent-real-smoke.json`，只保存执行时间、服务/模型名称、调用次数、Token 计数、延迟、任务终态、版本数、缓存命中数、修正次数、DOCUMENT 引用数和稳定问题/失败码；不保存 Prompt、模型回复、API Key、Authorization 头、用户名、taskId、planId 或请求自由文本。断言不满足时测试失败；报告会在规划场景完成后先落盘，保留真实失败证据。

### 2026-10-15 实际快照

经明确授权运行 `qwen3.7-plus` 与 `text-embedding-v4` 后，Failsafe 1/1 通过。两个固定场景均为 `COMPLETED`，版本数分别为 1 和 2；共 3 次模型调用、7458 个实际输入 Token、10,923 个实际输出 Token、1 次修正和 2 条 DOCUMENT 引用，规划场景总耗时 219,111 ms。供应商调用计数为 POI 3、路线 1、天气 1；连通性检查返回 3 个 POI、2 日天气、5 条允许引用和一条已验证路线。

Redis 本次不可达，缓存命中为 0，回源后场景仍完成。两个场景均保留 `COST_EVIDENCE_UNVERIFIED`、`COST_INFORMATION_INCOMPLETE`、`OPENING_HOURS_UNKNOWN`、`ROUTE_NOT_VERIFIED`，所以该快照不能解释为零告警、路线全覆盖或真实模型成功率。受控失败的 `PROVIDER_TIMEOUT` 来自不可达 loopback，不代表高德当时不可用。

## 6. 报告字段

- `evaluationMode`：`DETERMINISTIC_OFFLINE` 或 `REAL_SMOKE`，禁止混用。
- `datasetVersion`：离线数据集版本。
- `caseCount` / `passedCaseCount`：用例总数和完全符合预期的数量。
- `metrics[]`：指标名称、分子、分母、公式和显示值。
- `cases[]`：用例 ID、场景、预期/实际状态、预期/实际错误或诊断码、是否通过、安全失败原因和修正次数。
- `limitations[]`：报告不能支持的结论。

真实冒烟另外使用 `services`、`connectivity`、`routeFailureEvidence`、`scenarios[]` 和 `totals`。每个场景只包含安全名称、最终状态、版本数、延迟、模型/供应商调用计数、Token 计数、缓存命中、修正次数、DOCUMENT 引用数及稳定结果码。

失败原因只使用固定类别，例如状态不匹配或错误码不匹配。报告禁止包含完整 Prompt、完整模型回复、密钥、令牌、自由文本用户信息、堆栈或本机敏感路径。

## 7. 当前限制与不能得出的结论

- 假模型只验证严格 JSON、白名单和有界重试，不衡量真实模型的语言质量或事实正确性。
- 假数据源只验证调用、错误传播和缓存语义，不衡量高德或 Open-Meteo 的实时覆盖和精度。
- `ConservativeTokenEstimator` 是确定性安全估算器，不等同于任何供应商官方 Tokenizer。
- 引用覆盖率只表示条目是否带允许的结构化证据，不代表引用正文充分支持活动安排。
- 规则通过率包含 warning 对规则组的影响；`COMPLETED` 不等于没有 warning。
- 当前 40 条固定用例不是统计抽样，不能给出置信区间、泛化率或线上成功率。
- README、简历或演示材料只能引用已生成报告中的数据，并必须标明是离线 fixture 还是单独运行的真实冒烟结果。
