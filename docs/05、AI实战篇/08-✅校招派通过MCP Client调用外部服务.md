这篇文章将介绍校招派通过MCP调用`<font style="color:#000000;background-color:#c7edcc;">selenium</font>`来实现网页数据采集，从而增强校招派的采集目标范围。



<!-- 这是一张图片，ocr 内容为：FEATURES START BROWSER SESSIONS WITH CUSTOMIZABLE OPTIONS NAVIGATE TO URLS FIND ELEMENTS USING VARIOUS LOCATOR STRATEGIES CLICK,TYPE,AND INTERACT WITH ELEMENTS PERFORM MOUSE ACTIONS (HOVER,DRAG AND DROP) HANDLE KEYBOARD INPUT TAKE SCREENSHOTS UPLOAD FILES SUPPORT FOR HEADLESS MODE SUPPORTED BROWSERS CHROME FIREFOX MS EDGE -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760105470193-f12510cf-b62b-406c-a9a4-114b3a4348e1.png)

# 一、MCP Client
首先，我们需要找一些可用的MCP Client，到哪里找呢？

## 1.MCP服务市场
下面推荐部分可用的市场（无广告成分，大家按需获取即可），可以自助搜索自己需要的mcp server

+ [GitHub - modelcontextprotocol/servers: Model Context Protocol Servers](https://github.com/modelcontextprotocol/servers?tab=readme-ov-file#%EF%B8%8F-official-integrations)
+ [https://mcpmarket.com/zh](https://mcpmarket.com/zh)
+ [大模型服务平台百炼控制台](https://bailian.console.aliyun.com/?tab=mcp#/mcp-market)
+ [Cursor Directory](http://cursor.directory/mcp)
+ [腾讯云开发者社区-腾讯云	开发者 MCP广场_开发者MCP服务_MCP 服务器- 腾讯云](https://cloud.tencent.com/developer/mcp)



## 2.MCP Server验证
这里选择的MCP Server为 <font style="color:#000000;background-color:#c7edcc;">mcp-selenium</font>，对应的源码在：[https://github.com/angiejones/mcp-selenium](https://github.com/angiejones/mcp-selenium)

接下来我们利用 Trae 来验证一下这个mcp server的表现情况。



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754445276557-5df28bbf-163c-472c-af39-5f68edf840a9.png)

然后我们直接使用这个MCP Server 来看看表现情况

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754445421843-d20fa644-045c-4d46-a521-dca039328a8d.png)

大模型分析过程

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754445456176-b4077b6e-c3d8-48b8-bedd-4e3359cb60cd.png)

大模型调用MCP Server的执行过程

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754445575636-504caa50-1834-4295-9420-e5a0351c0d1e.png)

结果处理返回



<!-- 这是一张图片，ocr 内容为：IDE SEARCH RESTART TO UPDATE TEST {]MCP.JSON X TRAE TRAE>USER>{)MCP.JS APPLICATION SUPPORT > ITWANGER> LIBRARY> MCPSERVERS": 2 ITWANGER GITHUB": 26 IT 这篇文章的标题和简介,以 帮我获取技术派上: HTTPS://PAICODING.COM/ARTICL... ], 31 MARKDOWN 格式返回给我 32 "ENV": 33 GITHUB_PERSONAL_ACCESS TOKEN":" BUILDER WITH MCP 34 USED 1 CONTEXT "FROMGALLERYID":"MODELCONTEXTPROTOCO 35 子, 36 派聪明RAG项目发布俩月,已成功帮助1400+球友拿到OFFER-技术派 "校招派": 37 品 38 TYPE":'SSE", 我来帮您获取技术派上这篇文章的标题和简介.让我先查看一下网页内容. "URL":"HTTP://LOCALHOST:8087/API/SSE 39 SELENIUM/START BROWSER "VERSION":"1.0", 40 "HEADERS":{ 41 ARGUMENTS BEARER DEMOUSER 42 AUTHORIZATION": "BROWSER':'CHROME" ? 43 OPTIONS":{ 44 'HEADLESS':TRUE 45 SELENIUM": 子 "COMMAND":"NPX", 46 "ARGS":[ 47 RESPONSE 48 -Y" BROWSER STARTED WITH SESSION_ID: CHROME_1760104174980 49 "@ANGIEJONES/MCP-SELENIUM" 50 51 子 52 @BUILDER WITH MCP 53 YOU ARE CHATTING WITH BUILDER WITH MCP NOW DEEPSEEK-V3.1 LN 38,COL 27 SPACES:4 UTF-8 LF {& JSON WITH COMMENTS HENTS *CUE 042四 -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760105397594-e21c0076-2d71-4c93-9a4f-f43982c639c7.png)



从上面的整体表现来看，效果还是挺不错的，大模型通过多次调用MCP Server下达指令，从而实现目标数据的获取；接下来我们看一下，如何在校招派中使用MCP Server。



<!-- 这是一张图片，ocr 内容为：口 RESTART TO UPDATE IDE SEARCH TEST TRAE ITWANGOR LIBRARY APPLICATION SUPPORT TRAE TRAER USER "MCPSERVERS" ITWANGER HER回 THHM的GENGGENGLEDENGDENGL GITHUB LT 帮我获取技术派上:#HTTPS://PAICADING CAM/ARTICT... 这篇文章的标题和简介,以 MARKDOWN 格式返回给我 "ENV: "GITHUB_PERSONAL_ACCESS_TOKEN*: BUILDER WITH MCP USED I OONTEXT "FROMGALLERYID":"MODELCONTEXTPROTOCO 派聪明RAG项目发布俩月,已成功帮助1400+球友拿到AFFER-技术活 "校招派": 我来帮您获取技术涨上这篇文章的标题和简介.让我先查看一下网页内容. "TYPE":SSE", "URL":"HTTP://LOCALHOST:8087/API/SSE SE ENIUMFSTART BROWSER A "VERSION': "1.0.0", "HEADERS": ARGUMENTS AUTHORIZATION :"BEARER DEMOUSER 'BROWSER': CHRONE' 'OPTIONS":{ "HEADTESSTRUE 'SELENIUM: "CONMAND:"NPX*, "ARGS": RESPONS业 BROWSER STARTED WITH SESSION_ID:CHROME_1760104174980 "@ANGIEJONES/NCP-SELENIUM* @BUILDER WITH MCP % YOU ANE CHATTING WITH BULIDER WITH MCP NOW DEEPSEEK-Y3.1 042 0 COL 27 SPACES:4 UTF-E LF (A JSON WITH COMMENTS 米CUE LN 38.COL 27 -->
![](https://cdn.nlark.com/yuque/0/2025/gif/12564477/1760106287198-fe4dd62c-9ce0-4c69-8217-e20fb1a2038a.gif)



# 二、校招派使用MCP Server
## 1.依赖配置
首先在pom.xml中添加 MCP Client 的依赖。

```xml
  <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-mcp-client</artifactId>
  </dependency>
```



## 2.MCP Server配置
然后在配置文件中，指定MCP Server的配置信息；创建一个`mcp-servers.json`文件，放在 `resources/ `目录下

```json
{
  "mcpServers": {
    "selenium": {
      "command": "cmd",
      "args": [
        "/c",
        "npx",
        "-y",
        "@angiejones/mcp-selenium",
        "D:/"
      ]
    }
  }
}
```



:::success
说明：为什么这个配置json和trae中的json配置不一样呢？

原因：SpringBoot1.0.1 有个bug，导致在win环境中，无法正确启动服务；源码上有 [https://github.com/spring-projects/spring-ai/issues/3099](https://github.com/spring-projects/spring-ai/issues/3099) 这个issue，对应的解决方案是启动命令的调整



对于mac/linux系统的小伙伴，如果上面的配置导致无法正确启动，请将配置改成下面这种



{

  "mcpServers": {

    "selenium": {

      "command": "cmd",

      "args": [

        "npx",

        "-y",

        "@angiejones/mcp-selenium"

      ]

    }

  }

}

:::



然后在配置文件中，指定 mcp-client

```yaml
spring:
  ai:
    mcp:
      client:
        #  使用本地的mcp server，通过进程通信，我们这里演示的是使用 selenium 来实现网页数据抓取
        stdio:
          servers-configuration: classpath:mcp-servers.json
        enabled: true    # 注意，仓库中，默认是false，如需要体验，请将这里改成true
        name: 校招派MCP
        version: 1.0.0
        request-timeout: 30s
        type: async

```

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754446711848-1bc1b264-2013-4009-a787-7d7e6135e7bd.png)



## 3.MCP Client注入为大模型默认工具
校招派现在提供了两个免费的大模型，智谱 + 讯飞Lite，不过只有智谱支持Function Calling，也就是说，如果想要体验MCP Server，就只能选择智谱模型（如果选择氪金的话，所有的大模型厂商的主流模型都是支持Function Calling的）

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754447411947-f3c36f46-6d7e-44eb-bc6a-7872b47c0c53.png)

```java
public ZhiPuOcChatModel(ZhiPuAiChatModel zhiPuAiChatModel, List<McpAsyncClient> mcpClients) {
    this.zhiPuAiChatModel = zhiPuAiChatModel;

    chatClient = ChatClient.builder(zhiPuAiChatModel)
            .defaultSystem(GATHER_SYSTEM_PROMPT)
            .defaultOptions(ChatOptions.builder().stopSequences(Collections.emptyList()).build()) // 取消默认停止符
            .defaultAdvisors(new SimpleLoggerAdvisor())
            // 将MCP Client 注册为工具
            .defaultToolCallbacks(new AsyncMcpToolCallbackProvider(mcpClients))
            .build();

    // 图片理解模型，省略...
}
```

亮点说明：

1. 直接注入 `List<McpAsyncClient> mcpClients`，因为我们配置的是异步使用mcp server的方式，因此这里拿的也是异步的Client；对于同步的场景，可以注入 `List<McpSyncClient>`
2. 将MCP Client封装为 `McpToolCallbackProvider`传入MCP Model，作为大模型的默认工具回调

## 4.使用层适配
上面改完之后，照理我们传入网页链接后，大模型应该能正常通过 MCP Server 抓取网页内容并返回。但实际测试的表现和预期不一致，主要原因有两点。  


第一，**核心原因在于工具注册的对象不一致**。



网页获取的逻辑我们是通过 ChatModel 来实现的多轮对话，用这种方式是为了避免模型响应被截断的问题。然而在默认注册工具时，我们注册的对象是 ChatClient。这就导致虽然代码逻辑跑起来了，但实际工具并没有真正被注册到当前的会话上下文中，大模型自然也就无法正确调用。换句话说，它能“聊天”，但不知道自己有网页抓取这个“工具”。  


第二，**工具间存在冲突**。



我们在给大模型配置工具时，同时提供了一个基于 **Jsoup** 的网页抓取工具，用于直接解析 HTML。而 MCP Server 这边又通过 **Selenium** 实现了网页访问与内容提取，这就带来了“冲突问题”——两个工具都能完成类似任务，但模型无法明确判断该调用哪个。由于调用策略是由模型自身决策的，我们没法保证它每次都会选中预期的那一个。  


所以在测试阶段，建议只保留其中一种工具：



要么仅使用 MCP Server 的 Selenium 实现，保证模型请求都能走 MCP 链路；

要么暂时移除 MCP Server，直接用 Jsoup 方案做本地验证。



这样可以排除工具冲突的干扰，更容易定位问题到底出在注册流程、模型上下文，还是工具调用路径上。



基于此，我们对大模型的应用层，做一个针对性的适配

### step1: 传入文本校验逻辑放开
<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754449041023-8456e0ff-67b7-4fe5-bab4-e6621ed4bdbb.png)

修改之后，我们的输入可以如下

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754448973402-e87188e0-7a15-48dd-87d7-66dadf18896b.png)

