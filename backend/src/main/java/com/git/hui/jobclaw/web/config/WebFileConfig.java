package com.git.hui.jobclaw.web.config;

import com.git.hui.jobclaw.web.service.FileStorageScene;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jobclaw.img")
public class WebFileConfig {

    /**
     * 存储绝对路径：指的是硬盘的绝对路径前缀
     */
    private String absTmpPath;

    /**
     * 存储相对路径：指的是http访问的路径
     */
    private String webImgPath;
    /**
     * 访问图片的host
     */
    private String cdnHost;

    private String storageType = "local";

    private String minioEndpoint;

    private String minioAccessKey;

    private String minioSecretKey;

    private String minioBucket = "jobclaw";

    private Buckets buckets = new Buckets();

    public String buildImgUrl(String url) {
        if (!url.startsWith(cdnHost)) {
            return cdnHost + url;
        }
        return url;
    }

    public String bucketFor(FileStorageScene scene) {
        return switch (scene) {
            case PUBLIC_IMAGE -> fallbackBucket(buckets.getPublicImage());
            case PRIVATE_MATERIAL -> fallbackBucket(buckets.getPrivateMaterial());
            case TEMP_UPLOAD -> fallbackBucket(buckets.getTemp());
            case EXPORT_FILE -> fallbackBucket(buckets.getExport());
        };
    }

    private String fallbackBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? minioBucket : bucket;
    }

    @Data
    public static class Buckets {
        private String publicImage;
        private String privateMaterial;
        private String temp;
        private String export;
    }
}
