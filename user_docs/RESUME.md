# JobClaw 简历素材

> 这是个人使用的简历草稿与项目经历素材，区别于项目官方文档 `docs/`。

## 正式版项目经历

### 项目名称

求职派（JobClaw）多 Agent 求职系统

### 项目简介

基于 Spring AI 2.0 的多 Agent 求职平台，支持微信/钉钉/飞书多渠道接入，通过意图识别、Agent 路由、用户画像和上下文记忆，为用户提供身份采集、岗位抓取、岗位推荐和求职问答服务。

### 技术栈

Java 21、Spring Boot 4.0、Spring AI 2.0、LangGraph4J、Reactor、YAML、H2/MySQL

### 核心职责

- 基于 Spring AI 的 Prompt 和 ChatClient 抽象，封装 LLM 调用器（轻量/业务/用户偏好），统一处理模型选择、流式响应、会话记忆持久化和工具传参。
- 将 9 个提示词模板外置为 Markdown 资源文件，覆盖身份提取、灵魂画像、岗位抽取、会话摘要等场景，提示词与 Java 代码解耦，支持多 Agent 独立迭代，产品侧改模板不需要动业务代码。
- 实现智能上下文窗口管理，通过低效消息过滤、中英文 token 估算将多轮对话历史控制在预算内；支持超长对话触发异步摘要压缩，每 10 轮刷新一次，旧消息压缩为 100 字以内的摘要并归档。
- 设计用户身份注入机制，按系统规则、性格画像、基础信息、求职偏好四层维护用户档案，自动从对话中增量提取并拼接到系统提示词，同一个推荐 Agent 面对不同用户给出差异化推荐结果。
- 基于自定义 ReAct 替换 Spring AI 内置的自动工具执行，注册文件系统、Shell、MCP、网页抓取等十余种工具，并在推理-行动中插入中间件 Hook 钩子，支持 Function Calling 的日志记录和行为审计。

## 补充素材

### 项目名称

JobClaw - AI 驱动的求职信息采集与推荐系统

### 一句话介绍

基于 Spring Boot、React/Next.js 和多 Agent 能力实现的求职信息采集、清洗、管理与推荐系统，支持 H2/MySQL 数据库切换、Docker 一键部署、MinIO 对象存储和多模型 AI 配置。

### 技术栈

- 后端：Java 21、Spring Boot、Spring MVC、Spring Data JPA、Hibernate
- 数据库：H2、MySQL、Liquibase
- 前端：React 19、Next.js 15、TailwindCSS、shadcn/ui
- AI 能力：Spring AI、OpenAI 风格模型适配、智谱、DeepSeek、硅基流动等模型配置
- 工程化：Maven 多模块、Docker、Docker Compose、多阶段构建
- 存储：本地文件存储、MinIO 对象存储
- 任务调度：JobRunr

### 项目职责写法

可以根据真实参与程度选择使用：

- 负责梳理项目 Docker 化部署流程，补充 Spring Boot 后端镜像构建、H2 本地模式、MySQL 联调模式和 MinIO 对象存储模式。
- 优化 Maven 打包流程，配置 Spring Boot Maven Plugin，使应用能够生成可执行 jar 并在容器中稳定启动。
- 集成前端静态构建流程，在 Docker 多阶段构建中自动完成 Next.js 静态导出，并在前后端分离部署中由 Nginx Web 镜像托管静态资源。
- 设计本地文件存储与 MinIO 对象存储的可切换方案，通过环境变量控制存储类型，降低本地开发和部署环境之间的差异。
- 调整 Docker 容器时区配置，统一应用容器、Java 进程、MySQL 服务和 MySQL 日志为中国时间。
- 补充 Docker Compose 健康检查和依赖启动顺序，提升服务启动稳定性。

### 简历项目描述模板

```text
JobClaw 是一个面向求职场景的 AI 信息采集与推荐系统，支持岗位信息采集、数据清洗、草稿管理、用户管理、AI 模型配置和任务调度等能力。项目采用 Spring Boot + JPA/Hibernate 构建后端服务，React 19 + Next.js 15 构建前端页面，并通过 Docker Compose 支持 H2、MySQL、MinIO 等多种运行模式。
```

### 简历亮点模板

```text
1. 参与项目 Docker 化改造，设计 H2 本地快速体验、MySQL 生产化联调、MySQL + MinIO 完整部署三种运行方式，并补充健康检查与启动依赖控制。
2. 优化 Spring Boot 打包流程，绑定 spring-boot-maven-plugin 的 repackage 生命周期，解决普通 jar 无法直接运行的问题。
3. 集成 Next.js 静态导出到 Docker 多阶段构建中，实现前端 Web 镜像与后端 API 镜像拆分；legacy 一体镜像仍可按需复制静态资源并打包后端 jar。
4. 扩展图片存储能力，支持本地目录与 MinIO 对象存储切换，并保持业务访问路径 `/oc/img/...` 不变。
5. 统一容器运行时区，保证应用日志、Java 时间、MySQL 会话时间和 MySQL 日志均使用 Asia/Shanghai。
```

