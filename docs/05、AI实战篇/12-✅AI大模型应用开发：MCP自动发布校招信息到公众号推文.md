这一篇我们来借助MCP的能力，实现自动发布校招信息到微信公众号。

# 一、流程设计
当校招派顺利跑起来、进入稳定运营之后，我们就会开始思考一件事——**怎么让这些有价值的数据“动”起来**。



比如说，当有企业正式启动校招、或者某些岗位即将截止报名，我们能不能第一时间推送提醒？又或者，当互助区里有球友新增了内推机会，是不是也能自动同步给关注的同学？  


这些信息其实非常适合通过**微信公众号**来触达。公众号的订阅机制天生适合这种“轻量但高频”的内容分发。



接下来，我就用一个最简单的例子，带大家看看——**如何一键生成一篇微信公众号博文。**



从校招派的数据出发，到最终生成推文，我们将把整个流程跑通，让大模型帮我们把“信息”变成“内容”，真正做到自动化产出。

## 业务流程：
先以最简单的场景来跑通流程为例，比如后台职位管理这里，添加一个 “上新发布公众号” 按钮，点击之后，自动实现基于今天新上架的岗位信息生成公众号文章，然后通过大模型调用MCP，将内容发布到公众号的草稿内。

# 二、实现
## 1.MCP安装
我们这里使用 [https://github.com/caol64/wenyan-mcp/tree/main](https://github.com/caol64/wenyan-mcp/tree/main) 文颜MCP Server。



<!-- 这是一张图片，ocr 内容为：R G.8 TYPEARCH CAOL64/WENYAN-MCP 田PROJECTS PULL REGUESTS SECURITY LNSIGHTS ACTIONS 22 CODE LSSUES G FORK 96 WATCH STAR 869 SPONSOR WENYAN-MCP (PUBLIC) 5 ABOUT GF 1 BRANCH GO TO FILE CODE ADD FILE 3 TAGS MAIN 文颜MCP SERVER 可以让 AI 自动将 MARKDOWN 文章排版后发布至微信公众 CAOL64  FIX:列表无故换行问题.CLOSE #14 F451EEC`8 HOURS  AGO 34 COMMITS OCTOTREE Y 号 FIX:列表无故换行问题,CLOSE #14 GITHUB 8 HOURS AGO MCP-SERVER WENYAN DOCS:ADD DEBUG STEPS 4 MONTHS AGO DATA 中 README CHORE: UPDATE PACKAGE VERSION AND DEPENDENDENCIES; ADD RU.. LAST MONTH SRC 邓 APACHE-2.0 LICENSE DOCS(README):UPDATE PROJECT LOGO AND DESCRIPTION 4 MONTHS AGO ACTIVITY TEST 869 STARS 3 WEEKS AGO DOCKERIGNORE GOOD FOR REVIEW 5 WATCHING 4 MONTHS AGO .EDITORCONFIG FIRST COMMIT 96 FORKS -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760537442328-1a91fb80-618b-484a-b46b-9def22f86945.png)

安装步骤：

```bash
git clone https://github.com/caol64/wenyan-mcp.git
cd wenyan-mcp

npm install
npx tsc -b && npm run copy-assets
```



如果 Git clone 失败，建议直接用 GitHub 桌面版拉取。



<!-- 这是一张图片，ocr 内容为：CURRENT BRANCH CURRENT REPOSITORY FETCH ORIGIN LAST FETCHED 1 MINUTE AGO WENYAN-MCP MAIN HISTORY CHANGES FIX:列表无故换行问题,CLOSE#14 LEI +20-14 0.6 F451EEC NO BRANCHES TO COMPARE 安 GITHUB/WORKFLOWS/PUBLISH.YML 4 CHANGED FILES FIX:列表无故换行问题,CLOSE#14 V1.0.6 @@-111+1,13@@ GITHUB/WORKFL.../PUBLISH.YML LEI 9 HOURS AGO NAME:BUILD AND PUBLISH TO NPM README.MD REARRANGE README.MD TO IMPROVE LOGO AND BADGE VISIBILITY 2 LEI 2 DAYS AGO 3 ON: PACKAGE.JSON 45 RELEASE: MERGE PULL REQUEST #28 FROM LUOJIYIN1987/FIX/NODE-ENV-SET PNPM-LOCK.YAML TYPES:[CREATED] LEI`21 DAYS AGO 4 PUSH: GOOD FOR REVIEW 5 + TAGS: LUOJIYIN 21 DAYS AGO 9 + V* 7 GOOD FOR REVIEW 7 8 PERMISSIONS: LUOJIYIN`21 DAYS AGO 8 CONTENTS:WRITE GOOD FOR REVIEW ID-TOKEN:WRITE 6 LUOJIYIN`21DAYS AGO 10 CONTENTS:READ 11 GOOD FOR VIEW 10 12 JOBS: LUOJIYIN`21DAYS AGO 11 13 NPM-PUBLISH: ADD BLANK LINE @-21,12+23,14@@@ JOBS: LUOJIYIN`21 DAYS AGO 23 NODE-VERSION:'22 22 24 REGISTRY-URL: 'HTTPS://REGIS CONFIG:SET DOCKER BUILD IGNORE TRY.NPMJS.ORG LUOJIYIN`21 DAYS AGO 23 25 FIX:NODE ENV SET 26 # ENSURE NPM 11.5.1 OR LATER IS LUOJIYIN`21 DAYS AGO INSTALLED 27 NAME:UPDATE NPM MERGE PULL REQUEST #27 FROM LUOJIYIN1987/REFACTOR/DOCKER-BUILD 28 INSTALL NNMALATEST RUN: -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760538627248-f2b586ae-6ac4-4a09-a94f-77bdcdf05b48.png)



如果是windows开发的小伙伴，执行上面的 `npm run copy-assets`会报错

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755852473403-56deea9c-a9ae-4c57-94ef-50c0f28ddcac.png)

