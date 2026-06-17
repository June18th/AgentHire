# 07-✅实现一个求职派MCP Server

## 一、MCP Server
我们希望在求职派中实现一个MCP Server，那么这个 Server主要提供哪些能力 呢，怎么为用户提供服务呢？

### 1.设计目标
我们准备基于 **求职派** 已有的 offer/OC 信息，构建一个 **面向用户的校招信息查询与推荐 MCP Server**。它的目标是让大模型能直接调用我们的校招数据接口，为用户提供个性化的求职信息服务。


希望通过 MCP 的标准化协议，把“求职派”积累的海量校招数据变成一个可被 AI 助手访问的“智能服务接口”。这样，无论是 ChatGPT、Claude，还是自研 AI 助手，都可以通过 MCP 与我们进行对话式交互，实时查询或推荐校招岗位。


OK，我们一期的目标是：根据用户的个人信息与求职目标，为其推荐匹配的校招机会。


例如：


:::color1
“帮我看看今年有哪些适合计算机专业应届生的深圳岗位”“我想进银行类国企，有什么正在开放的校招吗？”

:::


AI 助手会通过 MCP 协议访问我们的服务端接口，返回经过筛选和结构化推荐的校招信息。


二期的目标是：在完成查询推荐的基础上，进一步实现“订阅”功能。


用户输入自己的诉求（行业、地区、目标岗位等）后，当有新的匹配校招信息发布，系统会主动推送更新结果，让求职信息的触达更及时、更智能。


接下来，我将完整记录 **“求职派首个 MCP Server 的诞生全过程”** ——从协议设计、服务搭建，到接口定义与调试，带大家看看这个从 0 到 1 的智能服务是怎么落地的。

### 2.交互流程
整个 MCP 的使用分为两个阶段：

1. 配置阶段：用户将求职派MCP Server配置到相关工具中（Trae）
2. 使用阶段：用户在Trae中问答，执行求职派MCP Server，获取职位推荐


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753685890767-10c20ad5-c892-42f6-893d-a03a12bae009.png)

交互时序图

## 二、实现步骤


1. 设计一个MCP Server，定义输入参数，返回职位列表 （核心）
2. 用户获取MCP Server配置信息 + 后台的访问鉴权

### 1.求职派MCP Server实现
由于这个接口是供大模型调用的，所以使用对象和我们平时的CURD开发有点不一样，日常的需求中，产品一般会给我们明确的应用场景、定义好查询条件，但针对大模型的调用，我们应该如何设计接口呢？

#### 步骤一：添加MCP Server依赖
既然要开发mcp server， 第一步当然是在pom.xml中，添加mcp server的依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

#### 步骤二：MCP Server配置
接下来就是在配置文件中，指定我们的 MCP Server相关配置


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753687063166-cae4d74e-c573-4b1b-ac1b-6b23a9bcf683.png)

```yaml
spring:
  ai:
    mcp:
      server:
        name: 求职派
        type: sync
        instructions: "求职派是一个专业的校招信息推荐服务，可以根据您输入的个人求职意愿如求职公司类型、工作地点要求、求职岗位、校招或者实习、毕业年限，自动匹配返回最优的校招信息列表，让你不再错过招聘信息"
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/messages
        version: 1.0.0
        capabilities:
          tool: true # 是否支持工具
          resource: true # 是否支持资源
          prompt: true # 是否支持提示词
          completion: true # 是否支持补全
```

#### 步骤三：MCP Server接口设计
假定现在给用户提供一个职位信息查询的接口，用户的目的是检索出适合自己的企业信息，那一般会有哪些输入呢？

+ 招聘对象：比如我是26届应届生，我更希望查询的招聘岗位是针对 26届的
+ 招聘类型：春招？秋招？社招？ 或者是准备找实习?
+ 公司类型：用户的个人偏好，是要外企还是国央企？
+ 工作地点：希望工作的城市
+ 岗位：我想找研发的岗位，就不要推设计的岗位给我了


基于上面的分析，我们的工具定义就出来了：

```java
@Data
public class McpReqDto {
    @ToolParam(description = "招聘类型：春招、秋招、秋招提前批、补录、暑期实习、寒假实习、日常实习、社招等", required = false)
    private String recruitmentType;

    @ToolParam(description = "招聘对象：如2025年毕业生、2026年毕业生、2027年毕业生", required = false)
    private String recruitmentTarget;

    @ToolParam(description = "职位名称：如研发工程师、运营等", required = false)
    private String position;

    @ToolParam(description = "职位地点：如武汉、全国等", required = false)
    private String jobLocation;

    @ToolParam(description = "职位类型：如央国企、外企、私企、事业单位、学校、银行等", required = false)
    private String companyType;
}

```

