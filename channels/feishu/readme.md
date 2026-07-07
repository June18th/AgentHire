# 飞书 Channel 接入说明

本文记录 JobClaw 飞书通道的当前接入方式、权限要求、Docker 发布步骤和常见排障口径。飞书通道用于把飞书机器人收到的 IM 消息转换为 JobClaw 内部 `ChannelReceiveMessage`，再通过统一的 Agent 路由返回文本或流式卡片响应。

## 一、前置准备

1. 在飞书开放平台创建企业自建应用。
2. 启用机器人能力，并把机器人添加到需要对话的单聊或群聊中。
3. 配置事件订阅，当前代码按长连接 WebSocket 接收事件，需要启用接收消息事件 `im.message.receive_v1`。
4. 配置应用凭证：`appId` 和 `appSecret` 写入 JobClaw 渠道配置。
5. 绑定 JobClaw 用户与飞书 `openId`，否则消息可能进入未绑定用户上下文，个性化记忆和主动回复都会受影响。

## 二、权限清单

飞书开放平台需要开通并发布以下权限：

| 权限 | 用途 | 缺失表现 |
| --- | --- | --- |
| `im:message:send_as_bot` | 机器人发送普通文本消息 | 直接文本回复失败 |
| `cardkit:card:write` | 创建和更新飞书流式卡片 | 日志出现 `Create streaming card failed`，流式卡片无法创建 |

权限开通后需要在飞书开放平台发布应用版本，并确保应用已经安装到当前企业。只在后台勾选权限但未发布时，线上容器仍可能拿不到新权限。

## 三、消息与流式响应

飞书通道当前同时兼容原始 `message` 事件和 `im.message.receive_v1` 事件，核心流程如下：

```text
飞书用户消息
  -> 飞书 WebSocket 长连接事件
  -> FeiShuBotChannel 原始事件处理
  -> 创建当前消息独立的流式卡片
  -> reportToAgent()
  -> Agent 流式输出
  -> 更新飞书卡片并完成响应
```

每条入站消息会创建独立的流式卡片，避免连续发送多条消息时互相覆盖。代码还包含 10 分钟入站消息去重窗口，防止飞书事件重投导致重复回答。

如果 `cardkit:card:write` 临时不可用，当前代码会 fallback 到普通文本直回；这只能保证基础对话继续走，不能替代流式卡片权限。

## 四、模型配置

飞书链路最终仍走 JobClaw 统一的大模型配置。当前推荐文本模型配置为：

```text
zhipu#glm-4.7
```

注意不要继续使用 `zhipu#glm-4.7-flash` 作为文本模型偏好；如果供应商模型清单里没有这个模型，会出现“未找到 glm-4.7-flash 模型”一类错误。需要在后台 **LLM供应商** 页面确认：

1. 智谱供应商已启用。
2. API Key、Base URL 正确。
3. 模型清单包含 `glm-4.7`。
4. 用户文本模型偏好或默认文本模型指向 `zhipu#glm-4.7`。

## 五、Docker 重新构建与重启

飞书通道属于后端代码，修改后只需要重建并重启 `jobclaw` 服务，不需要重建 MySQL、Redis、Kafka、MinIO、Elasticsearch 等基础设施。

```powershell
docker compose -f docker/compose/compose.mysql.yml -f docker/compose/compose.redis.yml -f docker/compose/compose.kafka.yml -f docker/compose/compose.elasticsearch.yml -f docker/compose/compose.minio.yml -f docker/compose/compose.frontend.yml build jobclaw
docker compose -f docker/compose/compose.mysql.yml -f docker/compose/compose.redis.yml -f docker/compose/compose.kafka.yml -f docker/compose/compose.elasticsearch.yml -f docker/compose/compose.minio.yml -f docker/compose/compose.frontend.yml up -d jobclaw
```

重启后确认容器健康：

```powershell
docker compose -f docker/compose/compose.mysql.yml -f docker/compose/compose.redis.yml -f docker/compose/compose.kafka.yml -f docker/compose/compose.elasticsearch.yml -f docker/compose/compose.minio.yml -f docker/compose/compose.frontend.yml ps jobclaw
```

## 六、验证方式

发送一条飞书消息后，查看后端日志：

```powershell
docker logs jobclaw --since 20m | Select-String -Pattern 'FeiShu|Raw event received|Inbound message accepted|Create streaming card success|Direct reply success|Heartbeat context refreshed|Stream response completed|Stream card update metrics|Create streaming card failed|Direct reply failed|HandlerNotFound|Unauthorized|glm-4.7-flash|未找到模型配置|ERROR|WARN' -Context 1,1
```

一次完整成功链路通常能看到：

- `Raw event received`：飞书事件已经到达后端。
- `Inbound message accepted`：消息被转换并进入 JobClaw 通道。
- `Create streaming card success`：流式卡片创建成功。
- `Heartbeat context refreshed`：当前用户的飞书回话上下文已刷新。
- `Stream response completed`：Agent 流式回答完成。
- `Direct reply success`：普通文本 fallback 或直接文本消息发送成功。

也可以在 MySQL 中检查心跳上下文：

```sql
SELECT NOW() AS mysql_now;
SELECT config_key, update_time,
       JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.passThrough.input.messageId')) AS message_id,
       JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.passThrough.input.aiCardId')) AS ai_card_id,
       JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.passThrough.input.openId')) AS open_id,
       JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.passThrough.input.content')) AS content
FROM global_env_config
WHERE config_key LIKE 'agent.channels.feishu.heartbeat.%'
ORDER BY update_time DESC
LIMIT 5;
```

## 七、常见问题

### 1. 发送消息后没有任何日志

优先检查飞书开放平台事件订阅是否启用了 `im.message.receive_v1`，事件发送方式是否为长连接，并确认容器启动日志里飞书 WebSocket 已连接。

### 2. 日志显示 OpenID 不匹配或用户上下文不对

检查 JobClaw 用户绑定的飞书 `openId`。绑定值应使用飞书事件里的 sender `openId`，不是 unionId、userId，也不是机器人自己的 appId。

### 3. 提示“未找到 glm-4.7-flash 模型”

后台模型偏好或环境默认值仍指向旧模型。把文本模型改成 `zhipu#glm-4.7`，并确认智谱供应商模型清单包含 `glm-4.7`。

### 4. 流式卡片创建失败

通常是应用缺少 `cardkit:card:write` 权限，或权限已勾选但应用版本未发布。补齐权限并发布后，重新构建/重启 `jobclaw`，再通过日志确认出现 `Create streaming card success`。

### 5. 普通文本发送失败

检查 `im:message:send_as_bot` 权限是否已开通、发布并安装到企业。没有该权限时，基础文本回复也可能失败。

## 八、编码注意

本仓库中文 Markdown 文档按 UTF-8 读写。Windows PowerShell 中如果直接读取出现乱码，优先显式设置 UTF-8 输出和读取编码：

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $OutputEncoding
Get-Content -LiteralPath 'channels\feishu\readme.md' -Encoding UTF8
```
