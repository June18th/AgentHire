package com.git.hui.jobclaw.web.model.wx;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * 返回的数据结构体
 * <p>
 *
 * @author yihui
 * @link <a href="https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Passive_user_reply_message.html"/>
 * @date 2022/6/20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JacksonXmlRootElement(localName = "xml")
public class WxImgTxtMsgResVo extends BaseWxMsgResVo {
    @JacksonXmlProperty(localName = "ArticleCount")
    private Integer articleCount;
    @JacksonXmlElementWrapper(localName = "Articles")
    @JacksonXmlProperty(localName = "item")
    private List<WxImgTxtItemVo> articles;

    public WxImgTxtMsgResVo() {
        setMsgType("news");
    }
}
