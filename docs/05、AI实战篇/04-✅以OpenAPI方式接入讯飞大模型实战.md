在 [✅求职派自定义实现讯飞大模型接入实战](https://www.yuque.com/itwanger/yyt72l/xkgxgln083t73gw4) 这一篇，我们演示了如何通过实现 SpringAI 的 `ChatModel`接口来实现大模型的交互流程；其核心就在于实现 `public ChatResponse call(Prompt prompt) {}`方法，在这个方法内部实现大模型的参数封装、接口调用、返回适配逻辑等。


接下来我们再来看一下，对于符合OpenAI接口交互的大模型，如何进行快速接入。

# 一、基础配置
## 0.查阅官方文档，判断是否支持OpenAI接口
在讯飞的官方接口文档中，可以明确看到说兼容 OpenAI 的，这是基础前提。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1756192057922-dc69fc00-5e99-4056-aca7-06cab6d63856.png)

## 1.申请APIKey
关于讯飞大模型的申请，请参照 [✅求职派大模型-星火SparkLite接入](https://www.yuque.com/itwanger/yyt72l/mk1l8vauyvtw13kw)

## 2.引入依赖
要利用OpenAi的starter来实现讯飞的LLM调用，需要先保障求职派的服务依赖中，有正确引入openai-starter。

```xml
        <!--  openai大模型     -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
```


## 3.添加AI配置
在`application-ai.yml`配置文件中，添加上讯飞和OpenAi的apiKey

:::success
说明：即便没有实际引用openAi，也需要配置对应的ApiKey，否则项目会启动失败

:::


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1756193022232-8828cfe9-5c65-4d9b-8fae-8637b1bca0f7.png)


这里依然沿用，自定义实现讯飞大模型接入中的配置，在此基础上新增了 `openai-base-url`，用于记录OpenAI-Starter进行交互的baseUrl。


:::danger
重点注意：为什么配置中，和官方接口文档的url不一致，少了 /v1 ?

原因：OpenAi的实际实现中，自动补了前缀 /v1，如下图，如果直接使用官方文档的url地址，就会报404哈


![](https://github.com/liuyueyi/spring-ai-demo/raw/master/docs/static/15-2.webp)

:::

# 二、讯飞模型集成
上面配置完毕之后，接下来需要通过OpenAi接口手动初始化讯飞的ChatModel。

## 1.讯飞模型初始化
将之前实现的 SparkLiteModel 调整为手动声明


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1756192857308-c95f891b-b16b-4d5e-8745-9de5215e01cb.png)

新增一个讯飞配置类，用于定义讯飞的ChatModel。

+ 根据配置参数 spring.ai.spark.openai-client 来决定到底用OpenAI的Client，还是使用我们自定义实现的Client进行交互。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1756193119771-12455ab7-ccb5-4d6c-8fb7-d2a8c45981c4.png)

## 2.求职派LLM使用适配
在 [✅求职派多模型集成实战](https://www.yuque.com/itwanger/yyt72l/rpk9m0bp2l9i94n6) 中我们提取了一层求职派的LLM调用抽象类，以方便更好的为求职派提供上层业务调用；因此这里还需要针对讯飞的求职派模型 `SparkOcChatModel`做一些针对性的改造。


具体就是对于注入的ChatModel，由之前写死的 `SparkLiteModel` 改为 `ChatModel`，如下。


![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1756193361316-186c8019-472a-4b4b-92e2-e1674e6ede28.png)

# 三、实测 &小结
改造完成之后，我们可以实际测试下效果，看看讯飞大模型是否可以正常工作；如下，直接上传csv文件让讯飞模型提取校招信息。


![](https://cdn.nlark.com/yuque/0/2025/gif/35158118/1756193911482-473a07a4-4a21-4441-94ce-ad48be9937c1.gif)


本文内容相对简单，重点知识就一个，如果需要接入的大模型官方没有提供 starter，那就看看它支不支持OpenAI的兼容风格，如果支持完全可以直接使用OpenAI的starter来实现大模型交互。


（除了本文介绍的讯飞、阿里的百炼同样也支持OpenAI的方式调用哦）


