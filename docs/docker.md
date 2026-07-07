# JobClaw Docker 运行说明

本文说明当前项目的 Docker 运行方式。后端主模块为 `backend/`，前端为 `ui-react/`。

## 推荐模式：前后端分离

推荐使用前后端分离的 Docker 组合。默认本地启动保持轻量，只启动 MySQL、后端 API、前端静态服务和统一网关：

```bash
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
```

也可以直接运行脚本：

```powershell
.\build\docker-split.ps1
```

该模式包含：

- `jobclaw`：Spring Boot API 服务，使用 `backend/`
- `jobclaw-web`：Next.js 静态前端，由 Nginx 服务
- `jobclaw-gateway`：统一浏览器入口，转发 `/api/**`、`/actuator/**`、`/oc/img/**` 到后端
- `jobclaw-mysql`：默认数据库

可选基础设施按需启动：

- `jobclaw-redis`：缓存能力，需要 `docker-compose.redis.yml`
- `jobclaw-kafka`：消息队列能力，需要 `docker-compose.kafka.yml`
- `jobclaw-minio`：对象存储能力，需要 `docker-compose.minio.yml`
- `jobclaw-elasticsearch`：搜索能力，需要 `docker-compose.elasticsearch.yml`

访问入口：

```text
http://localhost:8088/
```

在该模式下，`backend/src/main/resources/static/` 为空是预期行为。API 镜像会清空该目录，前端静态产物会进入 `jobclaw-web` 镜像。

`docker-compose.frontend.yml` 中的 `jobclaw` 服务会显式配置 MySQL 连接环境变量，并默认关闭 Redis：

```env
DATABASE_HOST=mysql
DATABASE_PORT=3306
DATABASE_USERNAME=root
DATABASE_PASSWORD=${MYSQL_ROOT_PASSWORD:-jobclaw_root}
JOBCLAW_REDIS_ENABLED=false
```

因此默认只需要叠加 `docker-compose.mysql.yml`。如果显式启用 Redis，再叠加 `docker-compose.redis.yml` 并设置 `JOBCLAW_REDIS_ENABLED=true`。

### 只重建受影响服务

日常改动如果只涉及后端 `backend/` 和前端 `ui-react/`，不需要重建 MySQL、Redis、Kafka、MinIO、Elasticsearch 等基础设施。可以只构建并重启 API 与 Web 两个服务：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml build jobclaw jobclaw-web
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d --no-deps jobclaw jobclaw-web
```

如果只改后端，构建 `jobclaw`；如果只改前端，构建 `jobclaw-web`。数据库迁移脚本变更仍属于后端发布的一部分，但通常不需要重建数据库容器。

如果基础设施容器已经在同一个 Docker Compose 项目中运行，且只改了应用层，也可以只对 `docker-compose.frontend.yml` 执行构建和启动：

```powershell
docker compose -f docker-compose.frontend.yml build jobclaw jobclaw-web
docker compose -f docker-compose.frontend.yml up -d --no-deps jobclaw jobclaw-web
```

这种方式不会重建 MySQL、Redis、Kafka、MinIO、Elasticsearch 等基础设施。

### AI 对话页变更发布

AI 对话页位于 `ui-react/app/chat/page.tsx`，当前属于前端静态应用的一部分。只修改对话页 UI、滚动行为、Markdown 渲染、输入框交互等内容时，通常只需要重建并重启前端服务：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml build jobclaw-web
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d jobclaw-web jobclaw-gateway
```

如果修改了 Agent 调用、模型选择、ReAct 工具循环、接口返回等后端逻辑，则需要重建并重启 `jobclaw`：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml build jobclaw
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d jobclaw
```

飞书通道、模型供应商、Agent 路由、心跳上下文等都属于后端逻辑。例如修复飞书 OpenID 绑定、`im.message.receive_v1` 事件处理、`cardkit:card:write` 流式卡片权限、`im:message:send_as_bot` 文本回复权限，或把文本模型偏好从 `zhipu#glm-4.7-flash` 改为 `zhipu#glm-4.7` 后，都只需要重建并重启 `jobclaw`。

