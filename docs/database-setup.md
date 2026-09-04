# 数据库初始化与旧库升级

本项目不接入 Flyway，也不会在应用启动时自动创建或修改数据库结构。数据库变更必须由操作人员在检查、确认和备份后手动执行。

## 安全规则

- 全新数据库只执行 `sql/schema.sql`。
- 已存在完整旧用户表、但尚无 Agent 业务表的本地数据库只执行 `sql/upgrade-from-legacy.sql`。
- 禁止对同一个数据库连续执行两份脚本。
- 禁止通过删除、清空或重建现有数据库来绕过升级问题。
- 不要在命令、脚本、终端历史或文档中写入数据库密码。让客户端交互式提示密码，或使用本机安全的凭据管理方式。
- 执行前必须确认备份文件位于用户认可的位置，并实际验证文件存在且大小非零。

## 第一步：只读检查

连接目标 MySQL 后，先确认当前数据库和服务状态：

```sql
SELECT DATABASE() AS current_database, VERSION() AS mysql_version;
SELECT 1 AS connection_ok;
```

检查现有表：

```sql
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_name;
```

检查用户表的实际名称、列、主键、索引和外键。以下查询中的 `user` 必须按实际表名核对，不能盲目替换：

```sql
SELECT table_name, column_name, column_type, is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'user'
ORDER BY ordinal_position;

SELECT table_name, index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'user'
ORDER BY index_name, seq_in_index;

SELECT table_name, constraint_name, column_name,
       referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND table_name = 'user'
ORDER BY constraint_name, ordinal_position;
```

还要核对旧推荐表 `ai_recommendations` 的列、主键和索引，确认它与当前 Java 实体使用的列一致。

登录账号必须具有确定性。升级前检查重复的非空用户名：

```sql
SELECT username, COUNT(*) AS duplicate_count
FROM `user`
WHERE username IS NOT NULL
GROUP BY username
HAVING COUNT(*) > 1;
```

查询必须返回零行。若存在重复用户名，停止升级并先由数据负责人决定保留或重命名哪个账号；脚本不会用 `LIMIT 1` 或自动合并掩盖冲突。

## 第二步：判定唯一执行路径

只能选择以下一种状态：

1. **全新数据库**：目标 schema 中不存在 `user`、`ai_recommendations` 和七张 Agent 业务表。选择 `schema.sql`。
2. **完整旧数据库**：存在结构完整的 `user` 与 `ai_recommendations`；`user` 尚无 `role`；以下七张 Agent 业务表全部不存在。选择 `upgrade-from-legacy.sql`。
3. **部分升级或结构不一致**：已存在部分 Agent 表、`role` 已存在但其他表不完整、用户表名称/主键/列与脚本不一致，或无法明确归入前两类。立即停止并请求用户确认，不执行任何脚本。

七张 Agent 业务表为：

- `user_travel_preference`
- `knowledge_document`
- `knowledge_chunk`
- `planning_task`
- `travel_plan`
- `travel_plan_version`
- `planning_trace`

## 第三步：在认可的位置完成可恢复备份

旧库升级前必须使用 MySQL Workbench 的 Data Export，或 `mysqldump` 创建完整备份。示例只使用占位符，并让客户端提示密码：

```powershell
mysqldump --host=<数据库主机> --port=<端口> --user=<用户名> --password `
  --single-transaction --routines --triggers --events `
  <数据库名> > <用户认可的绝对备份路径>
```

备份完成后检查文件存在、大小非零，并记录备份时间和恢复命令。未获得用户对备份位置的认可，或备份失败时，不得继续。

全新空库虽然没有旧业务数据，也应先确认选中的 schema 名称无误，避免把脚本执行到其他数据库。

## 第四步：只执行对应脚本

全新数据库：

```powershell
mysql --host=<数据库主机> --port=<端口> --user=<用户名> --password <数据库名> `
  --execute="source sql/schema.sql"
```

完整旧数据库：

```powershell
mysql --host=<数据库主机> --port=<端口> --user=<用户名> --password <数据库名> `
  --execute="source sql/upgrade-from-legacy.sql"
```

不要在同一个数据库上执行另一份脚本。脚本故意不使用 `IF NOT EXISTS` 掩盖结构冲突；任何“对象已存在”、唯一索引冲突、外键不匹配或列不兼容错误都意味着必须停止检查，不能继续拼凑执行。

升级脚本会把旧 `user.password` 扩容到 `VARCHAR(100)`，但不会读取或转换任何密码值。旧库中的密码如果仍是明文，必须通过受控的密码重置或离线迁移流程转换为 BCrypt 哈希后才能使用新登录接口。不要在 SQL 文件、聊天或日志中暴露原密码，也不要对已经是 BCrypt 的值重复编码。

## 第五步：执行后只读验收

核对七张业务表：

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'user_travel_preference', 'knowledge_document', 'knowledge_chunk',
    'planning_task', 'travel_plan', 'travel_plan_version', 'planning_trace'
  )
ORDER BY table_name;
```

核对列，包括用户角色列：

```sql
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (table_name = 'user' OR table_name IN (
    'user_travel_preference', 'knowledge_document', 'knowledge_chunk',
    'planning_task', 'travel_plan', 'travel_plan_version', 'planning_trace'
  ))
ORDER BY table_name, ordinal_position;
```

核对索引：

```sql
SELECT table_name, index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN (
    'user', 'user_travel_preference', 'knowledge_document', 'knowledge_chunk',
    'planning_task', 'travel_plan', 'travel_plan_version', 'planning_trace'
  )
ORDER BY table_name, index_name, seq_in_index;
```

核对外键：

```sql
SELECT table_name, constraint_name, column_name,
       referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND referenced_table_name IS NOT NULL
  AND table_name IN (
    'user_travel_preference', 'knowledge_document', 'knowledge_chunk',
    'planning_task', 'travel_plan', 'travel_plan_version', 'planning_trace'
  )
ORDER BY table_name, constraint_name, ordinal_position;
```

验收必须确认：七张表全部存在；`user.role` 为 `VARCHAR(16) NOT NULL DEFAULT 'USER'`；偏好表以 `user_id` 同时作为主键和外键；用户、城市、状态、方案、任务及创建时间的关键索引存在；所有预期外键都指向正确的表和列。

用户名索引还必须为唯一索引：

```sql
SELECT index_name, non_unique, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'user'
  AND index_name = 'uk_user_username'
ORDER BY seq_in_index;
```

预期恰好返回 `username` 一行，且 `non_unique = 0`。全新安装与旧库升级的最终用户名约束必须一致。
