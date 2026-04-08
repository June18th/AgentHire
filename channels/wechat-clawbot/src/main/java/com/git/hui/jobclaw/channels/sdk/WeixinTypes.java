package com.git.hui.jobclaw.channels.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Weixin protocol types (mirrors proto: GetUpdatesReq/Resp, WeixinMessage, SendMessageReq).
 * API uses JSON over HTTP; bytes fields are base64 strings in JSON.
 */
public class WeixinTypes {

    /** Common request metadata attached to every CGI request. */
    public static class BaseInfo {
        @JsonProperty("channel_version")
        private String channelVersion;

        public BaseInfo() {}

        public BaseInfo(String channelVersion) {
            this.channelVersion = channelVersion;
        }

        public String getChannelVersion() {
            return channelVersion;
        }

        public void setChannelVersion(String channelVersion) {
            this.channelVersion = channelVersion;
        }
    }

    /** Upload media type constants */
    public static class UploadMediaType {
        public static final int IMAGE = 1;
        public static final int VIDEO = 2;
        public static final int FILE = 3;
        public static final int VOICE = 4;
    }

    /** Message type constants */
    public static class MessageType {
        public static final int NONE = 0;
        public static final int USER = 1;
        public static final int BOT = 2;
    }

    /** Message item type constants */
    public static class MessageItemType {
        public static final int NONE = 0;
        public static final int TEXT = 1;
        public static final int IMAGE = 2;
        public static final int VOICE = 3;
        public static final int FILE = 4;
        public static final int VIDEO = 5;
    }

    /** Message state constants */
    public static class MessageState {
        public static final int NEW = 0;
        public static final int GENERATING = 1;
        public static final int FINISH = 2;
    }

    /** Typing status constants */
    public static class TypingStatus {
        public static final int TYPING = 1;
        public static final int CANCEL = 2;
    }

    // ==================== Request Types ====================

    /** GetUploadUrl request */
    public static class GetUploadUrlReq {
        private String filekey;
        
        @JsonProperty("media_type")
        private Integer mediaType;
        
        @JsonProperty("to_user_id")
        private String toUserId;
        
        private Integer rawsize;
        
        @JsonProperty("rawfilemd5")
        private String rawfilemd5;
        
        private Integer filesize;
        
        @JsonProperty("thumb_rawsize")
        private Integer thumbRawsize;
        
        @JsonProperty("thumb_rawfilemd5")
        private String thumbRawfilemd5;
        
        @JsonProperty("thumb_filesize")
        private Integer thumbFilesize;
        
        @JsonProperty("no_need_thumb")
        private Boolean noNeedThumb;
        
