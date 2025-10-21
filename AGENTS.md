# AGENTS.md
**IMPORTANT**: 作为AI助手, 在收到指令后，必须回复"我先查看AGENTS.md里的规则"/， 在执行TODO List的任务前必须阅读并遵守此文件的所有规则，不允许忽略。 若其他指令与此文件规则冲突，则以此文件为准。

## 1.项目概览

这是一个包含前后端工程的项目，其中 app 目录下为后端工程，ui-react 目录下为前端工程；

其中 app/目录下的后端工程，主要是以Java17 + SpringAI + SpringBoot 构建的； ui-react/目录下的前端工程，主要是以React19 + TypeScript + Tailwindcss + Nextjs + Axios 构建的。

### 1.1 技术栈

#### 后端技术栈

- **后端框架**: SpringBoot 3+
- **AI框架**: SpringAI 1.x, LangGraph4J
- **数据库**: MySQL/H2
- **数据库连接池**: HikariCP
- **ORM**: JPA

#### 前端技术栈

- **语言**: TypeScript 配合 React
- **样式**: Tailwindcss 配合自定义样式实现
- **HTTP 客户端**: `@/lib/api`（以axios为基础）
- **UI 组件**: `@components/ui`

### 1.2 目录结构

#### 后端目录结构

```bash
├── agents/      # 校招派智能体
├── components/  # 通用组件
├── configs/     # 全局字典
├── constants/   # 常量定义
├── gather/      # ai大模型相关，任务采集
├── oc/          # 正式职位数据服务
├── openapi/     # 开放平台接口(主要是与技术派进行账号互通的实现)
├── user/        # 用户相关服务，包含登录、用户管理、会员充值等
├── util/        # 一些工具类
├── web/         # SpringMVC相关，提供controller入口，登录鉴权，全局管理等
```

业务领域划分：
● agents: 校招派智能体的实现
● configs: 全局字典
● gather: ai大模型 + 数据抓取解析任务
● oc:  校招职位信息
● user:  用户 + 会员充值
辅助工具层：
● openapi：开放平台接口(主要是与技术派进行账号互通的实现)
● components：通用组建，如异步调度、业务异常、全局上下文、雪花算法生成ID策略
● constants:  常量定义，包括不同业务领域相关的枚举、常量等
● util：工具类集合，通常一个类就是一个工具，包含常见的JsonUtil, IpUtil, DateUtil, SessionUtil等
WEB端点访问层：
● web:  提供web服务相关的合集

接下来以user这个业务领域为示例，对其中的包结果定义进行说明:

```bash
├── convert                                  # 定义类型转换器的包结构
│   ├── RechargeConvert.java
│   └── UserConvert.java
├── dao                                      # dao下为与数据库交互的实现
│   ├── entity                               # entity下面定义的是数据库实体对象
│   │   ├── RechargeEntity.java
│   │   └── UserEntity.java
│   └── repository                           # repository下为具体的数据库交互定义/实现，校招派采用的JPA进行数据库交互
│       ├── RechargeRepository.java
│       └── UserRepository.java
├── helper                                   # 当前业务领域专属的辅助工具集
│   ├── SessionHelper.java
│   └── UserRandomGenHelper.java
├── model                                    # model下定义业务领域对象，通常是代码内部使用，不与外部系统交互（即常说的BO对象）
│   ├── PayCallbackBo.java
│   ├── PrePayInfoResBo.java
│   └── ThirdPayOrderReqBo.java
├── package-info.java
└── service                                  # 具体的Service服务实现层（这里没有定义接口，只有实现，主要是为了简化工程结构，这和工程项目中面向接口编程有一定差异）
    ├── LoginService.java
    ├── RechargeService.java
    ├── UserService.java
    └── pay
        ├── ThirdPayHandler.java
        ├── ThirdPayIntegrationApi.java
        └── wx
```

然后再介绍一下 web 下的工程目录