解决方案也简单：

+ 打开 src 目录
+ 手动将 文件`main.js``mac_style.css` 文件夹 `themes` `highlight` 复制到 `dist`目录下



或者就直接用 warp 一键搞定。



<!-- 这是一张图片，ocr 内容为：使用,比如说5个G CLAUDE HUB/WENYAN-MCP /KAFKA_2.13-3.9.0 沉 OWNLOADS/MINIO1 NGROK ..ENTS/GITHUB/WENYAN-MCP CSS" FIND . -TYPE F -NAME "*.HTML"I QREP-V NODE MODULES '*.JSON 0- -NAME -NAME O- LS -LA LS -LA DATA/ LS -LA DIST/ BASED ON THE COMMAND ERROR AND THE PROJECT STRUCTURE, I CAN SEE THAT THERE'S A DATAL DIRECTORY WITH ASSETS THAT LLKELY NEED TO BE COPIED TO TO THE DIST/ DIRECTORY. LET ME ADD THE MISSING COPY-ASSETS SCRIPT TO PACKAGE.JSON: ADD COPY-ASSETS SCRIPT TO PACKAGE.JSON REVIEW CHANGES V20.16.0 ~/DOCUMENTS/GITHUB/WENYAN-MCP GIT:(MAIN) (0.478S) NPX TSC-B && NPM RUN COPY-ASSETS @WENYAN-MD/MCP@1.0.6 COPY-ASSETS CP-R DATA DIST/ V20.16.0 MAIN ~/DOCUMENTS/GITHUB/WENYAN-MCP LS -LA DIST/ > A @@四 CLAUDE 4.5 SONNET -->
![](https://cdn.nlark.com/yuque/0/2025/png/12564477/1760538864082-89333a9f-5128-4e36-a9d4-132c19c08f1f.png)

## 2.配置MCP
在配置文件中 `application-ai.yml`中开启 mcp

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755857504366-f1fe34ae-e97a-4e2b-b07f-a9c47420ec10.png)

打开校招派的项目，找到 `mcp-servers.json` 文件

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755857460764-1f583897-487c-4bdf-9d8e-40817b9a2b19.png)



```json
{
  "wenyan-mcp": {
      "command": "node",
      "args": [
        "D:\\Workspace\\hui\\web\\wenyan-mcp\\dist\\index.js"
      ],
      "env": {
        "WECHAT_APP_ID": "wx4a128c315d9b1228",
        "WECHAT_APP_SECRET": "使用你的密钥进行替换"
      }
    }
  }
}
```

拷贝上面的配置时，注意修改三个参数：

+ args: 为你在上一步安装 wenyan-mcp 时的路径
+ env: 中为微信公众号的配置信息，在下面的位置进行获取

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755852801691-c9621bb1-4a99-42b3-acf7-1f13f8fbb539.png)

然后在安全中心中，添加ip白名单，用于限定哪些服务器可以获取微信公众号的访问token

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755852882345-1826f1ea-86b0-4307-b9e0-90c5a73ea22f.png)

## 3.后台实现MCP发布逻辑
在这里，我们实现一个每日的岗位快讯，用于同步今天的上新岗位信息，如

