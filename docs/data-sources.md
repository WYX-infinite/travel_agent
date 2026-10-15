# 数据源、坐标与失败行为

## 1. 数据源矩阵

| 数据源 | 生产用途 | 坐标/存储 | 缓存 | 失败语义 |
|---|---|---|---|---|
| MySQL | 用户、偏好、知识元数据/切片、任务、行程版本、Trace | 结构化表 | 无 | 核心持久化失败通常终止当前操作并返回稳定错误码 |
| 本地知识索引 | ACTIVE 文档的向量检索 | `data/index/knowledge-index.json` | 内存/文件索引 | 检索/索引不一致时不伪造引用；入库失败标记 FAILED |
| DashScope-compatible Chat | 初稿与修正 | 结构化 JSON | 无 | 超时/非法结构映射为稳定生成失败；有界重试 |
| DashScope-compatible Embedding | 文档切片向量 | 本地索引 | 无 | 上传入库失败，不发布半成品 ACTIVE 索引 |
| 高德 Web 服务 | POI 搜索、地理编码、步行/公交/驾车路线 | GCJ-02 | Redis | POI/路线关键事实不可用时失败或进入需调整，不由模型补造 |
| Open-Meteo | 日级天气 | WGS84 | Redis | 可降级为天气缺失 warning，不能声称实时天气已验证 |
| Redis | POI/路线/天气成功结果加速 | TTL 缓存 | 自身 | 非事实源；读写失败直接回源，不改变成功业务结果 |

## 2. 知识文档

管理员上传 `PDF`、`DOCX`、`Markdown` 或 `TXT`。默认单文件上限 10 MB、提取正文上限 1,000,000 个 Java 字符。处理顺序为：

```text
multipart 校验 → 安全文件名/大小/实际 MIME → Tika 提取
→ 确定性切片 → 数据库事务 → Embedding → 临时索引
→ 原子发布正式索引 → 文档 ACTIVE
```

`sourceUrl` 只作为来源元数据保存，服务端不会抓取该 URL。检索同时受 `status=ACTIVE` 和城市过滤。模型看到的每个知识切片都有允许的 `chunkId`；输出中的文档引用必须命中本轮允许集合。

停用文档从白名单和重建后的索引排除。删除只允许处于安全状态的文档，并受控清理上传文件；API 不返回本机绝对路径或 embedding 向量。

## 3. 高德与 Open-Meteo

高德返回 GCJ-02 坐标；Open-Meteo 接收 WGS84。天气调用前通过本地确定性转换器转换坐标，并在工具结果中保留请求/来源坐标系元数据。不能把 GCJ-02 坐标直接传给 Open-Meteo。

路线事实包括交通方式、距离、时长和 `verified`。只有实际路线工具成功的相邻移动才能计入路线覆盖率。模型给出的路线描述不构成已验证路线。

## 4. 受控工具边界

工具计划在执行前统一验证：

- 工具名必须在固定白名单。
- 城市必须与目的地一致。
- 只接受声明过的参数，类型和数量有上限。
- POI 搜索最多 5 个关键词组，每次最多 10 条。
- 总调用数受 `ToolExecutionPolicy` 限制。
- 任意 URL、URI、endpoint、host 或 URL-like 字符串被拒绝。
- 路线工具类型必须与 `TravelMode` 一致。

因此模型不能把用户文本变成任意 HTTP 请求，也不能更改供应商 base URL。

## 5. 缓存语义

缓存名称为 `poi`、`geocode`、`route`、`weather`。key 使用规范化城市/参数和 SHA-256 摘要，避免直接把长文本放入 key。

```text
cache hit(success) → 返回缓存，provider call count 不增加
cache miss         → 调供应商；成功后写缓存
cache read failure → 调供应商
cache write failure→ 保留供应商成功结果
provider failure   → 不写缓存，向编排层返回稳定失败
```

Redis 降级不等于供应商降级；如果关键 POI 或路线供应商本身失败，规划不能把猜测作为事实。

## 6. 配置

| 配置 | 默认/变量 |
|---|---|
| Chat/Embedding base URL | `TRAVEL_AI_BASE_URL`，默认 DashScope compatible-mode |
| Chat 模型 | `TRAVEL_CHAT_MODEL`，默认 `qwen-plus` |
| Embedding 模型 | `TRAVEL_EMBEDDING_MODEL`，默认 `text-embedding-v3` |
| DashScope Key | `DASHSCOPE_API_KEY` |
| 高德 base URL | `TRAVEL_AMAP_BASE_URL`，默认 `https://restapi.amap.com` |
| 高德 Key | `AMAP_WEB_API_KEY` |
| Redis | `TRAVEL_REDIS_HOST/PORT/PASSWORD` |
| 知识索引 | `TRAVEL_KNOWLEDGE_INDEX_FILE`，默认 `data/index/knowledge-index.json` |

base URL 只能由受信部署配置提供，不来自请求体或模型输出。

## 7. 能与不能得出的结论

离线供应商测试使用 `MockRestServiceServer`/Fake，只证明请求构造、解析、错误映射、坐标转换和缓存语义。它不证明高德或 Open-Meteo 当前在线、覆盖完整或数据准确。只有显式运行 `real-smoke` 后才能报告当次真实连通结果；即使单次成功也不能推出长期可用性或 SLA。
