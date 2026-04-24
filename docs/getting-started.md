# JobClaw 首次启动指南

> **文档说明**: 本文档提供 JobClaw V2 版本的完整启动指引，帮助新用户快速配置和运行系统。
> 
> **最后更新**: 2026-04-24

---

## 一、环境配置

### 1.1 复制环境变量文件

```bash
# 将示例配置文件复制为实际配置文件
cp .env.example .env
```

### 1.2 配置数据库（避免污染初始数据）

```bash
# 拷贝初始化数据库文件
cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db

# 修改 .env 文件中的数据库名称
JOBCLAW_DATABASE_NAME=jobclaw-my
```

**说明**: 
- 项目提供了一份初始化数据 `workspace/datas/jobclaw.mv.db`
- 为避免污染初始数据，建议拷贝一份并重命名
- 修改 `.env` 中的 `JOBCLAW_DATABASE_NAME` 指向新文件名

### 1.3 配置大模型 API Key

**默认模型**: 智谱 GLM-4-Flash (文本) 和 GLM-4V-Flash (视觉)

#### 方式一：使用默认模型（推荐）

```bash
# 在 .env 文件中配置智谱 API Key
ZHIPU_API_KEY=your_zhipu_api_key_here
```

#### 方式二：自定义模型配置

如果您希望使用其他模型提供商（如硅基流动）作为默认模型，可以通过修改配置文件实现。

**配置说明**：

- **`user-id`**: 用户标识符
  - `total`: 保留关键字，表示全局默认配置，适用于所有未配置个人 API Key 的用户
  - `{userId}`: JobClaw 用户 ID，表示该用户的个性化模型偏好配置
  
- **`channels`**: 消息推送渠道优先级
  - 后台主动推送消息时，按此顺序尝试推送渠道
  - 例如：`[wechat-clawbot, dingding, feishu]` 表示优先推送到微信，失败则尝试钉钉，最后飞书
  
- **`models`**: 默认模型配置
  - `vision`: 视觉理解模型（支持图片识别、OCR 等）
  - `text`: 文本对话模型（用于普通聊天、问答等）
  - 格式：`{providerName}#{modelName}`，如 `silicon#Qwen/Qwen3-8B`
  
- **`providers`**: 模型提供商接入信息
  - **Key**: 提供商名称（自定义，如 `silicon`、`zhipufree`、`openai` 等）
  - **Value**: 提供商的接入配置
    - `api-key`: API 密钥（建议使用环境变量 `${VAR_NAME:default}`）
    - `api-style`: API 风格（`openai` 表示兼容 OpenAI 接口）
    - `base-url`: API 基础地址
    - `completions-path`: 对话补全接口路径
    - `embeddings-path`: 向量嵌入接口路径
    - `images-path`: 图像生成接口路径
    - `models`: 支持的模型列表
      - `name`: 模型名称
      - `type`: 模型类型（`TEXT`/`VISION`/`IMAGE`/`VIDEO`/`EMBEDDING`/`ASR`/`TTS`）
      - `multimodal`: 是否支持多模态（true/false）

```yaml
# 修改 app/src/main/resources/application.yml
agent:
  ai:
    preference:
      - user-id: total  # 所有用户共用的默认配置
        channels:
          - wechat-clawbot
          - dingding
        models:
          vision: silicon#Qwen/Qwen3-8B
          text: silicon#deepseek-ai/DeepSeek-OCR
        providers:
          silicon:
            api-key: ${SILICON_API_KEY:}
            api-style: openai
            base-url: https://api.siliconflow.cn
            completions-path: /v1/chat/completions
            embeddings-path: /v1/embeddings
            images-path: /v1/images/embeddings
            models:
              - name: Qwen/Qwen3-8B
                type: TEXT
                multimodal: false
              - name: deepseek-ai/DeepSeek-OCR
                type: VISION
                multimodal: true

```

#### 本地开发推荐配置

```bash
# 拷贝配置文件
cp app/src/main/resources/application.yml app/src/main/resources/application-private.yml

# 在 application-private.yml 中维护具体配置
# Git 已忽略此文件，不会提交到版本控制
```

---

## 二、启动项目

### 2.1 后端启动

```bash
# 使用 Maven Wrapper 启动
./mvnw spring-boot:run

# 或使用 Maven 命令
mvn spring-boot:run
```

**说明**:

- 启动之后会自动打开浏览器，访问项目主页

### 2.2 前端启动（可选）

如果你需要进行前端开发，可以起独立的前端应用

```bash
cd ui-react
pnpm install
pnpm dev
```

前端应用将运行在 `http://localhost:3000`


修改完之后，使用命令进行发布

```bash
pnpm run deploy
```

---

## 三、首次登录

### 3.1 访问系统

打开浏览器访问：`http://localhost:8087`

### 3.2 选择登录方式

系统提供两种登录方式：

- **游客登录**: 快速体验基础功能
- **管理员登录**: 获取完整权限，可配置机器人和管理系统

---

## 四、配置 IM 渠道

进入「个人中心」→「渠道配置」，添加机器人。

