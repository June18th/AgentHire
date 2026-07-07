# JobClaw 首次启动指南

> 本文面向 JobClaw V2，用于从零完成本地配置、启动、前端开发和常见问题排查。
>
> 最后更新：2026-07-07

## 1. 环境准备

### 1.1 安装 Java 21

项目要求 Java 21。先确认本机 Java 和 Maven wrapper 使用的 JDK 都指向 21：

```bash
java -version
echo $JAVA_HOME
```

macOS 用户如果使用 `jenv`，可按下面方式注册并启用 Java 21：

```bash
brew install openjdk@21
jenv add /opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home
jenv global 21
jenv enable-plugin export
eval "$(jenv init -)"
echo $JAVA_HOME
```

### 1.2 准备环境变量

从示例文件复制一份本地配置：

```bash
cp .env.example .env
```

大模型供应商 API Key 不再通过 `.env` 或 `application.yml` 覆盖。启动后进入后台的「LLM供应商」页面，编辑或新增供应商并保存 API Key、Base URL、API Style 和模型列表。

### 1.3 准备本地数据库

建议复制一份初始 H2 数据库，避免污染种子数据：

```bash
cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db
```

然后在 `.env` 中设置：

```bash
JOBCLAW_DATABASE_NAME=jobclaw-my
```

### 1.4 配置 MCP 客户端

默认配置面向 Windows：

```bash
MCP_SERVERS_CONFIG=classpath:mcp-servers.json
```

macOS/Linux 用户可在 `.env` 中改为：

```bash
MCP_SERVERS_CONFIG=classpath:mcp-servers-mac.json
```

## 2. 后端启动

当前后端主模块是 `backend/`。旧 `app/` 已迁移并删除，所有后端启动、打包、测试命令都应以 `backend` 为主应用模块。

首次运行或代码变更后，先构建：

```bash
./mvnw install -DskipTests
```

启动 Spring Boot 后端：

```bash
./mvnw spring-boot:run -pl backend
```

默认访问地址：

```text
http://localhost:8087
```

如果 macOS 报 `不支持发行版本 21`，通常是 `JAVA_HOME` 没指向 Java 21。可临时指定：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run -pl backend
```

## 3. 前端启动

前端位于 `ui-react/`，基于 Next.js 15、React 19、TailwindCSS 和 shadcn/ui。

```bash
cd ui-react
pnpm install
pnpm dev
```

本地开发默认访问：

```text
http://localhost:8088
```

开发时优先使用前端 dev server 热更新，不需要把静态产物复制到后端。

## 4. 前后端部署方式

V2 推荐使用前后端分离部署：

- `backend` 作为 API 镜像，只提供后端接口、Actuator、上传图片等后端资源。
- `ui-react` 构建出的静态前端进入 `jobclaw-web` 镜像，由 Nginx 提供访问。
- `backend/src/main/resources/static/` 在分离部署的 API 镜像中为空是预期行为。

详细说明见：

```text
docs/frontend-backend-split.md
docs/docker.md
```

只有在明确需要 legacy 一体 jar 时，才把前端静态导出复制到 Spring Boot 的 `static/` 目录：

```bash
cd ui-react
pnpm run deploy:legacy-spring-static
```

## 4.1 Docker 轻量启动

本地 Docker 默认只启动 MySQL、后端 API、前端静态服务和统一网关：

```bash
docker compose -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
```

也可运行脚本：

```powershell
.\build\docker-split.ps1
```

Redis、Kafka、Elasticsearch、MinIO 都是可选中间件，默认不启动。只有在任务明确需要缓存、消息队列、搜索或对象存储时，再叠加对应 compose 文件。

## 5. 首次登录

本地后端直连时访问 `http://localhost:8087`。前后端分离或 Docker 网关模式下，优先访问 `http://localhost:8088`。

可使用游客登录快速体验，也可使用管理员账号进入完整后台。

管理员后台常用入口：

- LLM供应商：配置模型供应商、API Key 和模型列表。
- 用户配置：配置用户侧模型偏好。
- 渠道配置：添加 WeChat、DingDing、FeiShu 等 IM 入口。
- 职位与草稿：管理职位数据和采集流程。

## 6. IM 渠道配置

JobClaw V2 通过 IM 渠道接收用户消息，再路由到共享 Agent Runtime。

支持的渠道包括：

- WeChat ClawBot：适合快速体验，配置简单。
- DingDing：支持更丰富的企业 IM 场景。
- FeiShu：支持现代 IM 交互和文件能力。

配置完成后，用户可以在 IM 中使用以下系统命令：

```text
/help           查看帮助
/plan           进入计划模式
/agents         查看可用 Agent
/current        查看当前会话绑定的 Agent
/agent <id>     切换 Agent
/reset          重置当前会话
```

## 7. 常见问题

### Q1: Maven 测试因为临时目录权限失败怎么办？

仓库已通过 `.mvn/maven.config` 和 Surefire 配置把 Maven 本地仓库、Java 临时目录收敛到项目目录内：

```text
workspace/.m2/repository
build/tmp
```

直接执行：

```bash
./mvnw test
```

### Q2: 为什么不再使用 `app/`？

V2 已将原主应用模块迁移到 `backend/`。根 POM 现在使用 `backend` 作为 Spring Boot 主应用模块，旧 `app/` 删除是迁移的一部分。

### Q3: 为什么 `backend/src/main/resources/static/` 为空？

这是分离部署下的预期状态。前端静态资源由 `jobclaw-web` 镜像中的 Nginx 提供；后端 jar 不再默认携带前端静态导出。

### Q4: 如何重新生成本地数据库副本？

关闭后端后，删除自己的 H2 副本，再从种子库复制：

```bash
cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db
```

确认 `.env` 仍指向：

```bash
JOBCLAW_DATABASE_NAME=jobclaw-my
```

### Q5: 如何查看日志？

默认日志目录：

```text
logs/
```

常见文件：

- `logs/oc.log`
- `logs/req-oc.log`
- `logs/arch/`

## 8. 验证命令

后端全量测试：

```bash
./mvnw test
```

后端打包：

```bash
./mvnw clean package -DskipTests
```

前端构建：

```bash
cd ui-react
pnpm build
```
