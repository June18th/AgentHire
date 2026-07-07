# JobClaw 工程状态说明

> 最后更新：2026-07-07

本文记录当前仓库的工程状态，避免后续开发时继续按旧 `app/` 单体结构或全量 Docker 中间件来操作。

## 当前结构

- `backend/` 是 Spring Boot 主应用模块，负责组装 `core`、`channels`、`providers`、`plugins`、`agents`，并保留 Web/Admin/职位数据/用户/支付/MCP Server 等应用域。
- `core/` 是共享 Agent Runtime，包含 Channel/Agent 抽象、消息总线、路由、意图识别、模型解析、记忆、任务和工具能力。
- `channels/`、`providers/`、`plugins/`、`agents/` 是可替换扩展模块。
- `ui-react/` 是 Next.js 前端。默认通过 dev server 或 `jobclaw-web` 镜像提供页面。
- 旧 `app/` 已被 `backend/` 替代。文档中若仍出现旧 `app/` 作为后端主模块，应视为历史内容。

## 运行入口

后端本地开发：

```bash
./mvnw spring-boot:run -pl backend
```

前端本地开发：

```bash
cd ui-react
pnpm dev
```

默认地址：

```text
后端 API: http://localhost:8087
前端页面: http://localhost:8088
```

## Docker 默认策略

本地 Docker 默认保持轻量，只启动：

- `mysql`
- `jobclaw`
- `jobclaw-web`
- `jobclaw-gateway`

默认命令：

```bash
docker compose -f docker/compose/compose.dev.yml -f docker/compose/compose.mysql.yml -f docker/compose/compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
```

不要默认叠加：

- `docker/compose/compose.elasticsearch.yml`
- `docker/compose/compose.redis.yml`
- `docker/compose/compose.kafka.yml`
- `docker/compose/compose.minio.yml`

这些中间件只在明确需要搜索、缓存、消息队列或对象存储能力时按需启动。

## 文档收口项

- `readme.md`、`docs/getting-started.md`、`docs/docker.md`、`docs/frontend-backend-split.md` 是当前推荐入口。
- `docs/plan.md` 已同步到当前 V2 模块结构，但仍是研发计划文档，不替代启动手册。
- `docs/01-*`、`docs/02-*`、`docs/05-*` 等语雀导出长文可能保留历史章节；执行命令前优先参考当前入口文档。

## 后续建议

- 持续清理长文档中的旧 `app/` 引用。
- 为 Docker 可选中间件补独立启停小节。
- 在完成旧 `app/` 到 `backend/` 的迁移提交后，再做一次全仓文档链接校验。