其中 `@ToolParam`就是用来告知大模型，这个传参的含义，是否必填？


然后我们来完成Mcp Server的工具定义，参数是 `McpReqDto`，返回的是求职派前台展示的 `OcVo` 列表

```java
@Data
@Accessors(chain = true)
public class OcVo {
    private Long id;

    /**
     * 草稿数据
     */
    private Long draftId;

    /**
     * 公司名称
     */
    private String companyName;
    /**
     * 公司类型
     */
    private String companyType;
    /**
     * 工作地点
     */
    private String jobLocation;
    /**
     * 招聘类型
     */
    private String recruitmentType;
    /**
     * 招聘对象
     */
    private String recruitmentTarget;
    /**
     * 岗位
     */
    private String position;
    /**
     * 投递进度
     */
    private String deliveryProgress;
    /**
     * 岗位更新时间
     */
    private String lastUpdatedTime;
    /**
     * 投递截止
     */
    private String deadline;
    /**
     * 相关链接
     */
    private String relatedLink;
    /**
     * 招聘公告
     */
    private String jobAnnouncement;
    /**
     * 内推码
     */
    private String internalReferralCode;
    /**
     * 备注
     */
    private String remarks;

    /**
     * 状态:
     * -1 删除
     * 0 隐藏
     * 1 已发布
     */
    private Integer state;
    /**
     * 创建时间
     */
    private Long createTime;
    /**
     * 更新时间
     */
    private Long updateTime;
}


@Service
public class OcMcpService {
    @Tool(description = "根据输入的用户求职意愿信息，返回满足条件的职位列表给用户")
    public List<OcVo> queryRecommendOcListForUser(McpReqDto req) {
        return List.of();
    }
}

```

#### 步骤四：MCP Server接口实现
接口定义完成之后，实现就比较简单了，完全可以直接复用现有的查询逻辑

```java
@Service
public class OcMcpService {
    private final OcService ocService;

    @Autowired
    public OcMcpService(OcService ocService) {
        this.ocService = ocService;
    }

    @Tool(description = "根据输入的用户求职意愿信息，返回满足条件的职位列表给用户")
    public List<OcVo> queryRecommendOcListForUser(McpReqDto req) {
        OcSearchReq search = new OcSearchReq();
        if (StringUtils.isNotBlank(req.getCompanyType())) {
            search.setCompanyType(req.getCompanyType());
        }
        if (StringUtils.isNotBlank(req.getRecruitmentType())) {
            search.setRecruitmentType(req.getRecruitmentType());
        }
        if (StringUtils.isNotBlank(req.getRecruitmentTarget())) {
            search.setRecruitmentTarget(req.getRecruitmentTarget());
        }
        if (StringUtils.isNotBlank(req.getPosition())) {
            search.setPosition(req.getPosition());
        }
        if (StringUtils.isNotBlank(req.getJobLocation())) {
            search.setJobLocation(req.getJobLocation());
        }
        // 定义最多只返回20条数据
        search.setPage(1);
        search.setSize(20);
        PageListVo<OcVo> vo = ocService.searchOcList(search);
        return vo.getList();
    }
}
```


#### 步骤五：注册MCP工具
将上面实现的tool注册为MCP Server

```java
@Configuration
public class OcMcpConfiguration {
    @Bean
    public ToolCallbackProvider dateProvider(OcMcpService dateService) {
        return MethodToolCallbackProvider.builder().toolObjects(dateService).build();
    }
}
```


#### 步骤六：测试验证
到这里，mcp server的核心功能已经开发完成，接下来进入连接验证。


我们使用TraeCN作为测试工具，首先在TraeCN上配置MCP（**假如你正在尝试这一步，请直接跳过，因为后续我们做了鉴权，这里会失败**）


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753694157602-81fbcfd5-903f-4977-9b4c-076daec78f35.png)

对应的配置json信息为：

```json
{
  "mcpServers": {
    "求职派": {
      "type": "sse",
      "url": "http://localhost:8080/sse",
      "version": "1.0.0"
    }
  }
}
```


在 Trae 中可以基于这个MCP创建一个智能体，接下来看一下mcp的使用 case；先准备一些职位信息数据。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753695076367-80b39079-d4a6-48ca-901f-0ccadc42054e.png)

然后进行对话：