发布后可用下面的命令确认入口服务状态：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml ps jobclaw jobclaw-web jobclaw-gateway
```

## 本地快速体验：H2

适合本地体验和功能调试。应用使用 `dev` profile，默认 H2 数据库。

首次启动前建议复制一份数据库，避免污染初始数据：

```bash
cp .env.example .env
cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db
```

然后在 `.env` 中设置：

```env
JOBCLAW_DATABASE_NAME=jobclaw-my
```

启动：

```bash
docker compose up --build -d
```

访问：

- 应用首页：`http://localhost:8087`
- JobRunr Dashboard：`http://localhost:8099/dashboard`

停止：

```bash
docker compose down
```

## MySQL 模式

仅启动后端与 MySQL：

```bash
docker compose -f docker-compose.mysql.yml up --build -d
```

默认配置：

```env
MYSQL_DATABASE=jobclaw
MYSQL_ROOT_PASSWORD=jobclaw_root
```

停止但保留数据库：

```bash
docker compose -f docker-compose.mysql.yml down
```

停止并删除 MySQL 数据卷：

```bash
docker compose -f docker-compose.mysql.yml down -v
```

## MinIO 对象存储

默认图片存储仍然是本地文件：

```env
JOBCLAW_IMG_STORAGE_TYPE=local
JOBCLAW_IMG_ABS_TMP_PATH=./workspace/storage/
JOBCLAW_IMG_WEB_IMG_PATH=/oc/img/
JOBCLAW_IMG_CDN_HOST=http://localhost:8087
```

如需保存到 MinIO，叠加 `docker-compose.minio.yml`：

```bash
docker compose -f docker-compose.mysql.yml -f docker-compose.minio.yml up --build -d
```

默认 MinIO 配置：

```env
JOBCLAW_IMG_STORAGE_TYPE=minio
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=jobclaw
```

MinIO 控制台：

```text
http://localhost:9001
```

## 其他可选中间件

默认本地 Docker 不启动 Redis、Kafka、Elasticsearch。需要对应能力时再叠加：

```bash
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.redis.yml -f docker-compose.frontend.yml up -d --build mysql redis jobclaw jobclaw-web jobclaw-gateway
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.kafka.yml -f docker-compose.frontend.yml up -d --build mysql kafka jobclaw jobclaw-web jobclaw-gateway
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.elasticsearch.yml -f docker-compose.frontend.yml up -d --build mysql elasticsearch jobclaw jobclaw-web jobclaw-gateway
```

## 前端开发

本地开发 UI 时通常不需要复制静态产物到后端：

```bash
cd ui-react
pnpm install
pnpm dev
```

前端开发服务默认运行在：

```text
http://localhost:8088
```

环境文件：

- `.env.development`：本地开发，默认请求 `http://localhost:8087`
- `.env.test`：测试环境，默认请求 `http://localhost:18087`
- `.env.production`：静态部署，默认使用相对路径

## 兼容模式：静态产物打进 Spring Boot

根目录 `Dockerfile` 仍保留一体镜像流程：先构建 `ui-react`，再把 `.next-build` 复制到 `backend/src/main/resources/static/`，最后打包 Spring Boot jar。

非 Docker 场景下也可手动执行：

```bash
cd ui-react
pnpm run deploy:legacy-spring-static
```

该模式仅作为兼容 fallback。日常开发和生产化部署优先使用前后端分离模式。

## 健康检查

后端健康检查：

```text
http://localhost:8087/actuator/health
```

MySQL 模式会等待 MySQL healthcheck 通过后再启动后端应用。

## 时区

Docker 运行时统一使用中国时间：

- 应用容器设置 `TZ=Asia/Shanghai`
- Java 进程设置 `-Duser.timezone=Asia/Shanghai`
- MySQL 容器设置 `TZ=Asia/Shanghai`
- MySQL 服务设置 `--default-time-zone=+08:00`