        private String aeskey;
        
        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        // Getters and Setters
        public String getFilekey() { return filekey; }
        public void setFilekey(String filekey) { this.filekey = filekey; }
        public Integer getMediaType() { return mediaType; }
        public void setMediaType(Integer mediaType) { this.mediaType = mediaType; }
        public String getToUserId() { return toUserId; }
        public void setToUserId(String toUserId) { this.toUserId = toUserId; }
        public Integer getRawsize() { return rawsize; }
        public void setRawsize(Integer rawsize) { this.rawsize = rawsize; }
        public String getRawfilemd5() { return rawfilemd5; }
        public void setRawfilemd5(String rawfilemd5) { this.rawfilemd5 = rawfilemd5; }
        public Integer getFilesize() { return filesize; }
        public void setFilesize(Integer filesize) { this.filesize = filesize; }
        public Integer getThumbRawsize() { return thumbRawsize; }
        public void setThumbRAWsize(Integer thumbRawsize) { this.thumbRawsize = thumbRawsize; }
        public String getThumbRawfilemd5() { return thumbRawfilemd5; }
        public void setThumbRawfilemd5(String thumbRawfilemd5) { this.thumbRawfilemd5 = thumbRawfilemd5; }
        public Integer getThumbFilesize() { return thumbFilesize; }
        public void setThumbFilesize(Integer thumbFilesize) { this.thumbFilesize = thumbFilesize; }
        public Boolean getNoNeedThumb() { return noNeedThumb; }
        public void setNoNeedThumb(Boolean noNeedThumb) { this.noNeedThumb = noNeedThumb; }
        public String getAeskey() { return aeskey; }
        public void setAeskey(String aeskey) { this.aeskey = aeskey; }
        public BaseInfo getBaseInfo() { return baseInfo; }
        public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }
    }

    /** GetUploadUrl response */
    public static class GetUploadUrlResp {
        @JsonProperty("upload_param")
        private String uploadParam;
        
        @JsonProperty("thumb_upload_param")
        private String thumbUploadParam;
        
        @JsonProperty("upload_full_url")
        private String uploadFullUrl;

        // Getters and Setters
        public String getUploadParam() { return uploadParam; }
        public void setUploadParam(String uploadParam) { this.uploadParam = uploadParam; }
        public String getThumbUploadParam() { return thumbUploadParam; }
        public void setThumbUploadParam(String thumbUploadParam) { this.thumbUploadParam = thumbUploadParam; }
        public String getUploadFullUrl() { return uploadFullUrl; }
        public void setUploadFullUrl(String uploadFullUrl) { this.uploadFullUrl = uploadFullUrl; }
    }

    /** Text item */
    public static class TextItem {
        private String text;

        public TextItem() {}
        public TextItem(String text) { this.text = text; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    /** CDN media reference; aes_key is base64-encoded bytes in JSON. */
    public static class CDNMedia {
        @JsonProperty("encrypt_query_param")
        private String encryptQueryParam;
        
        @JsonProperty("aes_key")
        private String aesKey;
        
        @JsonProperty("encrypt_type")
        private Integer encryptType;
        
        @JsonProperty("full_url")
        private String fullUrl;

        // Getters and Setters
        public String getEncryptQueryParam() { return encryptQueryParam; }
        public void setEncryptQueryParam(String encryptQueryParam) { this.encryptQueryParam = encryptQueryParam; }
        public String getAesKey() { return aesKey; }
        public void setAesKey(String aesKey) { this.aesKey = aesKey; }
        public Integer getEncryptType() { return encryptType; }
        public void setEncryptType(Integer encryptType) { this.encryptType = encryptType; }
        public String getFullUrl() { return fullUrl; }
        public void setFullUrl(String fullUrl) { this.fullUrl = fullUrl; }
    }

    /** Image item */
    public static class ImageItem {
        private CDNMedia media;
        
        @JsonProperty("thumb_media")
        private CDNMedia thumbMedia;
        
        private String aeskey;
        private String url;
        
        @JsonProperty("mid_size")
        private Integer midSize;
        
        @JsonProperty("thumb_size")
        private Integer thumbSize;
        
        @JsonProperty("thumb_height")
        private Integer thumbHeight;
        
        @JsonProperty("thumb_width")
        private Integer thumbWidth;
        
        @JsonProperty("hd_size")
        private Integer hdSize;

        // Getters and Setters
        public CDNMedia getMedia() { return media; }
        public void setMedia(CDNMedia media) { this.media = media; }
        public CDNMedia getThumbMedia() { return thumbMedia; }
        public void setThumbMedia(CDNMedia thumbMedia) { this.thumbMedia = thumbMedia; }
        public String getAeskey() { return aeskey; }
        public void setAeskey(String aeskey) { this.aeskey = aeskey; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Integer getMidSize() { return midSize; }
        public void setMidSize(Integer midSize) { this.midSize = midSize; }
        public Integer getThumbSize() { return thumbSize; }
        public void setThumbSize(Integer thumbSize) { this.thumbSize = thumbSize; }
        public Integer getThumbHeight() { return thumbHeight; }
        public void setThumbHeight(Integer thumbHeight) { this.thumbHeight = thumbHeight; }
        public Integer getThumbWidth() { return thumbWidth; }
        public void setThumbWidth(Integer thumbWidth) { this.thumbWidth = thumbWidth; }
        public Integer getHdSize() { return hdSize; }
        public void setHdSize(Integer hdSize) { this.hdSize = hdSize; }
    }

    /** Voice item */
    public static class VoiceItem {
        private CDNMedia media;
        
        @JsonProperty("encode_type")
        private Integer encodeType;
        
        @JsonProperty("bits_per_sample")
        private Integer bitsPerSample;
        
        @JsonProperty("sample_rate")
        private Integer sampleRate;
        
        private Integer playtime;
        private String text;

        // Getters and Setters
        public CDNMedia getMedia() { return media; }
        public void setMedia(CDNMedia media) { this.media = media; }
        public Integer getEncodeType() { return encodeType; }
        public void setEncodeType(Integer encodeType) { this.encodeType = encodeType; }
        public Integer getBitsPerSample() { return bitsPerSample; }
        public void setBitsPerSample(Integer bitsPerSample) { this.bitsPerSample = bitsPerSample; }
        public Integer getSampleRate() { return sampleRate; }
        public void setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; }
        public Integer getPlaytime() { return playtime; }
        public void setPlaytime(Integer playtime) { this.playtime = playtime; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    /** File item */
    public static class FileItem {
        private CDNMedia media;
        
        @JsonProperty("file_name")
        private String fileName;
        
        private String md5;
        private String len;

        // Getters and Setters
        public CDNMedia getMedia() { return media; }
        public void setMedia(CDNMedia media) { this.media = media; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getMd5() { return md5; }
        public void setMd5(String md5) { this.md5 = md5; }
        public String getLen() { return len; }
        public void setLen(String len) { this.len = len; }
    }

    /** Video item */
    public static class VideoItem {
        private CDNMedia media;
        
        @JsonProperty("video_size")
        private Integer videoSize;
        
        @JsonProperty("play_length")
        private Integer playLength;
        
        @JsonProperty("video_md5")
        private String videoMd5;
        
        @JsonProperty("thumb_media")
        private CDNMedia thumbMedia;
        
        @JsonProperty("thumb_size")
        private Integer thumbSize;
        
        @JsonProperty("thumb_height")
        private Integer thumbHeight;
        
        @JsonProperty("thumb_width")
        private Integer thumbWidth;

        // Getters and Setters
        public CDNMedia getMedia() { return media; }
        public void setMedia(CDNMedia media) { this.media = media; }
        public Integer getVideoSize() { return videoSize; }
        public void setVideoSize(Integer videoSize) { this.videoSize = videoSize; }
        public Integer getPlayLength() { return playLength; }
        public void setPlayLength(Integer playLength) { this.playLength = playLength; }
        public String getVideoMd5() { return videoMd5; }
        public void setVideoMd5(String videoMd5) { this.videoMd5 = videoMd5; }
        public CDNMedia getThumbMedia() { return thumbMedia; }
        public void setThumbMedia(CDNMedia thumbMedia) { this.thumbMedia = thumbMedia; }
        public Integer getThumbSize() { return thumbSize; }
        public void setThumbSize(Integer thumbSize) { this.thumbSize = thumbSize; }
        public Integer getThumbHeight() { return thumbHeight; }
        public void setThumbHeight(Integer thumbHeight) { this.thumbHeight = thumbHeight; }
        public Integer getThumbWidth() { return thumbWidth; }
        public void setThumbWidth(Integer thumbWidth) { this.thumbWidth = thumbWidth; }
    }

    /** Referenced message */
    public static class RefMessage {
        @JsonProperty("message_item")
        private MessageItem messageItem;
        private String title;

        // Getters and Setters
        public MessageItem getMessageItem() { return messageItem; }
        public void setMessageItem(MessageItem messageItem) { this.messageItem = messageItem; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    /** Message item */
    public static class MessageItem {
        private Integer type;
        
        @JsonProperty("create_time_ms")
        private Long createTimeMs;
        
        @JsonProperty("update_time_ms")
        private Long updateTimeMs;
        
        @JsonProperty("is_completed")
        private Boolean isCompleted;
        
        @JsonProperty("msg_id")
        private String msgId;
        
        @JsonProperty("ref_msg")
        private RefMessage refMsg;
        
        @JsonProperty("text_item")
        private TextItem textItem;
        
        @JsonProperty("image_item")
        private ImageItem imageItem;
        
        @JsonProperty("voice_item")
        private VoiceItem voiceItem;
        
        @JsonProperty("file_item")
        private FileItem fileItem;
        
        @JsonProperty("video_item")
        private VideoItem videoItem;

        // Getters and Setters
        public Integer getType() { return type; }
        public void setType(Integer type) { this.type = type; }
        public Long getCreateTimeMs() { return createTimeMs; }
        public void setCreateTimeMs(Long createTimeMs) { this.createTimeMs = createTimeMs; }
        public Long getUpdateTimeMs() { return updateTimeMs; }
        public void setUpdateTimeMs(Long updateTimeMs) { this.updateTimeMs = updateTimeMs; }
        public Boolean getIsCompleted() { return isCompleted; }
        public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }
        public String getMsgId() { return msgId; }
        public void setMsgId(String msgId) { this.msgId = msgId; }
        public RefMessage getRefMsg() { return refMsg; }
        public void setRefMsg(RefMessage refMsg) { this.refMsg = refMsg; }
        public TextItem getTextItem() { return textItem; }
        public void setTextItem(TextItem textItem) { this.textItem = textItem; }
        public ImageItem getImageItem() { return imageItem; }
        public void setImageItem(ImageItem imageItem) { this.imageItem = imageItem; }
        public VoiceItem getVoiceItem() { return voiceItem; }
        public void setVoiceItem(VoiceItem voiceItem) { this.voiceItem = voiceItem; }
        public FileItem getFileItem() { return fileItem; }
        public void setFileItem(FileItem fileItem) { this.fileItem = fileItem; }
        public VideoItem getVideoItem() { return videoItem; }
        public void setVideoItem(VideoItem videoItem) { this.videoItem = videoItem; }
    }

    /** Unified message (proto: WeixinMessage) */
    public static class WeixinMessage {
        private Long seq;
        
        @JsonProperty("message_id")
        private Long messageId;
        
        @JsonProperty("from_user_id")
        private String fromUserId;
        
        @JsonProperty("to_user_id")
        private String toUserId;
        
        @JsonProperty("client_id")
        private String clientId;
        
        @JsonProperty("create_time_ms")
        private Long createTimeMs;
        
        @JsonProperty("update_time_ms")
        private Long updateTimeMs;
        
        @JsonProperty("delete_time_ms")
        private Long deleteTimeMs;
        
        @JsonProperty("session_id")
        private String sessionId;
        
        @JsonProperty("group_id")
        private String groupId;
        
        @JsonProperty("message_type")
        private Integer messageType;
        
        @JsonProperty("message_state")
        private Integer messageState;
        
        @JsonProperty("item_list")
        private List<MessageItem> itemList;
        
        @JsonProperty("context_token")
        private String contextToken;

        // Getters and Setters
        public Long getSeq() { return seq; }
        public void setSeq(Long seq) { this.seq = seq; }
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public String getFromUserId() { return fromUserId; }
        public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
        public String getToUserId() { return toUserId; }
        public void setToUserId(String toUserId) { this.toUserId = toUserId; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public Long getCreateTimeMs() { return createTimeMs; }
        public void setCreateTimeMs(Long createTimeMs) { this.createTimeMs = createTimeMs; }
        public Long getUpdateTimeMs() { return updateTimeMs; }
        public void setUpdateTimeMs(Long updateTimeMs) { this.updateTimeMs = updateTimeMs; }
        public Long getDeleteTimeMs() { return deleteTimeMs; }
        public void setDeleteTimeMs(Long deleteTimeMs) { this.deleteTimeMs = deleteTimeMs; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public Integer getMessageType() { return messageType; }
        public void setMessageType(Integer messageType) { this.messageType = messageType; }
        public Integer getMessageState() { return messageState; }
        public void setMessageState(Integer messageState) { this.messageState = messageState; }
        public List<MessageItem> getItemList() { return itemList; }
        public void setItemList(List<MessageItem> itemList) { this.itemList = itemList; }
        public String getContextToken() { return contextToken; }
        public void setContextToken(String contextToken) { this.contextToken = contextToken; }
    }

    /** GetUpdates request */
    public static class GetUpdatesReq {
        @JsonProperty("get_updates_buf")
        private String getUpdatesBuf;
        
        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        public GetUpdatesReq() {}
        public GetUpdatesReq(String getUpdatesBuf, BaseInfo baseInfo) {
            this.getUpdatesBuf = getUpdatesBuf;
            this.baseInfo = baseInfo;
        }

        public String getGetUpdatesBuf() { return getUpdatesBuf; }
        public void setGetUpdatesBuf(String getUpdatesBuf) { this.getUpdatesBuf = getUpdatesBuf; }
        public BaseInfo getBaseInfo() { return baseInfo; }
        public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }
    }

    /** GetUpdates response */
    public static class GetUpdatesResp {
        private Integer ret;
        private Integer errcode;
        private String errmsg;
        private List<WeixinMessage> msgs;
        
        @JsonProperty("sync_buf")
        private String syncBuf;
        
        @JsonProperty("get_updates_buf")
        private String getUpdatesBuf;
        
        @JsonProperty("longpolling_timeout_ms")
        private Integer longpollingTimeoutMs;

        // Getters and Setters
        public Integer getRet() { return ret; }
        public void setRet(Integer ret) { this.ret = ret; }
        public Integer getErrcode() { return errcode; }
        public void setErrcode(Integer errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public List<WeixinMessage> getMsgs() { return msgs; }
        public void setMsgs(List<WeixinMessage> msgs) { this.msgs = msgs; }
        public String getSyncBuf() { return syncBuf; }
        public void setSyncBuf(String syncBuf) { this.syncBuf = syncBuf; }
        public String getGetUpdatesBuf() { return getUpdatesBuf; }
        public void setGetUpdatesBuf(String getUpdatesBuf) { this.getUpdatesBuf = getUpdatesBuf; }
        public Integer getLongpollingTimeoutMs() { return longpollingTimeoutMs; }
        public void setLongpollingTimeoutMs(Integer longpollingTimeoutMs) { this.longpollingTimeoutMs = longpollingTimeoutMs; }
    }

    /** SendMessage request */
    public static class SendMessageReq {
        private WeixinMessage msg;
        
        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        public SendMessageReq() {}
        public SendMessageReq(WeixinMessage msg) { this.msg = msg; }

        public WeixinMessage getMsg() { return msg; }
        public void setMsg(WeixinMessage msg) { this.msg = msg; }
        public BaseInfo getBaseInfo() { return baseInfo; }
        public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }
    }

    /** SendTyping request */
    public static class SendTypingReq {
        @JsonProperty("ilink_user_id")
        private String ilinkUserId;
        
        @JsonProperty("typing_ticket")
        private String typingTicket;
        
        private Integer status;
        
        @JsonProperty("base_info")
        private BaseInfo baseInfo;

        public SendTypingReq() {}
        public SendTypingReq(String ilinkUserId, String typingTicket, Integer status) {
            this.ilinkUserId = ilinkUserId;
            this.typingTicket = typingTicket;
            this.status = status;
        }

        public String getIlinkUserId() { return ilinkUserId; }
        public void setIlinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; }
        public String getTypingTicket() { return typingTicket; }
        public void setTypingTicket(String typingTicket) { this.typingTicket = typingTicket; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public BaseInfo getBaseInfo() { return baseInfo; }
        public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }
    }

    /** GetConfig response */
    public static class GetConfigResp {
        private Integer ret;
        private String errmsg;
        
        @JsonProperty("typing_ticket")
        private String typingTicket;

        // Getters and Setters
        public Integer getRet() { return ret; }
        public void setRet(Integer ret) { this.ret = ret; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public String getTypingTicket() { return typingTicket; }
        public void setTypingTicket(String typingTicket) { this.typingTicket = typingTicket; }
    }
}