```json
我现在是26届毕业的计算机专业学生，我理想的工作地点是北京，想找一些研发的岗位，请帮我推荐一些秋招信息
```


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753695480492-2116f0c4-506b-416e-ba52-e925fa11fa9f.png)


说明：

+ 在本地测试时，oc表中的数据越全，推荐效果越好；
+ 针对招聘类型、对象等，虽然采用的是 like查询，但如果用户传入了一个 “校招”，由于我们存的都是 春招/秋招，就会导致查询不到数据，这块需要我们后续进行优化

### 2.MCP权限管理
OK，到此为止，一个简单的 mcp  server 就搞定了。但这里有一个隐患，那就是：只要别人拿到我们的访问端点，就可以使用，没有权限控制。


如何杜绝这种白嫖行为呢？

****

**答案是：添加权限管控。**

****

求职派本身已经实现了基于 JWT 的鉴权，所以我们可以直接拿来用到 MCP Server 中。

#### 步骤一：权限校验
我们直接采用Http的权限管控，即在请求头中添加`Authorization`字段，值为`Bearer <token>` 方式来进行管控；求职派的web站点是通过拦截器 `PermissionCheckInterceptor`来实现的，我们可以在这里做一个针对MCP Server调用的校验。


这个token如何设计呢？


答：考虑到我们的登录方式是通过微信公众号的方式，我们直接将用户的微信id来作为每个用户的访问token，正好也可以省一个额外的存储字段（说明：求职派在用户表设计时，也预留了 loginName + password 两个字段，因此也可以考虑使用 `Basic <user:password>`方式进行校验）


权限校验的实现代码如下

```java
@Slf4j
@Component
public class PermissionCheckInterceptor implements HandlerInterceptor {
    @Value("${spring.ai.mcp.server.sse-endpoint:/sse}")
    private String sseUrl;
    @Value("${spring.ai.mcp.server.sse-message-endpoint:/mcp/messages}")
    private String msgUrl;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod) {
            // 省略 web 站点的权限管控
        } else if (!checkMcpPermission(request)) {
            // mcp 相关校验 - 无权访问
            response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getWriter().println(JsonUtil.toStr(ResVo.fail(StatusEnum.FORBID_VIP_INFO)));
            response.getWriter().flush();
            return false;
        }
        return true;
    }

    private boolean checkMcpPermission(HttpServletRequest request) {
        if (!mcpUrl(request)) {
            // 不是mcp的请求，不做拦截
            return true;
        }
        // 表示是mcp的请求，需要进行权限管控
        String auth = request.getHeader("Authorization");
        if (StringUtils.isBlank(auth) || !auth.startsWith("Bearer ")) {
            return false;
        }
        // 根据token，获取用户信息，如果拿不到，则表明没有传用户身份，直接返回false，无权访问
        String token = auth.substring(7);
        UserBo user = SpringUtil.getBean(UserService.class).getUserByWxId(token);
        if (user == null) {
            return false;
        }

        // 设置用户上下文信息
        ReqInfoContext.getReqInfo().setUserId(user.userId());
        ReqInfoContext.getReqInfo().setUser(user);
        return true;
    }

    private boolean mcpUrl(HttpServletRequest request) {
        String reqUrl = request.getRequestURI();
        return reqUrl.equals(sseUrl) || reqUrl.equals(msgUrl);
    }
}
```

#### 步骤二：拦截器配置
请注意，权限管控拦截器之前只拦截 /api 开头，为了沿用这个逻辑，我们需要调整一下MCP Server的端点规范(当然也可以直接调整拦截器的应用配置）


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753697423854-8c6f4a0c-46f1-455b-bc44-27cf83128013.png)

调整后的config为

```yaml
  ai:
    mcp:
      server:
        name: 求职派
        type: sync
        instructions: "求职派是一个专业的校招信息推荐服务，可以根据您输入的个人求职意愿如求职公司类型、工作地点要求、求职岗位、校招或者实习、毕业年限，自动匹配返回最优的校招信息列表，让你不再错过招聘信息"
        sse-endpoint: /api/sse
        sse-message-endpoint: /api/mcp/messages
        version: 1.0.0
        capabilities:
          tool: true # 是否支持工具
          resource: true # 是否支持资源
          prompt: true # 是否支持提示词
          completion: true # 是否支持补全
```


#### 步骤三：更新Trae MCP配置
因为我们调整了访问端点，并增加了鉴权，因此我们需要重新调整一下mcp的配置信息