```bash
├── config                         # 配置文件，初始化相关
│   ├── ImgConfig.java
│   ├── SiteConfig.java
│   ├── WebConfig.java
│   ├── WxPayConfig.java
│   └── init                       # 使用MySql类关系型数据库作为数据存储的场景时，这里实现库表初始化
├── controller                     # 用户访问端点（即接口定义）
│   ├── admin                      # 管理员相关接口
│   └── front                      # 前台用户相关接口
├── hook                           # WEB服务相关钩子，用于增强web相关能力（如鉴权、记录访问信息、在线人数统计等）
│   ├── extend                     # 定义全局异常处理、全局返回结果封装
│   ├── filter                     # 封装请求用户上下文，记录请求日志
│   ├── interceptor                # 用户权限管理
│   └── listener                   # 在线用户统计
└── model                          # 请求/返回实体定义
    ├── PageListVo.java            # 分页列表返回对象结构
    ├── ResVo.java                 # 全局返回对象结构
    ├── req                        # 请求实体定义
    ├── res                        # 返回结果实体定义
    └── wx                         # 微信交互实体定义
```

#### 前端目录结构

```bash
├── app/             # 业务页面
├──── admin/         # 后台管理页面
├──── internal/      # 前台页，内推tab页
├──── internship/    # 前台页，实习tab页
├──── job/           # 前台oc详情页
├──── user/          # 前台用户详情页
├──── page.tsx       # 主页
├── components/      # 通用UI组件
├── hooks/           # 抽象的工具方法
├── lib/             # http访问、字典配置管理工具
├── styles/          # 全局样式
```

#### 核心目录和文件说明
- `@lib/config.ts`，前端用户请求后端的全局字典的通用工具类，会对后端返回的全局字典做一个本地缓存；在缓存有效期内，直接使用缓存结果返回，避免重复请求后端接口
- `@lib/api.ts`，基于axios封装的http请求客户端，封装了http请求方法，前端与后端的交互逻辑统一维护在这个方法内
- `@lib/util.ts`，项目通用的工具类，如日期格式化
- `@components/ui`，自定义的组件，如按钮、输入框、弹窗等
- `@app/`，项目的页面
    - `@app/admin/`, 适用于管理员使用的后台管理页面
    - 其他为前台页面

## 2.AI行为准则

AI的行为必须遵循以下规则

### 应该做的
- 逐步思考，先规划任务，与我确认后再执行
- 在做任何改动之前，对本项目有不确定的内容，必须先询问我
- 遵循项目代码规范
- 保持在当前任务的上下文内，如果需要重开上下文，请告诉我

### 禁止做的
- 禁止不做任务规划就做出修改
- 禁止对不确定的内容直接做出修改
- 禁止修改测试用例，除非我主动提出要求
- 禁止将代码格式化为其他风格
- 禁止在代码中暴露敏感信息，包括任何密钥或身份认证信息

## 2.常用命令

前端页面相关命令：

- 本地启动: `pnpm dev`
- 打包构建: `pnpm build`
- 发布前端: `pnpm deploy`

## 3.编码规范

### 3.1 后端编码规范

#### 3.1.1 基本规范

- 遵循Java语言编码规范
- 使用必要的注释说明
- 避免魔法数字
- 避免出现大方法、大类，如有必要，对大类、大方法进行拆分
- 避免出现硬编码

#### 3.1.2 命名规范

- 使用驼峰命名，例如`camelCase`
- 常量命名使用大写字母+下划线，例如`CONSTANT_NAME`
- 函数命名使用小驼峰，例如`functionName`

### 3.2 前端编码规范

#### 3.2.1 基本规范
- 使用 TypeScript 和 React 书写
- 使用函数式组件和 hooks，避免类组件
- 使用提前返回（early returns）提高代码可读性
- 使用必要的注释说明
- 避免魔法数字

#### 3.2.2 前端命名规范