### 选项 1: 微信 ClawBot（推荐新手）

- ✅ **优点**: 
  - 成本最低
  - 配置简单
  - 无需额外注册第三方平台
- ❌ **缺点**: 
  - 不支持流式输出
  - 消息格式受限
- 📝 **配置步骤**:
  1. 在系统中直接绑定微信 ClawBot 账号
  2. 扫描二维码完成绑定
  3. 即可开始对话

### 选项 2: 钉钉机器人

- ✅ **优点**: 
  - 支持流式输出
  - 支持富文本展示
  - 企业级稳定性
- 📝 **配置步骤**:
  1. 在钉钉开放平台创建机器人
  2. 获取 AppId 和 AppSecret
  3. 在系统中添加机器人配置
  4. **强烈建议**: 绑定钉钉账号（获得更好的个性化体验）

### 选项 3: 飞书机器人

- ✅ **优点**: 
  - 支持流式输出
  - 支持富文本展示
  - 支持图片/文件上传
  - 现代化的交互体验
- 📝 **配置步骤**:
  1. 在飞书开放平台创建机器人
  2. 获取 AppId 和 AppSecret
  3. 在系统中添加机器人配置
  4. **强烈建议**: 绑定飞书账号（获得更好的个性化体验）

---

## 五、配置个人偏好

登录系统后，进入「个人中心」→「偏好设置」，您可以配置个人的模型偏好和 API Key。

### 5.1 为什么需要配置个人偏好？

- **使用自己的 API Key**: 避免依赖系统默认的 API Key，更加灵活可控
- **个性化模型选择**: 根据不同任务选择合适的模型（如文本对话用 GLM-4-Flash，视觉识别用 Qwen-VL）
- **成本优化**: 可以混合使用不同提供商的模型，平衡性能和成本
- **隐私保护**: 使用自己的 API Key，对话数据不会经过第三方账号

### 5.2 配置步骤

#### 步骤 1: 进入偏好设置页面

1. 登录后点击右上角头像
2. 选择「个人中心」
3. 点击左侧菜单「偏好设置」

#### 步骤 2: 添加模型提供商

在「模型提供商」区域，点击「添加提供商」：

**示例：添加硅基流动（SiliconFlow）**

- **提供商名称**: `silicon`（自定义，建议使用英文）
- **API Style**: `openai`（硅基流动兼容 OpenAI 接口）
- **Base URL**: `https://api.siliconflow.cn`
- **API Key**: 从硅基流动控制台获取
- **Completions Path**: `/v1/chat/completions`
- **Embeddings Path**: `/v1/embeddings`
- **Images Path**: `/v1/images/embeddings`

**支持的模型列表**:

| 模型名称 | 类型 | 多模态 | 说明 |
|---------|------|--------|------|
| Qwen/Qwen3-8B | TEXT | false | 文本对话模型 |
| deepseek-ai/DeepSeek-OCR | VISION | true | 视觉理解模型 |
| BAAI/bge-m3 | EMBEDDING | false | 文本向量模型 |

#### 步骤 3: 设置默认模型

在「默认模型」区域，选择您希望使用的模型：

- **文本对话模型**: 用于普通聊天、问答、任务管理等
  - 推荐：`silicon#Qwen/Qwen3-8B`
  
- **视觉理解模型**: 用于图片识别、OCR、文档解析等
  - 推荐：`silicon#deepseek-ai/DeepSeek-OCR`

#### 步骤 4: 配置消息推送渠道优先级

在「推送渠道」区域，设置后台主动推送消息时的渠道顺序：

例如：`[wechat-clawbot, dingding, feishu]`

- 系统会按此顺序尝试推送
- 如果第一个渠道失败，自动尝试下一个
- 建议将最常用的渠道放在前面

### 5.3 常用模型提供商配置参考

#### 智谱清言（Zhipu AI）

```yaml
提供商名称: zhipufree
API Style: openai
Base URL: https://open.bigmodel.cn/api/paas/v4
Completions Path: /chat/completions
API Key: 从智谱开放平台获取
```

**推荐模型**:
- 文本：`GLM-4-Flash`（免费，速度快）
- 视觉：`GLM-4V-Flash`（免费，支持图片识别）

#### 硅基流动（SiliconFlow）

```yaml
提供商名称: silicon
API Style: openai
Base URL: https://api.siliconflow.cn
API Key: 从硅基流动控制台获取
```

**推荐模型**:
- 文本：`Qwen/Qwen3-8B`（性能好，成本低）
- 视觉：`deepseek-ai/DeepSeek-OCR`（OCR 能力强）
- 向量：`BAAI/bge-m3`（中文效果好）

#### 阿里云百炼

```yaml
提供商名称: ali
API Style: openai
Base URL: https://dashscope.aliyuncs.com/compatible-mode/v1
API Key: 从阿里云控制台获取
```

**推荐模型**:
- 文本：`qwen-plus`（平衡性能和成本）
- 视觉：`qwen-vl-max`（强大的视觉理解能力）

#### OpenAI