+ 注意：不要直接在弹窗中编辑json串，弹窗中的内容相比于原始json文件，缺少了 `type` 和 `version`


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753698178811-f206e29b-e8ef-47a2-b512-76daf281ba35.png)


你也可以直接复制下面的 JSON（那这个配置怎么来，为什么是这个样子呢？后面会讲，不着急）：


```java
"求职派": {
            "type": "sse",
            "url": "http://localhost:8087/api/sse",
            "version": "1.0.0",
            "headers": {
                "Authorization": "Bearer demoUser-admin"
            }
        }
```


当 MCP 的配置中求职派的 Server 配置中出现绿色对号的时候就标明成功了。


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1759983081041-d8ee021e-f6d5-47ce-aab4-ed982ba8ebf4.png)


#### 步骤四：测试验证
我们在MCP Server 调用的入口，添加一行日志


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753698709371-4028c20c-7ce0-49cc-8b03-ffb69b80e956.png)

然后在Trae中，进行交互问答。


注意这里要选【@Builder with MCP】，输入“我现在是26届毕业的计算机专业学生，我理想的工作地点是北京，想找一些研发的岗位，请帮我推荐一些秋招信息”


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1759983249447-6b787875-b173-4dec-a7a1-681b40cb25a7.png)


TRAE 会在这里提示调用我们本地的 MCP Server。点击【run】。


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1759983228965-f287dc65-96aa-4d58-8b80-db4116e74288.png)


如果没跑通的话，很可能是你的 MCP Server 配置出了点问题，检查一下是否和我给出的例子有差异。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753699580416-a3e32668-ed7e-42f7-92d9-a87064d9aaab.png)


不过这里有一个 bug：就是第二次执行会没有返回结果。


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760011517462-688a6113-55f9-4939-8882-417c016a2a81.png)


深入调查了一下，应该是 mcp.sdk 有问题，但升级到最新的话和 SpringAI 又有冲突，随后我们单独开一篇来讲吧，就放到面试篇里，算是一个非常经典的面试题：**请讲讲求职派项目中你遇到的难题，如何解决的？**

### 3.体验优化:个人主页新增mcp配置
到此为止，求职派的 MCP Server 就算是完成了，接下来就是体验上的优化了。


从我个人的角度出发，MCP Server 的 json 配置对于刚开始接触的小伙伴来说，有点懵逼，这个json怎么来的？为什么要配置这些信息？


求职派作为一个成熟的产品，当然要以最大程度减小用户门槛为己任，所以我们在用户的主页上，添加了一个一键拷贝 MCP Server 配置的功能。

#### 步骤一：调整后端用户接口，新增mcp配置返回

![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753701963997-65bb1ec7-443f-4269-a060-0e0fa5d7d756.png)

#### 步骤二：前端新增MCP配置
进入个人信息页，点击【MCP 配置】，复制出来就可以了。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753701838401-ecf8d0bf-fbbf-47a8-8b0e-cd1fc7aa064b.png)


## 三、小结
这篇文章和求职派 MCP Server 的实现几乎是同步进行的，也就是说，文中展示的步骤基本就是我当时真实的开发流程。中间虽然夹杂了一些别的事，时间线可能看起来不够连贯，但不影响整体思路，请大家忽略这些“时空错位”的小细节。


希望通过这篇文章，大家能对 MCP 的开发有更直观的理解和一点收获。文章中展示的求职派 MCP 实现还算是一个早期版本，结构简单，功能也比较基础。真正落地到生产环境后，整个工程肯定会持续迭代和优化。但这篇文章无法实时跟进更新，因此我已经贴心地在项目仓库里打了一个 tag，大家可以对照查看对应实现，地址在这里：

👉 [https://github.com/liuyueyi/JobClaw/releases/tag/0.0.2](https://github.com/liuyueyi/JobClaw/releases/tag/0.0.2)


如果大家对MCP有更多想要了解的，推荐看看 [✅️SpringAI知识点系列说明](https://www.yuque.com/itwanger/yyt72l/eullibm4xdohc20e) 如果有想要交流的，欢迎评论or微信call我


如果你对 MCP 有更深入的兴趣，推荐去看看我写的 [✅️《SpringAI 知识点系列说明》](https://www.yuque.com/itwanger/yyt72l/eullibm4xdohc20e)。


当然，如果你在阅读或实现过程中遇到问题、想聊聊心得，也欢迎评论区留言或者直接微信 call 我，一起交流。


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760011743058-f386110a-6ccd-41af-88b9-35d715e07eb5.png)