### step2: 工具注册
```java
/**
 * 给大模型使用的工具提供类
 */
private ToolCallbackProvider toolCallbackProvider;

/**
 * 将MCP Server注册大模型的回调工具
 *
 * @param mcpClients
 */
@Autowired(required = false)
public void setToolCallbackProvider(List<McpAsyncClient> mcpClients) {
    if (!CollectionUtils.isEmpty(mcpClients)) {
        // mcp server provider
        this.toolCallbackProvider = new AsyncMcpToolCallbackProvider(mcpClients);
        log.info("----> 将 MCP Server 注册为大模型的回调工具 <-----");
    }
}

/**
 * 没有MCP Server时，使用本地的工具作为大模型的回调工具
 */
@PostConstruct
public void initLocalToolCallback() {
    if (this.toolCallbackProvider == null) {
        this.toolCallbackProvider = new StaticToolCallbackProvider(ToolCallbacks.from(new CrawlerTools()));
        log.info("----> 将应用的 CrawlerTools 注册为大模型的回调工具 <-----");
    }
}
```

然后在使用层，做一个小小的改造

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754449553740-21a3ab84-ce1c-4f7d-8ea8-95a0423b4320.png)



## 5.使用测试
然后启动应用，注意将 `application-ai.yaml`配置文件中` spring.ai.mcp.client.enabled = true`； 然后再提交一个http链接获取的任务看看执行效果

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754449172240-ff8596b7-cda0-40b8-83ca-98d0d8ebd824.png)