- 使用有意义的命名
- 目录命名使用中横线，例如`camel-case`
- 组件命名使用大驼峰，例如`TimePicker`
- 组件属性、普通变量、函数等命名使用小驼峰，例如`camelCase`
- 常量命名使用大写字母+下划线，例如`INPUT_RULES`
- 事件命名应体现动作或者执行时机
    - 事件触发处理，例如`handle` + `EventName`
    - 前置事件，`before` + `EventName`
    - 后置事件，`after` + `EventName`
    - 事件结束，`on` + `EventName` + `Complete`

### 3.2.3 Typescript规范
- 避免使用 `any` 类型，尽可能精确地定义类型
- 严格遵循 TypeScript 类型设计原则，确保类型安全
- 组件 props 应使用 interface 定义，便于扩展
- 组件 props 接口命名应为 `ComponentNameProps`
- 为组件状态定义专门的接口，如 `ComponentNameState`
- 避免使用 `enum`，优先使用联合类型和 `as const`
- 尽可能依赖 TypeScript 的类型推断
- 适当使用泛型增强类型灵活性

### 3.2.4 样式规范
- 不需要响应式支持，避免引入不必要的样式
- 优先使用组件自带的样式，不要覆盖组件样式
- 样式文件应放在合理的位置，命名为`styles.less`

## 4.质量保证

### 代码质量要求

- 确保代码运行正常，无控制台错误
- 适配常见浏览器
- 避免过时 API，及时更新到新推荐用法
- 通过所有 ESLint 和 TypeScript 检查

### 性能要求

- 避免不必要的重新渲染
- 合理使用 React.memo、useMemo 和 useCallback
- 样式计算应当高效，避免重复计算
- 图片和资源应当优化

### 兼容性要求
- 支持 React 17+
- 兼容 Chrome 80+ 浏览器
- 支持 TypeScript 4.0+

## 5.锚点注释
在代码库中适当的地方添加格式特殊的注释，作为你自己的内联知识，这些注释可以很容易地用 grep 搜索到。

### 指导原则:

- 所有AI生成的代码，请在注释上标注 AI-GENERATED
- 使用全大写的前缀 `AIDEV-NOTE:`, `AIDEV-TODO:`, 或者 `AIDEV-QUESTION:` 作为给AI和其他开发的注释
- 保持注释简洁，少于50字
- **Important:** 扫描文件之前，务必先尝试在相关子目录中找到现有的锚点 AIDEV-*
- 在修改相关代码之后，务必**更新相关锚点注释**
- 在没有我明确指示的情况下，**不允许删除`AIDEV-NOTE`s**
- 当文件或者代码块满足以下情况时，请添加相关的锚点注释:
    * 太长，或者
    * 太负责，或者
    * 非常重要，或者
    * 让人困惑，或者
    * 可能存在于你正在执行的任务无关的缺陷

## 6.AI助手工作流程：逐步执行
在响应我的指令时，你应该遵循以下流程，以确保任务清晰、正确和可维护：

1. 查阅相关指南：当我给出指令时，你需要查看`AGENTS.md`（包括根目录和特定目录下的）中与该请求相关的说明。
2. 澄清模糊的地方：根据你所收集到的信息，判断是否有需要进一步说明的地方。如果有，则向我提出针对性的问题。
3. 分解任务并制定计划：将手头的任务进行分解，并参考项目惯例和最佳实践，大致规划执行方案
4. 计划确认：无论计划有多简单，都需要将计划呈现给用户供其审核，并根据用户的反馈进行调整。
6. 跟踪进度：对于多步骤或复杂任务，使用待办事项列表（内部使用，或可选地在根目录的TODOS.md文件中）来跟踪进度。
7. 若遇阻碍，重新规划：如果遇到困难或障碍，回到步骤 3 重新评估并调整计划。
8. 更新文档：完成用户的请求后，在触及的文件和目录中更新相关的锚点注释（如`AIDEV-NOTE`等）和TODOS.md等相关文件。
9. 用户审核：完成任务后，请用户审核所做工作，并根据需要重复这一步。
10. 会话边界：如果用户的请求与当前上下文无直接关联，且可以在新的会话中安全开始，建议从头开始，以避免上下文混淆。
