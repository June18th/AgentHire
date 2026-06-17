# 01-✅求职派Spring AI集成实战

这一篇将以智谱清言大模型为例，介绍求职派是如何借助SpringAI来进行 AI 应用开发的。


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1758335723638-8d096c89-5c91-4d79-9dcc-98b2ed28ac06.png)

## 一、前置准备
### 1.申请ApiKey
到大模型后台进行申请，智谱的申请请看 [✅求职派大模型-智普清言接入](https://www.yuque.com/itwanger/yyt72l/bkb31aukyil47osx)

星火大模型接入的请看 [求职派大模型-星火SparkLite接入](https://www.yuque.com/itwanger/yyt72l/mk1l8vauyvtw13kw)

### 2.项目配置
对于智谱，官方提供了starter，可以很方便集成（星火麻烦一点）

+ 官方接入教程：[ZhiPu AI Chat :: Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/chat/zhipuai-chat.html)


在pom.xml中添加依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-zhipuai</artifactId>
</dependency>
```


在配置文件 application-ai.yml 中，添加ai配置

```yaml
spring:
  ai:
    zhipuai:
      # https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys
      # api-key 使用你自己申请的进行替换；如果为了安全考虑，可以通过启动参数进行设置
      api-key: ${zhipuai-api-key}
      chat:
        options:
          model: GLM-4-Flash
```


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753781086299-3c1dc972-9536-48fe-b089-7f7df834984a.png)


## 二、SpringAI集成
### 1.ChatModel注入
对于智谱而言，引入依赖之后，会自动注入 `ZhiPuAiChatModel` 。因为我们与大模型的交互除了文本之外，还有图片，因此会使用两个模型：

+ 文本模型：GLM-4-Flash
+ 图片模型：GLM-4V-Flash


我们可以基于这个Model，创建两个ChatClient，分别用于文本和图片的交互；对应的代码实现在 com.git.hui.offer.gather.service.ai.AiModelFacade 中。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753781455473-610badf9-5f4d-4fea-a917-cf8f51b30403.png)


### 2.系统提示词
因为我们与模型的交互目的很纯粹，就是让大模型从我们从输入的信息中提取需要的信息，所以可以预设这样的系统提示词。

```java
public static final String SYSTEM_PROMPT = """
        你现在是一个专业的数据挖掘者，可以从我提供给你的文本内容、表格文件、html文本中获取用户希望的信息；
        如果我给你的是一个http链接，则借助function tool crawlerHttpTable从链接对应的网页中找到表格元素返回给用户希望的信息
         """;
```


然后在创建ChatClient 的时候，通过 `ChatClient.builder().defaultSystem()`设置系统提示词，这样每次与大模型交互时，都会携带上这段提示词。


### 3.大模型交互
我们这里以最基础的文本交互为例，让大模型按照我们希望的方式进行数据提取，一个非常重要的点，大模型怎么知道提取什么数据呢？


这时候可以借助大模型的结构化输出能力，指定大模型的返回格式。好，我们来定义一个希望大模型返回的业务实体。

```java
public record GatherOcDraftBo(
        String companyName,         // 公司名称
        String companyType,         // 公司类型
        String jobLocation,         // 工作地点
        String recruitmentType,     // 招聘类型
        String requirementTarget,      // 招聘对象
        String position,            // 岗位(大都不限专业)
        String deliveryProgress,   // 投递进度
        String lastUpdatedTime,     // 更新时间
        String deadline,            // 投递截止
        String relatedLink,         // 相关链接
        String jobAnnouncement,     // 招聘公告
        String internalReferralCode,// 内推码
        String remarks             // 备注
) {
}
```


直接用 ChatClient的 `entity`指定返回：


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753781823838-8638d8bf-1c3f-4c02-8644-832471136224.png)

```java
public List<GatherOcDraftBo> gatherByText(String text) {
    ArrayList<GatherOcDraftBo> list = this.aiModelFacade.getChatClient().prompt(text)
            .tools(new CrawlerTools()) // function calling的后文会说，这里先无视
            .call()
            // 注意下面很关键，指定了结构化返回
            .entity(new ParameterizedTypeReference<ArrayList<GatherOcDraftBo>>() {});
    return list;
}
```

到这里，SpringAI与大模型的交互就算是完成了（如此简单，是不是有点不敢相信了~）

### 4.业务层调用
有了前面 GatherAiAgent 与大模型交互的基础，现在我们来新建一个统一的任务处理服务类 `OfferGatherService`。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753782118209-9c56b73b-1bb0-488b-bb53-5a46cd46f776.png)

（说明：在这个实现里，我们加了一个兜底，如果传入的字符串为空，则使用默认的输入文本-- 实际生产时应该直接忽略这个任务）

### 5.测试验证
接下来我们实际体验一下，看看效果。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753782251869-112b13f6-cdec-411b-b681-f5042570ccd0.png)

请求图


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753782306564-3081e71c-3a6e-421c-b9f7-a6b19d0d4151.png)

大模型的结构化返回


## 三、小结
本文主要介绍了求职派集成 SpringAI，整体实现比较简单，需要特殊说一下的是，并不是所有的大模型，都有直接可用的 `starter`，对于没有的，需要我们按照SpringAI的规范进行扩展。


当然接入只是第一步，之后还有很多工作要做，比如大模型的返回结果会被截断，这个问题就搞了好久，这也是一个难点（😭）。


