# JobClaw 项目理解笔记

技术栈

    Java 21
    Spring Boot 4.0.5
    Spring AI 2.0.0-M4
    Spring Modulith
    LangGraph4J
    JPA / Hibernate
    H2 / MySQL
    React 19 / Next.js 15 / TailwindCSS / shadcn/ui

JobClaw/
├── backend/                 # Spring Boot 主应用，组装所有模块
├── core/                    # Agent Runtime 核心抽象、路由、事件、模型、记忆
├── channels/                # 微信、钉钉、飞书等消息入口
├── providers/               # 大模型 Provider 接入
├── plugins/                 # 工具插件
├── agents/                  # 业务 Agent
├── ui-react/                # Next.js 前端
└── docs/                    # 项目文档与语雀导出资料

## H2 初始化数据库

默认 `dev` 环境使用 H2 数据库。为了避免污染项目自带的种子数据，建议复制一份本地开发库：

```bash
cp .env.example .env
cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db
```

然后把 `.env` 中的数据库名改为：

```env
JOBCLAW_DATABASE_NAME=jobclaw-my
```

这样本地开发时实际使用的是：

```text
workspace/datas/jobclaw-my.mv.db
```

原始种子库仍然保留为：

```text
workspace/datas/jobclaw.mv.db
```

## H2 是什么

H2 是一个轻量级 Java 数据库，可以直接以内嵌方式运行在 Spring Boot 应用里，不需要单独启动数据库服务。

在本项目中，H2 主要用于本地快速体验和开发调试。它的数据保存为 `.mv.db` 文件，比如：

```text
workspace/datas/jobclaw.mv.db
workspace/datas/jobclaw-my.mv.db
```

所以使用 H2 时，项目启动成本比较低：不需要安装 MySQL，也不需要额外启动数据库容器。

## H2 和 MySQL 在本项目中的区别

H2 是本地开发数据库，MySQL 是更接近生产环境的数据库。

本项目的 `dev` 环境使用 H2：

```text
jdbc:h2:${AGENT_WORKSPACE}datas/${jobclaw.database.name}
```

特点是：

- 适合本地快速启动和功能体验。
- 数据保存在项目目录下的 `.mv.db` 文件中。
- 默认使用 JPA / Hibernate 的 `ddl-auto: update` 自动更新表结构。
- 可以通过 `/h2-console` 查看数据库。
- 不适合作为正式生产数据库。

本项目的 `prod` 环境使用 MySQL：

```text
jdbc:mysql://${DATABASE_HOST}:${DATABASE_PORT}/${jobclaw.database.name}
```

特点是：

- 更接近真实部署环境。
- 数据保存在 MySQL 容器的数据卷中。
- 表结构和初始化数据通过 Liquibase 迁移脚本管理。
- 支持更完整的并发、事务、权限、备份和运维能力。
- 更适合长期运行和正式部署。

简单理解：

```text
H2    = 本地开发 / 快速体验 / 文件数据库
MySQL = 联调环境 / 生产环境 / 独立数据库服务
```

在 Docker 中，如果只是想快速体验项目，可以使用 H2 模式：

```bash
docker compose up --build -d
```

如果想更接近正式部署，可以使用 MySQL 模式：

```bash
docker compose -f docker-compose.mysql.yml up --build -d
```

IM 渠道层
-> 消息网关适配器
-> 对话管理 Agent
-> 业务 Agent
-> 工具调用 Agent / 模型路由 Agent / 任务调度 Agent
-> 消息推送

核心思路：

    channels/：把微信、钉钉、飞书等入口统一成 ChannelReceiveMessage。
    core/：提供 Agent/Channel 抽象、事件总线、对话管理、路由、会话绑定、意图识别、模型选择、记忆和任务能力。
    agents/：承载可插拔业务 Agent，例如身份采集、岗位抓取、岗位推荐。
    providers/：隔离智谱、OpenAI 兼容、阿里、Anthropic 等模型接入。
    plugins/：提供 Playwright、职位库检索等工具能力。
    backend/：组装所有模块，并保留 Web 管理、职位库、草稿、用户、支付、MCP Server 等应用域。


