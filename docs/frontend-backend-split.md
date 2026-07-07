# JobClaw 前后端分离部署

这是当前推荐的开发和 Docker 部署方式。

## 为什么分离

旧流程会把 Next.js 静态导出复制到：

```text
backend/src/main/resources/static/
```

这会导致每次前端页面变更都需要重新打 Spring Boot jar 和后端镜像。

在分离部署中，`backend/src/main/resources/static/` 在 API 镜像里为空是预期行为。后端只提供 API、Actuator 和上传图片等后端资源；前端静态产物进入 `jobclaw-web` 镜像，由 Nginx 提供访问。

服务职责如下：

```text
jobclaw-web      Nginx 提供的 Next.js 静态前端
jobclaw          Spring Boot API 服务
jobclaw-gateway  Nginx 统一浏览器入口
jobclaw-mysql    MySQL
```

默认本地只启动 `mysql`、`jobclaw`、`jobclaw-web`、`jobclaw-gateway`。Redis、Kafka、Elasticsearch、MinIO 是可选中间件，按需叠加。

浏览器统一入口：

```text
http://localhost:8088/
```

网关内部路由：

```text
/             -> jobclaw-web
/api/**       -> jobclaw:8087
/actuator/**  -> jobclaw:8087
/oc/img/**    -> jobclaw:8087
```

分离部署里的 API 容器使用 `prod` profile。默认只需要 MySQL 内部地址：

```text
DATABASE_HOST=mysql
DATABASE_PORT=3306
```

`docker-compose.frontend.yml` 为 API 服务提供这些默认值，MySQL 由 `docker-compose.mysql.yml` 启动。默认关闭 Redis、Kafka、Elasticsearch、MinIO。

## 启动

```powershell
.\build\docker-split.ps1
```

等价命令：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
```

## 只重建变化服务

只修改后端 API 或前端静态代码时，只重建并重启受影响的应用服务：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml build jobclaw jobclaw-web
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d --no-deps jobclaw jobclaw-web
```

后端变更只构建 `jobclaw`，前端变更只构建 `jobclaw-web`。数据库迁移文件随后端镜像发布，通常不需要重建数据库容器。

基础设施容器已经运行且只改应用层时，也可使用较短命令：

```powershell
docker compose -f docker-compose.frontend.yml build jobclaw jobclaw-web
docker compose -f docker-compose.frontend.yml up -d --no-deps jobclaw jobclaw-web
```

这会保持 MySQL 等基础设施不变。

## AI 对话页发布说明

AI 对话页是前端路由：

```text
ui-react/app/chat/page.tsx
```

助手回复通过以下组件渲染：

```text
ui-react/components/chat/MarkdownMessage.tsx
```

渲染器支持模型回复常用的 Markdown 块：标题、段落、粗体、行内代码、代码块、引用、有序/无序列表、表格和链接。它不会渲染原始 HTML，链接协议限制为 `http`、`https` 和 `mailto`。

聊天滚动限制在消息面板内部。只有当用户已经接近底部时，新消息才会把面板保持在底部，避免点击页面或收到消息时把整个浏览器视口向下拉。

仅修改聊天 UI 时，重建前端镜像并重启前端容器：

```powershell
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml build jobclaw-web
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d jobclaw-web jobclaw-gateway
```

如果修改 Agent runtime、模型供应商或 `/api/chat/**` 后端接口，则重建 `jobclaw`。

## 访问地址

```text
前端入口:      http://localhost:8088/
后端 API:      http://localhost:8087/
MinIO 控制台:  http://localhost:9001/
```

默认轻量模式不会启动 MinIO；只有叠加 `docker-compose.minio.yml` 后才会有 MinIO console。

浏览器通常访问 `http://localhost:8088/`。网关会把 API 请求转发到后端。

如果 `8088` 已被占用，可在启动前覆盖网关端口：

```powershell
$env:JOBCLAW_GATEWAY_PORT = "18088"
.\build\docker-split.ps1
```

## 本地前端开发

开发 UI 时使用热更新：

```powershell
cd ui-react
pnpm dev
```

然后访问：

```text
http://localhost:8088/
```

开发模式下，前端从 `.env.development` 读取 `NEXT_PUBLIC_API_BASE_URL`，默认值为：

```text
http://localhost:8087
```

如果 Docker 网关已经占用 `8088`，可先停止网关，或临时使用备用端口：

```powershell
pnpm run dev:3000
```

生产/静态模式下，前端使用相对 `/api/...` 路径，由网关代理到后端。

## Legacy 静态资源打进 Spring Boot

旧的复制到 Spring Boot 流程仅作为兼容 fallback：

```powershell
cd ui-react
pnpm run deploy:legacy-spring-static
```

不要作为主工作流使用。只有在明确希望 Spring Boot jar 内包含前端静态导出时才使用。