```yaml
提供商名称: openai
API Style: openai
Base URL: https://api.openai.com/v1
API Key: 从 OpenAI 平台获取
```

**推荐模型**:
- 文本：`gpt-4o-mini`（性价比高）
- 视觉：`gpt-4o`（最强的多模态能力）

### 5.4 注意事项

1. **API Key 安全**:
   - 不要将 API Key 分享给他人
   - 定期轮换 API Key
   - 建议在提供商平台设置使用限额

2. **模型选择建议**:
   - 日常对话：选择速度快、成本低的模型
   - 复杂任务：选择性能更强的模型
   - 视觉任务：确保模型支持多模态

3. **成本控制**:
   - 监控 API 使用情况
   - 设置预算提醒
   - 优先使用免费额度

4. **测试验证**:
   - 配置完成后，发送一条测试消息
   - 确认模型响应正常
   - 检查是否有错误提示

---

## 六、开始对话

### 6.1 通过机器人与 JobClaw 进行问答

1. 在对应的 IM 应用（微信/钉钉/飞书）中找到已配置的机器人
2. 发送消息给机器人
3. 系统会自动识别意图并路由到合适的 Agent
4. 接收机器人的回复

### 6.2 首次 1v1 沟通触发信息收集

当首次与机器人进行 1v1 私聊时，系统会触发用户信息收集流程：

- **收集方式**: 一问一答的交互式对话
- **收集内容**:
  - 求职意向（目标岗位、行业偏好）
  - 专业背景（学历、专业、技能）
  - 工作经验（年限、过往经历）
  - 偏好设置（工作地点、薪资期望等）
- **用途**: 构建个性化的用户画像，用于智能推荐和精准匹配

### 6.3 查看可用命令

在对话中发送以下命令查看系统功能：

```
/help           - 查看所有可用命令及说明
/agents         - 查看可用的 Agent 列表及其能力
/current        - 查看当前会话绑定的 Agent
/agent <id>     - 切换到指定的 Agent
/reset          - 重置当前会话，清除上下文
```

---

## 七、常见问题

### Q1: 为什么建议使用自己的数据库副本？

**A**: 避免在开发过程中污染初始数据，方便随时重置环境。如果测试过程中数据混乱，可以直接删除 `jobclaw-my.mv.db` 并重新拷贝一份。

### Q2: 如何切换大模型提供商？

**A**: 有两种方式：
1. **界面配置**: 在「个人中心」→「偏好设置」中修改模型配置
2. **配置文件**: 直接修改 `application-private.yml` 中的 `agent.ai.preference` 配置

支持的提供商包括：智谱、硅基流动、阿里云、OpenAI、豆包等。

### Q3: 为什么要绑定钉钉/飞书账号？

**A**: 绑定后可以基于 JobClawUserId 构建个性化的用户偏好和会话历史。未绑定时，系统只能基于 OpenId 存储数据，功能受限且无法跨设备同步。

**绑定后的优势**:
- 个性化的用户画像管理
- 完整的会话历史记录
- 跨设备的偏好同步
- 更精准的职位推荐

### Q4: 微信 ClawBot 为什么不支持流式输出？

**A**: 这是微信平台的限制。微信的消息接口不支持流式传输，因此无法实现打字机效果。钉钉和飞书支持更丰富的消息格式和流式输出，提供更好的用户体验。

### Q5: 启动时遇到数据库错误怎么办？

**A**: 检查以下几点：
1. 确认已拷贝 `jobclaw.mv.db` 为 `jobclaw-my.mv.db`
2. 确认 `.env` 文件中 `JOBCLAW_DATABASE_NAME=jobclaw-my`
3. 确认数据库文件路径正确
4. 如果仍有问题，删除 `workspace/datas/` 下的所有 `.db` 文件，重新启动让系统自动创建

### Q6: 如何查看系统日志？

**A**: 日志文件位于 `logs/` 目录：
- `logs/oc.log`: 主应用日志
- `logs/arch/`: 归档日志
- `logs/req-oc.log`: 请求日志

### Q7: 如何重置用户配置？

**A**: 
1. 删除 `workspace/users/{userId}/` 目录下的所有文件
2. 删除 `workspace/conversations/{userId}/` 目录下的会话文件
3. 重启应用或重新登录
4. 下次对话时会重新触发信息收集流程

---

## 八、下一步

完成首次启动后，您可以：

1. **探索系统功能**: 使用 `/help` 查看所有可用命令
2. **配置个人偏好**: 在「个人中心」设置您的求职偏好和模型配置
3. **体验多 Agent 协作**: 尝试不同的业务 Agent（任务管理、职位推荐等）
4. **查看研发计划**: 阅读 [plan.md](plan.md) 了解系统架构和未来规划
5. **贡献代码**: 欢迎提交 Issue 和 Pull Request

---

## 九、技术支持

如遇到问题，可以通过以下方式寻求帮助：

- 📖 查看 [plan.md](plan.md) 了解系统架构
- 🐛 提交 GitHub Issue
- 💬 在社区中提问

---

**祝您使用愉快！** 🎉