```java
/**
 * 微信公众号博文发布Agent
 *
 * @author YiHui
 * @date 2025/8/22
 */
@Service
public class WechatBlogPublishService {
    private final OcAiModelContext ocAiModelContext;

    private final OcService ocService;

    @Autowired
    private List<McpAsyncClient> mcpClients;

    private static final String companyTemplate = """
            ### {companyName}

            公司类型：{companyType}
            行业属性：{companyIndustry}
            工作地点：{jobLocation}
            职位类型：{position}
            招聘对象：{recruitmentTarget}
            目标职位：{recruitmentType}
                            
            访问链接: <a href="{relatedLink}">{relatedLink}</a>
            """;

    private static final String blogs = """
            帮我将下面这篇markdown格式的文章内容发布到微信公众号的草稿箱，使用 OrangeHeart 主题

            ---
            title: {title}
            cover: cover: ![https://gips0.baidu.com/it/u=1453504171,3846524544&fm=3042&app=3042&f=JPEG&wm=1,baiduai,0,0,13,9&wmo=0,0&w=640&h=360](https://gips0.baidu.com/it/u=1453504171,3846524544&fm=3042&app=3042&f=JPEG&wm=1,baiduai,0,0,13,9&wmo=0,0&w=640&h=360)
            ---
                            
            校招派今日上新校招岗位{ocCnt}条、实习岗位{internshipCnt}条，找工作的小伙伴重点关注一波哦
                            
            {text}
            """;


    public WechatBlogPublishService(OcAiModelContext ocAiModelContext, OcService ocService) {
        this.ocAiModelContext = ocAiModelContext;
        this.ocService = ocService;
    }

    /**
     * 发布今天上新的公众号文章内容
     *
     * @return
     */
    public String publishTodayOcInfo() {
        // 查询今天新上的oc列表信息
        List<OcVo> list = ocService.searchTodayOcList();
        if (CollectionUtils.isEmpty(list)) {
            return "今日没有上新";
        }

        StringBuilder ocInfo = new StringBuilder();
        int ocCnt = 0;
        StringBuilder internship = new StringBuilder();
        int internshipCnt = 0;
        for (OcVo vo : list) {
            String text = new PromptTemplate(companyTemplate)
                    .render(Map.of("companyName", vo.getCompanyName()
                                    , "companyType", vo.getCompanyType()
                                    , "companyIndustry", vo.getCompanyIndustry()
                                    , "jobLocation", vo.getJobLocation()
                                    , "position", vo.getPosition()
                                    , "recruitmentTarget", vo.getRecruitmentTarget()
                                    , "recruitmentType", vo.getRecruitmentType()
                                    , "relatedLink", vo.getRelatedLink()
                            )
                    );
            if (vo.getRecruitmentType().contains("实习")) {
                internshipCnt++;
                internship.append(text);
            } else {
                ocCnt++;
                ocInfo.append(text);
            }
        }

        String text = "";
        if (ocCnt > 0) {
            text += "\n## 校招信息\n\n" + ocInfo.toString();
        }
        if (internshipCnt > 0) {
            text += "\n## 实习信息\n\n" + internship.toString();
        }

        String promp = new PromptTemplate(blogs)
                .render(Map.of("title", "校招派快讯【" + DateUtil.todayStr() + "】"
                        , "text", text
                        , "ocCnt", ocCnt
                        , "internshipCnt", internshipCnt
                ));

        return this.ocAiModelContext
                .getMainChatClient()
                .prompt()
                .system("你现在是一个善于发布公众号的专家，善于使用各种工具。我会给你提供发布公众号的工具调用，请你基于这些工具调用来实现发布公众号")
                .user(promp)
                .toolCallbacks(new AsyncMcpToolCallbackProvider(mcpClients))
                .call().content();
    }

}

```



## 4.触发测试
在后台添加入口，进行测试验证。



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755856932171-b013751e-ce0f-4c06-8641-eeedede2750f.png)



点击上面的按钮，如果一切正常，则可以在公众号后台看到对应博文：样式还有点问题，待优化调整🤣

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/35158118/1755857191132-8462234a-74bc-47ba-9c16-2af2d9a71354.png)



:::success
说明：

+ 智普的免费模型，回调MCP Server的能力一般，很容易失败（推荐想体验的小伙伴使用更“智能“的模型)
+ 下一篇 [校招派集成阿里云实现自动发公众号](https://www.yuque.com/itwanger/yyt72l/vpbrt8do8ga67un7)  会演示自动发布公众号的全过程

:::



## 三、小结
本文的内容其实很轻量，只是借一个小例子，演示了如何用 **大模型 + MCP** 去完成那些以前看起来很复杂的任务。  


说白了，这就是把“智能能力”下沉到业务底层，让它和系统逻辑一起工作。



从现在的发展趋势来看，后端工程师的角色可能也要进化了——过去我们写的是 **API 接口**，未来我们可能要写的是 **MCP Server 接口**。  


毕竟，能被模型直接调用的服务，才算是面向未来的后端。