IM 系统命令

    /help：查看帮助
    /plan：进入计划模式
    /agents：查看可用 Agent
    /current：查看当前会话绑定的 Agent
    /agent <agentId>：切换 Agent
    /reset：重置会话
## 生产级平台基础设施理解

当前项目开始从“本地可运行应用”向“生产级平台”演进。基础设施层建议采用：

- MySQL：生产数据库，配合 Liquibase 管理表结构和初始化数据。
- Redis：分布式缓存与共享状态，适合在线人数、限流、验证码状态、热点数据、任务状态等场景。
- Kafka：消息队列/事件总线，适合 Agent 执行事件、LLM 审计事件、岗位抓取事件、通知事件和后续数据分析。
- MinIO：对象存储，适合图片、附件、简历文件等非结构化数据。
- Nginx Gateway：统一浏览器入口，转发前端页面和后端 API。

Redis 与 Kafka 的定位不同：

```text
Redis = 快速共享状态 / 缓存 / 分布式计数 / 限流
Kafka = 异步事件流 / 消息解耦 / 审计日志 / 后续数据分析
```

本项目消息队列优先选择 Kafka。原因是 JobClaw 更像事件驱动平台：岗位抓取、Agent 推理轨迹、LLM 调用审计、用户行为、通知投递都可以抽象成事件流。Kafka 对事件保留、消费组、多消费者订阅、后续分析扩展更友好。

RocketMQ 也可以用于生产环境，尤其适合订单、交易、事务消息、电商金融等强业务消息场景。但 JobClaw 当前不是强事务订单系统，所以 Kafka 更贴合平台演进方向。

当前已落地：

- Docker 增加 Redis 服务。
- Docker 增加 Kafka 服务。
- Spring Boot 增加 Redis 与 Kafka 配置。
- 在线人数计数优先使用 Redis，Redis 不启用或异常时回退本地内存。
- 增加统一 MQ 发布器，后续业务事件可以统一接入 Kafka。

需要注意：`SseEmitter` 这类 JVM 内长连接对象不能直接放到 Redis。真正多实例部署时，需要配合粘性会话、消息转发或连接管理层继续演进。
## 岗位搜索 + Elasticsearch

本次新增的岗位搜索能力，是把原来主要依赖 MySQL 条件查询的岗位列表，扩展为 Elasticsearch 全文检索。

核心思路：

- MySQL 仍然作为岗位主数据存储，负责事务、增删改和最终一致的数据来源。
- Elasticsearch 作为搜索索引，负责关键词检索、多字段匹配和后续搜索体验优化。
- 后端查询岗位时优先走 Elasticsearch；如果 ES 未启用或调用异常，会自动回退到 MySQL 查询，保证基础功能可用。
- 岗位发布、编辑、状态变更时，会同步更新 ES 索引。
- 管理端提供重建索引接口，用于把 MySQL 中已发布岗位重新写入 ES。

相关配置：

```env
JOBCLAW_SEARCH_ES_ENABLED=true
ELASTICSEARCH_ENDPOINT=http://elasticsearch:9200
JOBCLAW_SEARCH_ES_INDEX=jobclaw_oc_job
JOBCLAW_SEARCH_ES_CONNECT_TIMEOUT_MS=2000
JOBCLAW_SEARCH_ES_SOCKET_TIMEOUT_MS=5000
```

本地 Docker 生产级联调时，Elasticsearch 通过独立 compose 文件启动：

```bash
docker compose -f docker-compose.mysql.yml -f docker-compose.redis.yml -f docker-compose.kafka.yml -f docker-compose.elasticsearch.yml -f docker-compose.minio.yml -f docker-compose.frontend.yml up -d
```

搜索入口：

```text
GET http://localhost:8088/api/oc/search?keyword=java&page=1&size=5
```

