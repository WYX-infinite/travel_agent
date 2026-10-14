# Agent 评测方法

## 1. 评测边界

任务十七提供两套明确分离的检查：

- `DETERMINISTIC_OFFLINE`：默认执行。读取 `src/test/resources/evaluation/travel-cases.json`，只使用固定虚构 fixture、假模型、假数据源和内存缓存；复用生产代码中的需求校验器、行程校验器、引用白名单、偏好合并、上下文组装、工具调度和指标计算器。
- `REAL_SMOKE`：必须显式启用。用于检查已配置的 DashScope、高德、Open-Meteo、MySQL、Redis 和知识检索链路是否可访问。它不是默认测试，也不计入离线指标。

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

普通 `mvn test` 使用 Surefire，类名为 `TravelAgentRealSmokeIT` 的真实冒烟不会被发现。只有 Maven Profile `real-smoke` 才绑定 Failsafe：

```powershell
$env:TRAVEL_REAL_SMOKE_ENABLED='true'
.\mvnw.cmd -Preal-smoke verify
```

真实凭据只能来自部署环境变量，或来自 Git 忽略的 `src/main/resources/application-local.yml`。不要把凭据写进测试、命令参数、报告或已跟踪配置。真实冒烟会产生真实外部请求，运行前应确认供应商配额和本地 MySQL/Redis 状态。

真实冒烟报告为 `target/evaluation/travel-agent-real-smoke.json`，只保存数量、布尔结果和 Token 计数。它记录保守估算输入 Token、服务端实际输入 Token，以及两者的绝对差值；不保存 Prompt、模型回复、API Key、Authorization 头、用户输入或数据库内容。

## 6. 报告字段

- `evaluationMode`：`DETERMINISTIC_OFFLINE` 或 `REAL_SMOKE`，禁止混用。
- `datasetVersion`：离线数据集版本。
- `caseCount` / `passedCaseCount`：用例总数和完全符合预期的数量。
- `metrics[]`：指标名称、分子、分母、公式和显示值。
- `cases[]`：用例 ID、场景、预期/实际状态、预期/实际错误或诊断码、是否通过、安全失败原因和修正次数。
- `limitations[]`：报告不能支持的结论。

失败原因只使用固定类别，例如状态不匹配或错误码不匹配。报告禁止包含完整 Prompt、完整模型回复、密钥、令牌、自由文本用户信息、堆栈或本机敏感路径。

## 7. 当前限制与不能得出的结论

- 假模型只验证严格 JSON、白名单和有界重试，不衡量真实模型的语言质量或事实正确性。
- 假数据源只验证调用、错误传播和缓存语义，不衡量高德或 Open-Meteo 的实时覆盖和精度。
- `ConservativeTokenEstimator` 是确定性安全估算器，不等同于任何供应商官方 Tokenizer。
- 引用覆盖率只表示条目是否带允许的结构化证据，不代表引用正文充分支持活动安排。
- 规则通过率包含 warning 对规则组的影响；`COMPLETED` 不等于没有 warning。
- 当前 40 条固定用例不是统计抽样，不能给出置信区间、泛化率或线上成功率。
- README、简历或演示材料只能引用已生成报告中的数据，并必须标明是离线 fixture 还是单独运行的真实冒烟结果。
