# 02-✅求职派实现Function Call

上一篇我们实现了大模型的接入，已经可以提取文本信息了；接下来，我们希望传一个 http 链接，让大模型帮我们抓取对应网站的数据。


不过，很遗憾的是免费的大模型，并不支持爬取网络信息，所以我们考虑使用 Function Call 机制来辅助它。


![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1758336940659-f49c842e-fda4-4ef6-8bc4-a1727ed0083a.png)

## 一、Function Call
不是所有的大模型都支持 function call，幸运的是智谱的免费大模型是支持的。

### 1.网页抓取实现
既然大模型没有网页抓取的能力，那我们就自己实现一个；对于静态网页，我们可以通过 jsoup 来实现，动态网页抓取则使用 hutool。


在 pom.xml 中添加依赖

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-core</artifactId>
    <version>5.8.38</version>
</dependency>
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-http</artifactId>
    <version>5.8.38</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.3</version>
</dependency>
```


网页提取的具体实现如下，提取网页中的表格，然后将表格内容全部返回。

```java
public String crawlerHttpTable(String url) {
    log.info("开始获取表格内容: {}", url);
    String text = HttpUtil.get(url, CharsetUtil.CHARSET_UTF_8);
    Document document = Jsoup.parse(text);
    Element table = document.select("table").first();
    String ans = table.html().trim();
    if (log.isDebugEnabled()) {
        // 一行打印
        log.debug("获取到的表格内容为：{}", ans.replaceAll("\n", ""));
    }
    return ans;
}
```


### 2.注册Function Tool
工具实现后，我们需要将它注册为大模型可以调用的工具，这里主要借助 `@Tool`注解来实现。

```java
/**
 * 提供给大模型的 function tools
 */
public class CrawlerTools {
    /**
     * 获取http地址中的表格
     * <p>
     * 说明：即便我给大模型的是一个http链接，但是无法保证大模型每次都会触发调用这个方法(😂)
     *
     * @param url
     * @return
     */
    @Tool(description = "输入一个http链接，返回这个http链接对应的网页中的表格内容")
    public String crawlerHttpTable(@ToolParam(description = "http格式的url地址") String url) {
        log.info("开始获取表格内容: {}", url);
        String text = HttpUtil.get(url, CharsetUtil.CHARSET_UTF_8);
        Document document = Jsoup.parse(text);
        Element table = document.select("table").first();
        String ans = table.html().trim();
        if (log.isDebugEnabled()) {
            // 一行打印
            log.debug("获取到的表格内容为：{}", ans.replaceAll("\n", ""));
        }
        return ans;
    }
}
```

### 3.Function Call 调用
```java
// 传入数据太长，导致解析的结果被截断的场景时，转用下面的 gatherByAutoSplit 调用方法
public List<GatherOcDraftBo> gatherByText(String text) {
    ArrayList<GatherOcDraftBo> list = this.aiModelFacade.getChatClient().prompt(text)
            .tools(new CrawlerTools())
            .call()
            .entity(new ParameterizedTypeReference<ArrayList<GatherOcDraftBo>>() {
            });
    return list;
}
```

对于ChatClient，直接通过 `.tools`注册工具，传参为包含`@Tool`注解方法的类实例。


因为网页访问时，返回的数据太大，会被大模型截断；因此我用 ChatModel 做了一个多轮的对话实现，对应的工具注册代码在 `com.git.hui.offer.gather.service.GatherAiAgent#autoContinueChat`中，通过 `ToolCallback.from(工具类实例)`来注册工具。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753783368554-f7ff5a88-4727-4084-9856-bc31da8462d4.png)

### 4.开启日志，验证结果
在配置文件 `application-ai.yml` 中，打开ai交互日志，方便观察过程（大模型交互通过SimpleLoggerAdvisor 实现的日志输出）

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor: debug
    com.git.hui.offer.gather.service.GatherAiAgent: debug  # 开发时，输出debug调试日志，用于显示与大模型交互的输入/返回
```


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753783879419-be5c0863-34d8-42f5-87c6-1c81a9e7a850.png)

日志提现了function calling的执行


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753784032243-34d30fbf-bb1d-48a6-b32e-274e5558ac93.png)

大模型提取的结果

## 二、小结
这一篇主要介绍了求职派如何通过 Function Call 来给大模型进行赋能。


当模型本身不具备某项能力时，我们可以通过这种工具调用的方式进行扩展。


当然这里实现的比较简单，对于网页抓取，由于我们的目标网站是静态网页，可以使用 jsoup 搞一下；对于 js 渲染的页面，就需要考虑无头浏览器了，这个后续有机会再给大家介绍。