管理端重建索引入口：

```text
POST http://localhost:8088/api/admin/oc/reindex
```

可以这样理解：

```text
MySQL = 权威数据源，负责保存岗位真实数据
Elasticsearch = 搜索引擎，负责让岗位检索更快、更灵活
```

当前 ES 索引名为 `jobclaw_oc_job`。如果数据库里没有已发布岗位，索引会存在，但文档数为 0，这是正常现象。
## 生产级部署理解

生产级部署不是开发阶段的“能跑起来”，也不是测试阶段的“功能可验证”。生产阶段表示系统具备面向真实用户持续运行的基本条件。

本项目当前采用单服务器生产基线：

```text
Nginx Gateway -> 前端 Web / 后端 API
后端 API -> MySQL / Redis / Kafka / Elasticsearch / MinIO
```

生产部署和开发部署的区别：

- 开发部署关注快速启动、调试方便，可以暴露数据库、Redis、Kafka、MinIO、ES 等端口。
- 生产部署只暴露统一网关入口，内部基础设施不直接暴露到公网。
- 生产部署必须使用 `.env.production` 管理真实密钥，不能使用示例密码。
- 数据必须放在 Docker volume 或外部云服务中，容器重建不能丢数据。
- 服务需要 healthcheck、restart 策略和日志滚动。
- 前后端采用分离式部署，前端由 Nginx 容器托管，后端只负责 API。

当前新增的生产入口：

```text
docker-compose.prod.yml
.env.production.example
build/docker-prod.ps1
docs/production-deployment.md
```

生产启动方式：

```powershell
Copy-Item .env.production.example .env.production
# 修改 .env.production 中所有 CHANGE_ME 配置
.\build\docker-prod.ps1
```

可以这样理解：

```text
dev  = 本地开发，方便调试
test = 测试验证，验证功能和缺陷
prod = 真实运行，面向用户，关注安全、数据、稳定性和可恢复
```

当前方案是单机生产基线，适合小规模真实上线。如果后续访问量变大，需要继续演进为多副本 API、独立数据库、对象存储云服务、Kafka/ES 集群、监控告警和自动化发布。

## 登录认证与权限控制理解

前端页面的登录守卫只负责用户体验，真正的安全边界必须放在后端接口。

本项目当前后端有两层权限：

- Spring Security：按接口路径做第一层拦截。
- `PermissionCheckInterceptor`：按 Controller 上的 `@Permission` 注解做第二层拦截。

可以这样理解：

```text
前端 AuthGuard = 页面体验，提示登录 / 无权限
后端 SecurityConfig = 接口路径级安全边界
后端 @Permission = 业务接口级权限边界
```

当前接口边界：

- `/api/common/**`、`/api/wx/**`：公共接口。
- `/api/admin/**`：管理员接口。
- `/api/user/**`、`/api/chat/**`、`/api/recharge/**`：登录用户接口。
- `/api/oc/list`：公开岗位列表。

角色体系同时兼容旧角色和新的 RBAC 角色：

- 旧角色 `ADMIN` 可以访问管理员接口。
- RBAC 角色 `PLATFORM_ADMIN` / `SUPER_ADMIN` 也可以访问管理员接口。
- 旧角色 `VIP` 可以访问会员内容。
- RBAC 角色 `VIP_USER` 也可以访问会员内容。
- 管理员默认可以访问会员内容。

接口返回语义：

```text
401 = 未登录
403 = 已登录但无权限
```

本次新增了权限拦截器测试：

```text
backend/src/test/java/com/git/hui/jobclaw/web/hook/interceptor/PermissionCheckInterceptorTest.java
```

覆盖场景：

- 未登录访问受保护接口返回 401。
- 普通用户访问管理员接口返回 403。
- RBAC 平台管理员可以通过管理员接口权限校验。
- RBAC VIP 用户可以通过 VIP 接口权限校验。
- 管理员可以访问 VIP 接口。

模型  初始化 问题  解决  