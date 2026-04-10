# ai-oc

## 后端工程

后端相关工程放置在 [app](app/) 目录下

### 技术栈

jdk17 + SpringBoot3.5.3 + H2/MySql + SpringJPA + SpringAI + LangGraph4J

### 启动

本地开发时，数据库指向h2，直接启动即可；如果需要体验大模型的数据抓取录入，需要修改启动参数

1. 到智谱清言申请账号，注册一个API Key
    - api申请地址: [智谱清言API Key](https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys)

2. 传入大模型ApiKey
   a. 命令行传参方式
    - 编辑启动命令
    - 点击 Modify options, 在菜单栏中，开启 `Program arguments`
    - 添加命令行参数 `--zhipuai-api-key=xxx`

   ![命令行配置.webp](docs/imgs/01-1.webp)

   b. 直接修改配置参数
    - 打开文件： [application-ai.yml](app/src/main/resources-env/dev/application-ai.yml)
    - 修改参数: `api-key:`

3. 入口类，直接启动

说明：

- dev 环境：使用h2数据库, 对应的数据库文件为：[app/src/main/resources/ai-oc.mv.db](app/src/main/resources/ai-oc.mv.db)
- test/prod 环境：使用MySql数据库

## 前端工程

前端相关工程放置在 [ui-react](ui-react/) 目录下

### 技术栈

react + next.js + tailwindcss

推荐nodejs=18.x, next.js=15+

### 启动

```bash
# 安装依赖
pnpm install

# 本地开发
pnpm dev

# 打包
pnpm build 

# 发布到SpringBoot的static目录下
pnpm run deploy
```

## todo

- [ ] 消息提醒通知
- [x] 用户订阅/上新通知 -> 借助大模型进行目标订阅、通知
- [x] 优惠券减免
    - 2025/08/21: 完成优惠券系统
- [x] 自动发送每日上线博文
- [ ] 添加更多大模型
- [ ] 支持用户录入内推岗位
- [x] 账号与技术派实现打通，实现技术派登录之后，校招派自动静默登录
    - 2025/09/15: 实现校招派的静默登录
- [x] 前端页面，区分 校招、实习、社招
    - 2025/08/21: 完成前台页面分区

---


v2 版本，新增模块：

- [ ] IM通信通道集成: --> @channels
  - [x] Channel顶层设计，支持多种渠道扩展 --> @core/channel
  - [x] 微信ClawBot的集成，支持绑定 + 问答交互（目前支持图文） --> @channels/wechat-clawbot
    - [x] 支持绑定多个用户
  - [ ] 飞书集成
  - [ ] 钉钉集成
- [ ] 消息转发网关
  - [x] 基于BUS方式，实现消息转发（用户IM消息 -> 中转层 -> 业务Agent -> 模型 -> 业务响应 -> IM回调 -> 用户）  
    - 定义四种消息类型：--> @core/bus
      - 接收IM消息
      - 推送消息到IM（包括用户问答回复，以及后台任务的主动推送两种触发方式）
      - 用户绑定消息
      - 用户解绑消息
  - [ ] Agent 意图识别 (即中转层，需要识别用户意图，自动路由到合适的业务Agent)
- [ ] 心跳机制与任务管理
- [ ] 用户偏好与自我学习
  - [ ] 记录聊天用户的偏好，进行自我学习 ---> 需要设计一套学习方案
  - [ ] 记录用户的岗位投递状态、面试细节、结果等
- [ ] 业务能力封装为独立的Agent
  - [ ] 岗位信息收集（需要将现在的能力封装一下进行提供）
    - [ ] IM对话方式，提供 excel、文本、http连接、markdown等，需要主动去获取信息
    - [ ] 收集信息之后，发送用户进行确认，是否进行录入
  - [ ] 岗位信息推送
  - [ ] 岗位信息推荐
- [ ] 模型交互层
  - [x] 底层大模型支持动态切换 --> @providers
    - [x] 完成OpenAI接口风格集成 --> @providers/openai
    - [x] 完成智普集成 --> @providers/zhipu
    - [x] 完成阿里集成 --> @providers/ali
    - [x] 完成Anthropic集成 --> @providers/anthropic
  - [x] 用户会话持久化
      - [x] 实现了一个基于yaml文件的存储方案 --> @core/agent/memory 
      - [ ] 同一个用户多通道的特征识别
  - [ ] 记忆：对话上下文管理 --> 需要实现一套记忆方案 --> @core/agent/memory
  - [ ] MCP
  - [ ] tools
  - [ ] SKILLS
  - [ ] 自定义插件
- [x] 动态配置管理，支持热更新 -> @core/configuration
  - [x] 支持IM通道的动态绑定，实时更新
  - [ ] 用户维护自己的ApiKey，大模型偏好，并支持热更新
- [ ] 前台页面重构

迭代计划：

- [x] 2026/04/10 
  - 整体研发进度 30%
  - V2版本项目重构:
    - app: 业务模块
    - core: 核心模块，定义框架的基础骨架（包括Channel、Agent、Bus、Configuration、Task、Permissions、providers、tools、mcp、skills等）
    - providers: 模型提供者，根据不同的厂家进行实现；对于遵循OpenAi接口风格的，同样可以使用OpenAI的Provider进行实现
    - channels: 通道模块，实现IM通道
    - plugins: 插件模块，为大模型赋能，比如提供 playwright 能力
  - 基础环境升级：jdk21 + SpringAI 升级 2.x, SpringBoot 升级 4.x
  - 完成动态配置组件，自动集成Spring环境上下文，支持动态刷新
  - 完成IM Channel顶层设计，支持多种渠道扩展
    - 实现微信ClawBot的集成，支持绑定 + 问答交互（目前支持图文）
  - 完成Providers底层设计，支持自动扩展不同厂家的模型实现
    - 实现OpenAI接口风格集成
    - 实现智普集成
    - 实现阿里集成
    - 实现Anthropic集成
    - 支持模型动态切换、支持按照用户偏好设置，选择底层模型、支持根据用户对话内容，自动在文本/视觉理解模型切换
  - 完成IM消息/响应流程设计，通过发布/订阅（Bus）的方式，实现IM消息的接收、处理、推送
    - 已打通IM聊天 到 大模型响应的链路流程
  - 使用 .env 文件进行环境变量配置