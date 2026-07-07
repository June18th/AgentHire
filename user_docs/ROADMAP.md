# JobClaw 后续开发路线

这个文档用于记录个人理解下的 JobClaw 后续开发方向，区别于项目自带的 `docs/` 教程文档。这里更关注“未来可以怎么把项目做成真正可用的求职平台”。

## 投递流程状态机

后续可以把 JobClaw 从“岗位信息采集与推荐平台”继续扩展为“个人秋招投递记录平台”。用户看到合适岗位后，不只是收藏岗位或跳转投递链接，而是可以在本平台维护自己的投递进度、面试安排、反馈结果和复盘记录。

### 核心定位

- 岗位库负责提供职位信息。
- 投递记录负责记录“我是否投了、投到哪一步、下一步要做什么”。
- 状态机负责约束投递进度，避免状态随意跳转导致数据混乱。
- Agent 后续可以基于投递记录做提醒、复盘、推荐和求职节奏管理。

### 当前已落地

- 前端已提供“我的投递”页面，支持手动新增、编辑、删除投递记录。
- 列表支持按投递状态、公司名称、岗位名称、公司类型、关注度和跟进状态筛选。
- 投递表每页最多显示 10 条记录，分页信息放在表格下方，表格内容居中并显示行列分界线。
- 顶部统计卡片按当前筛选结果计算，覆盖活跃记录、已投递及之后阶段、笔面试中、待跟进/已逾期等数字。
- 跟进筛选支持 `全部 / 待跟进 / 已逾期`，已逾期记录会在列表中高亮，便于优先处理。
- 记录支持“已跟进”快捷操作，会写入跟进事件，并清空或更新下一次跟进日期。
- 记录支持“重新打开”终止状态，用户可以把已拒绝、已放弃、已过期、已结束等终止记录重新拉回准备投递流程。
- 投递时间、截止时间、下次跟进时间在前端只要求填写日期，不需要录入具体时分秒。
- 当前筛选结果支持 CSV 导出，导出会拉取完整筛选结果，不只导出当前页 10 条。
- “我的投递”页面已提供今日要投递、今日要跟进、今日笔面试、已逾期待办看板。
- “我的投递”页面已提供状态漏斗、公司类型分布和 Offer 转化统计。
- 首页岗位列表和实习岗位列表已支持直接标记 `感兴趣 / 准备投递 / 已投递`，并展示是否已加入投递记录。
- 投递详情已展示投递链接、截止时间、来源岗位、关注度、下一步建议、笔面试事件、状态历史和复盘备注。
- 后端已提供投递记录、状态日志、事件记录三类数据表，并通过 MySQL Liquibase 脚本维护。
- 新增记录时会对可选字段做默认值兜底，避免 MySQL `NOT NULL` 字段因空值导致保存失败。

### 建议状态

```text
INTERESTED       感兴趣
PREPARING        准备投递
SUBMITTED        已投递
WRITTEN_TEST     笔试
INTERVIEW_1      一面
INTERVIEW_2      二面
HR_INTERVIEW     HR 面
OFFER            Offer
ACCEPTED         已接受
REJECTED         已拒绝
GAVE_UP          已放弃
EXPIRED          已过期
CLOSED           已结束
```

### 主流程

```text
感兴趣
  -> 准备投递
  -> 已投递
  -> 笔试
  -> 一面
  -> 二面
  -> HR 面
  -> Offer
  -> 已接受
```

### 终止状态

```text
已拒绝
已放弃
已过期
已结束
```

### 流转规则

- `INTERESTED -> PREPARING -> SUBMITTED` 是主投递流程。
- `SUBMITTED` 之后可以进入 `WRITTEN_TEST`、`INTERVIEW_1`、`REJECTED`、`GAVE_UP`。
- `WRITTEN_TEST` 之后可以进入 `INTERVIEW_1`、`REJECTED`、`GAVE_UP`。
- `INTERVIEW_1 -> INTERVIEW_2 -> HR_INTERVIEW -> OFFER -> ACCEPTED` 是面试到录用流程。
- 任意非终止状态都可以进入 `GAVE_UP`。
- 超过投递截止时间或长期无响应时，可以进入 `EXPIRED` 或 `CLOSED`。
- `ACCEPTED`、`REJECTED`、`GAVE_UP`、`EXPIRED`、`CLOSED` 属于终止状态，默认不允许继续流转，除非用户主动重新打开。

### 建议数据对象

```text
job_application
- id
- user_id
- job_id
- company_name
- position
- apply_url
- current_status
- source
- priority        关注度，前端展示为 0-3 星，不使用“普通/重点/冲刺”文案
- deadline
- submitted_at
- next_follow_up_at
- company_type
- remark
- create_time
- update_time

job_application_status_log
- id
- application_id
- from_status
- to_status
- operator_type
- operator_id
- reason
- event_time

job_application_event
- id
- application_id
- event_type
- event_title
- event_time
- event_result
- note
```

### 前端页面规划

- 我的投递：已支持列表、筛选、统计、逾期标记、跟进处理、CSV 导出和重新打开终止记录。
- 投递详情：已展示岗位信息、投递链接、截止时间、来源岗位、关注度、状态历史、面试安排、下一步建议和复盘备注。
- 待办提醒：已展示今天要投递、要跟进、要参加笔试或面试以及已逾期的事项。
- 数据统计：已支持状态漏斗、公司类型分布和 Offer 转化；后续可以继续扩展城市、岗位类型和阶段转化趋势。
- 岗位列表入口：首页岗位列表和实习岗位列表已支持直接标记投递状态，减少重复加入。

### 当前接口

```text
GET  /api/user/applications/list
GET  /api/user/applications/detail
POST /api/user/applications/save
POST /api/user/applications/status
POST /api/user/applications/reopen
GET  /api/user/applications/by-jobs
POST /api/user/applications/delete
GET  /api/user/applications/events
GET  /api/user/applications/events/day
POST /api/user/applications/events/save
POST /api/user/applications/follow-up/complete
```

列表查询常用参数：

```text
currentStatus   当前投递状态
companyName     公司名称关键词
position        岗位名称关键词
companyType     公司类型
priority        关注度，0-3
followUpScope   跟进范围：PENDING / OVERDUE
```

### 与现有能力结合

- 在岗位详情页增加“加入投递记录”按钮。
- 首页岗位列表和实习岗位列表已支持“标记感兴趣 / 准备投递 / 已投递”，后续可继续扩展到岗位详情页和 Agent 推荐结果。
- 用 Kafka 发布投递状态变更事件，后续可用于通知、统计和 Agent 分析。
- 用 Redis 做短期提醒缓存，例如当天面试提醒、投递截止提醒。
- 用 Agent 根据用户画像、岗位偏好和历史投递结果，分析哪些岗位更适合继续推进。

### 业务闭环

```text
岗位采集
  -> 岗位搜索
  -> 岗位推荐
  -> 投递记录
  -> 面试跟进
  -> 结果复盘
  -> 推荐优化
```

这个功能适合放在 JobClaw 的下一阶段，因为它能把“看岗位”推进到“管投递”，让系统真正服务个人秋招全过程。
