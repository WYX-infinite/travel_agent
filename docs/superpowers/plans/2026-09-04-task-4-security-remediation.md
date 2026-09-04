# 任务四安全修复实施计划

> 本计划是任务四的审阅修复附录，只执行已批准的安全收口，不开始任务五。

## 任务 1：先补失败测试

**修改/新增文件：**
- `src/test/java/com/wyx/travelagent/auth/JwtTokenServiceTest.java`
- `src/test/java/com/wyx/travelagent/auth/JwtAuthenticationInterceptorTest.java`
- `src/test/java/com/wyx/travelagent/auth/CaptchaServiceTest.java`
- `src/test/java/com/wyx/travelagent/auth/AuthControllerTest.java`
- `src/test/java/com/wyx/travelagent/auth/LegacyEndpointRetirementTest.java`

1. 断言 64 字节密钥签发的 JWT 仍为 HS256，并拒绝 HS512 Token。
2. 断言带 context path 或矩阵参数的管理员路径仍要求 ADMIN，并断言异步处理开始时清理上下文。
3. 断言验证码同一 ID 在错误答案后也已失效。
4. 断言验证码响应带 `Cache-Control: no-store`。
5. 断言旧根路径控制器映射未注册。
6. 运行任务四定向测试，确认新增断言在实现前失败。

## 任务 2：实施最小安全修复

**修改/删除文件：**
- `src/main/java/com/wyx/travelagent/auth/JwtTokenService.java`
- `src/main/java/com/wyx/travelagent/auth/JwtAuthenticationInterceptor.java`
- `src/main/java/com/wyx/travelagent/auth/CaptchaService.java`
- `src/main/java/com/wyx/travelagent/auth/AuthController.java`
- 删除 `src/main/java/com/wyx/travelagent/controller/UserController.java`
- 删除 `src/main/java/com/wyx/travelagent/controller/AiRecommendationsController.java`

1. 显式固定签发和解析算法为 HS256。
2. 规范化应用内路径并补齐异步 ThreadLocal 清理。
3. 把验证码 Lua 改为原子读取、删除、比较。
4. 给验证码响应设置禁止缓存头，登录查询移除 `LIMIT 1`。
5. 删除遗留根路径控制器，使旧接口不再注册。
6. 重跑定向测试直至通过。

## 任务 3：统一数据库用户名约束

**修改文件：**
- `sql/upgrade-from-legacy.sql`
- `docs/database-setup.md`

1. 升级脚本使用 `uk_user_username` 唯一索引，与全新 schema 一致。
2. 文档增加升级前重复用户名检查和升级后唯一索引验收。
3. 对目标库只读检查重复用户名；若存在则停止。
4. 在既有迁移前备份保护下，把已升级数据库的非唯一索引替换为唯一索引，再只读验收。

## 任务 4：完整验证与提交

1. 使用 JDK 21 运行任务四定向测试和完整 Maven 测试。
2. 静态检查禁止 SQL、敏感配置、临时文件、包名和 Git 范围。
3. 使用正确 Redis ACL 凭据启动应用，确认健康状态为 UP、验证码 200、未授权 `/api/auth/me` 为 401，然后停止进程。
4. 邀请代码审阅复查全部修复，解决 Critical/Important 问题。
5. 只暂存任务四文件，执行 staged diff 检查，按原计划提交：`feat: add schema and jwt authentication`。