## 面试表达

如果面试官问“这个项目是微服务吗”：

```text
不是严格意义上的微服务。它更接近模块化单体架构：代码上通过 Maven 多模块拆分 core、backend、channels、providers、plugins、agents 等模块；部署上可以拆成 Spring Boot API、前端 Web、MySQL、MinIO 等容器，但业务后端仍是一个主应用。MySQL 和 MinIO 是基础设施服务，不属于业务微服务。
```

如果面试官问“前端是动态还是静态”：

```text
前端部署形态是静态的。Next.js 使用静态导出生成 HTML、JS 和 CSS 文件，V2 推荐由独立的 Nginx Web 镜像托管；只有 legacy 一体 jar 才会复制到 Spring Boot 的 static 目录。业务数据仍然是动态的，React 页面加载后会请求 Spring Boot API 获取用户、岗位、字典、任务等数据。
```

如果面试官问“H2 和 MySQL 的区别”：

```text
H2 用于 dev 环境，作为本地文件数据库，适合快速启动和演示；MySQL 用于 prod 环境，更接近真实部署，配合 Liquibase 管理表结构和初始化数据，适合长期运行、备份和运维。
```

## 可继续补充

- 你实际负责的功能模块
- 解决过的具体 bug
- 压测或启动耗时优化数据
- 数据库表设计理解
- AI Agent 流程理解
- 前端页面截图和功能说明

## 生产级平台改造素材

- 参与项目生产级基础设施改造，引入 Redis 作为分布式缓存与共享状态组件，用于在线人数计数等跨实例状态管理，并设计 Redis 异常时的本地内存降级策略。
- 引入 Kafka 作为平台事件总线，沉淀统一消息发布入口，为 Agent 执行事件、LLM 调用审计、岗位抓取事件和通知投递等异步场景提供扩展基础。
- 基于 Docker Compose 拆分 MySQL、Redis、Kafka、MinIO、后端 API、前端 Web 与 Nginx Gateway 服务，形成更接近生产环境的前后端分离部署结构。
- 设计基础设施可选开关，通过环境变量控制 Redis、Kafka、MinIO 等组件启用状态，兼顾本地开发轻量运行与生产级部署。
## 岗位搜索与 Elasticsearch 素材

- 引入 Elasticsearch 作为岗位全文检索引擎，围绕公司名称、岗位名称、工作地点、行业、招聘类型、公告内容和备注等字段建立搜索索引，提升岗位关键词检索能力。
- 设计 MySQL + Elasticsearch 双写与降级方案：MySQL 作为权威数据源，岗位发布、编辑、状态变更时同步更新 ES；查询链路优先使用 ES，异常时自动回退 MySQL，保证核心列表功能可用。
- 补充管理端重建索引接口，支持将 MySQL 中已发布岗位批量同步到 ES，便于初始化、故障恢复和搜索索引重建。
- 基于 Docker Compose 增加 Elasticsearch 独立服务、健康检查、数据卷和后端依赖启动顺序，并通过环境变量控制 ES 是否启用、连接地址、索引名称和超时时间。

简历表述可写为：

```text
负责岗位搜索能力升级，引入 Elasticsearch 构建岗位全文检索索引，支持公司、岗位、地点、行业、公告内容等多字段关键词搜索；设计 MySQL 权威数据源 + ES 搜索索引的同步和降级机制，在岗位发布、编辑、状态变更时维护索引，并提供管理端重建索引能力，提升系统搜索性能与生产可恢复性。
```
## 生产级部署素材

- 设计并落地单服务器生产部署基线，基于 Docker Compose 编排 Nginx Gateway、Spring Boot API、Next.js 静态 Web、MySQL、Redis、Kafka、Elasticsearch 和 MinIO，形成可真实运行的前后端分离部署结构。
- 将开发联调配置与生产配置拆分，生产模式仅暴露统一网关入口，数据库、缓存、消息队列、搜索引擎和对象存储均限制在 Docker 内网访问，降低基础设施端口暴露风险。
- 补充 `.env.production` 生产环境变量模板和启动脚本校验，要求替换数据库密码、Redis 密码、MinIO 密钥和 JWT 密钥，避免示例密钥进入生产环境。
- 为核心服务补充健康检查、失败自动重启、Docker 命名卷持久化和日志滚动策略，提升单机部署下的稳定性、可恢复性和运维可观察性。

简历表述可写为：

```text
负责项目生产级部署改造，基于 Docker Compose 编排 Nginx Gateway、Spring Boot API、Next.js Web、MySQL、Redis、Kafka、Elasticsearch 与 MinIO，拆分开发联调与生产运行配置；生产环境仅暴露统一网关入口，内部基础设施通过 Docker 网络访问，并补充环境变量密钥模板、健康检查、自动重启、日志滚动和数据卷持久化，提升系统上线可用性与运维安全性。
```