怎么判断有没有使用MCP Server呢？

+ 执行过程中，会突然启动浏览器

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754449743922-8343a2ed-3167-432c-8df8-814d9bfdc367.png)

+ 执行完毕之后，可以正确解析到结果

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1754449809095-876952f5-3afe-4d9b-8db0-eca20d4a67f3.png)



一灰那边是 Windows 电脑，他在运行是没有问题，我这边是 macOS，并且是 test 模式运行，出错了。



<!-- 这是一张图片，ocr 内容为：调试 AIOCAPPLICATION 线程和变量 骨环境 运行状况 I0企业上ON 映射 R 控制台 BEAN FAILED TO START PROCESS WITH COMMAND: [CMD, NPX,-Y, QANGIEJONES/MCP-SELENIUML CAUSED BY:JAVA.LANG.RUNTIMEEXCEPTION CREATE BREAKPOINT `LINGMA- AT 10-BODELCONTEXTPROTOCOL-CLIENT, TRANSPORT-STAIOCLIENTTRANSPART,LANBDASCONNECTSZ (STDIANSRONSROCT,  SUPPRESSED: REACTOR.CORE.PUBLISHER.ELUXONASSEMBLY$ONASSEMBLYEXSERTION: 不 ASSEMBLY TRACE FROM PRODUCER [REACTOR.CORE.PUBLISHER.MONOSUNSGRIHEANGALLABLE] 0G REACTOR.CORE.PUBLISHER.MONO.SUBSCRIBEON(MONO.JAVA:4625) IO.NODALCONTEXTPROTOCOL.CONNECTIENTTRANSPORT,STDIOCLIENTTRANSPORT,CONNECT(STDIOSLIENTTRANSPART,JAVA;1 ERROR HAS BEEN OBSERVED AT THE FOLLOWING SITE(S): ORIGINAL STACK TRACE: AF 10:NOURJONTERTERTPROTANBAST, ERENSPORT,SEDTERT RONEPORT, JARBASROONNEETJ AT JAVA.BASE/JAVA.UTIL.CONCURRENT.FUTURETASK.RUN(FUTURETASK.JAVA) <4 个内部行> AT JAVA.BASE/JAVA.LANG.PROCESSBUILDER.START(PROCESSBUILDER.IAVA:1143) AT JAVA.BASE/JAVA.LANG.PROCESSBUILDER.START(PROCESSBUILDER.JAVA:1073) 29)(7个内部行> AT IO,RODELCONTEXTPROTOCOL.CLLENT, TRANSPORT,STAIOCLIONTTRONSPART, LANBDASCONNECTSIGIGNTTRANEPART,JAV AT JAVA.BASE/JAVA.UTIL.CONCURRENT.FUTURETASK.RUN(FUTURETASK.JAVA) <4 个内部行> CAUSED BY: JAVA.IO:IDEXCENTION CREATE BREAKPOINT & LNGMA+: ERROR:Z, NO SUCH FILE OR DIRECTORY AT JAVA.BASE/JAVA.LANG.PROCESSIMPL.FORKANDEXEC(NATIVE METHOD) AT JAVA.BASE/JAVA.LANG.PROCESSIMPL.<INIT>(PROCESSIMPL.IAVA:314) AT JAVA.BASE/JAVA.LANG.PROCESSIMPL.START(PROCESSIMPL.JAVA:244) AT JAVA.BASE/JAVA.LANG.PROCESSBUILDER.START(PROCESSBUILDER.JAVA:1110) ..14 COMMON FRAMES OMITTED 33:14 OAPP > SRC >MAIN > RESOURCES-ENV > TEST > APPLICATION-AI.YML -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760108948627-23fd65b1-7b47-41f8-b671-e76cb2ac7e9a.png)



就是因为我在配置 mcp-servers.json 的时候错误地使用了 Windows 的方式，这个大家一定要注意。记得一定要修改为 macOS 的方式，如下。



<!-- 这是一张图片，ocr 内容为：AO AI-OC MAIN 项目 TEST/MCP-SERVERS.JSON OCMCPSERVICE.JAVA MY IMA PAY.YML SRC 23456- MAIN "MCPSERVERS": { "SELENIUM": { JAVA "COMMAND": "NPX", RESOURCES "ARGS": [ DATA O "-Y". DB.CHANGELOG 1 7 "@ANGIEJONES/MCP-SELENIUM" STATIC 8 APPLICATION.YML 6 LOGBACK-SPRING.XML "WENYAN-MCP-发布微信公众号": "COMMAND": "NODE"....予 10 RESOURCES-ENV 20 DEV 21 APPLICATION-AI.YML APPLICATION-DAL.YML APPLICATION-OC.YML APPLICATION-PAY.YML MCP-SERVERS.JSON PROD TEST APPLICATION-AI.YML APPLICATION-DAL.YML APPLICATION-OC.YML APPLICATION-PAY.YML }MCP-SERVERS.JSON TEST -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760109274373-7bff0967-fb99-4466-be2c-4a50f07612e8.png)



如果确认没有问题的话，重新启动项目，然后在录入数据这里重跑一下之前的任务列表就好了。



<!-- 这是一张图片，ocr 内容为：校招派 管理后台 职位录入 校招派AGENT 录入数据 任务列表 AI录入 草稿列表 职位列表 任务ID 任务状态 抓取类型 模型 查询 字典管理 操作 AI 结果 类型 输入 创建时间 处理时间 更新时间 状态 模型 2 用户管理 重跑 2025-10-10 22:55:30  2025-10-10 23:17:32 请提取网页HTTPS://OFFER.GF... 2025-10-10 23:17:32 智谱清言 处理中 SUCCESS HTTP链接 2025-10-09 11:17:52  2025-10-09 11:35:03 2025-10-09 11:34:31 插入:1,2,3,4,5,6 智谱清言 图片 重跑 图片 已处理 券码管理 下一页 第1/1页 上一页 -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760109542472-0524fe3f-0474-4ac4-97ae-a4d4b91a6a71.png)

如果没有问题的话，会启动一个 chrome 浏览器，并且提醒 chrome 正收到自动测试软件的控制。见下图。

<!-- 这是一张图片，ocr 内容为：校园招聘-OFFER星球-最新校招 OFFER.GFJIANLI.COM X CHROME 正受到自动测试软件的控制. 升级会员 校招信息 OFFER星球 登录/注册 投递进展 内推企业 26届毕业生1300万!校招很卷,但不要输在校招信息差上! 7000+用户的选择花小钱办大事,高峰期每天更新100+校招信息,1W+知名企业校招信息追踪! 每日更新 电脑端体验更佳 秋招进度管理 搜索公司/岗位/地点/行业/备注 日期筛选 刷新 选择求职进度 近三日更新数量 近七日更新数量 累计更新数量 11,526 134 272 实时更新 每周统计 持续增长中 工作地点 公司 投递方式 行业 岗位 求职进度 备注 壹号食品 点击添加备注 全国 校园招聘正式启动! 点击投递 营销管培生(专业不... 其他,消费 点击添加备注 仲利国际 融资租赁客户经理 届校园招聘正式启航! 北京,天津,石家庄... 点击投递 银行/金融 占丰添加名注 公司2026校园招聘正... 上海,重庆,海外 钢铁工艺工程师机械... 中冶赛迪 点击投递 国企,其他 温馨提示 北京市建筑设计 点击添加备注 建筑设计景观设计室 北京 点击投递 |未来|北京建院2026... 国企 研究院 国浩律师事务所    |2026届国浩校... 点击添加备注 北京,天津,石家庄... 点击投递 律师助理实习生 专业服务 -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760109523445-121f02b8-7db4-410b-a9ca-ef3d3fccf2b2.png)

# 三、小结
本文主要介绍了如何通过 **Spring AI** 集成使用 **MCP Server**。整体体验其实出奇地简单：引入依赖、自动注入几个 Client、传给大模型，流程基本就跑通了。从应用层开发者的角度来看，使用 MCP 的门槛非常低，几乎感受不到额外负担。



当然，集成容易并不意味着能自动“用好”。MCP 只是让模型与外部系统之间的沟通变得更标准、更通顺，而最终能否发挥它的真正价值，还是要看业务侧的设计与实现逻辑。就像我常说的那句：**工具是中性的，聪明的是人。** 😊



文中所有涉及到的改动，可以通过提交 [feat: 集成MCP Server提供网页数据抓取 · liuyueyi/ai-oc@35d0172](https://github.com/liuyueyi/ai-oc/commit/35d01722e4bc3e95fd6b7f3b3a958ea49b5e111a) 进行查看；若有需要交流讨论的，欢迎评论区给出。









