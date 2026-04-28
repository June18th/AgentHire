星火大模型提供了一个免费的 Lite，可以提供基本的文本处理，我们也将这个模型集成在校招派了；接下来介绍一下如何申请集成

# <font style="color:rgb(31, 35, 40);">一、 星火ApiKey申请</font>
<font style="color:rgb(31, 35, 40);">注册、登录账号相关流程省略，请直接在官网自助完成</font>

<font style="color:rgb(31, 35, 40);">进入开放平台:</font><font style="color:rgb(31, 35, 40);"> </font>[<font style="color:rgb(9, 105, 218);">https://console.xfyun.cn/services/cbm</font>](https://console.xfyun.cn/services/cbm)

<!-- 这是一张图片，ocr 内容为： -->
![](https://github.com/liuyueyi/spring-ai-demo/raw/master/docs/static/05-1.webp)

<font style="color:rgb(31, 35, 40);">选择 Spark Lite 模型，上图中因为我已经开通了；对于没有开通的小伙伴，可以看到上图中 </font>`<font style="color:rgb(31, 35, 40);background-color:rgba(129, 139, 152, 0.12);">领取无限量</font>`<font style="color:rgb(31, 35, 40);"> 这个按钮是激活状态，点击之后对于已认证账号即可获取了（未认证的，直接跳转到认账账号进行认证，支持个人/企业认证）</font>

<font style="color:rgb(31, 35, 40);">领取之后，在右边的鉴权信息中，将 ApiPassword 复制出来待用</font>

<font style="color:rgb(31, 35, 40);"></font>

# <font style="color:rgb(31, 35, 40);">二、配置</font>
星火的配置和智谱差不多，同样放在 application-ai.yml 配置文件中

```yaml
spring:
  ai: 
    spark:
      # https://console.xfyun.cn/services/cbm
      # api-key 使用你自己申请的进行替换；如果为了安全考虑，可以通过启动参数进行设置
      base-url: https://spark-api-open.xf-yun.com/v1/chat/completions
      api-key: ${spark-api-key}
      chat:
        options:
          model: lite
```

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753787595694-8a9175e6-2ed3-4066-af7a-c3988fdb9e56.png)



<font style="color:rgb(31, 35, 40);">将你申请的 ${spark-api-key} 替换为你申请的apiKey；或者通过命令行参数进行输入</font>

# 三、使用
在后台使用时，大模型的选择改为 - 讯飞星火

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1753787688026-e0b856da-529e-4106-98be-286ff6dd0da5.png)

注意：免费的大模型不支持图片处理、不支持function calling

