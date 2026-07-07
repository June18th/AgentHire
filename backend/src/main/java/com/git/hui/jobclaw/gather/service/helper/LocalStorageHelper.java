package com.git.hui.jobclaw.gather.service.helper;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.web.config.WebFileConfig;
import com.github.hui.quick.plugin.base.OSUtil;
import com.github.hui.quick.plugin.base.file.FileReadUtil;
import com.github.hui.quick.plugin.base.file.FileWriteUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

/**
 * Stores uploaded files in local disk by default, or MinIO when configured.
 */
@Slf4j
@Component
public class LocalStorageHelper {
    private static final Random random = new Random();
    private static final long MINIO_PART_SIZE = 10L * 1024 * 1024;

    @Autowired
    private WebFileConfig imgConfig;

    private volatile MinioClient minioClient;

    private String genTmpFileName() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + "_" + random.nextInt(100);
    }

    public String saveFile(InputStream input, String fileType) {
        String fileName = genTmpFileName();
        if (isMinioStorage()) {
            return saveToMinio(input, fileName, fileType);
        }
        return saveToLocal(input, fileName, fileType);
    }

    public InputStream loadFile(String path) {
        if (isMinioStorage()) {
            return loadFromMinio(path);
        }
        return loadFromLocal(path);
    }

    public String buildFileHttpUrl(String file) {
        return imgConfig.buildImgUrl(file);
    }

    public boolean isMinioStorage() {
        return "minio".equalsIgnoreCase(imgConfig.getStorageType());
    }

    private String saveToLocal(InputStream input, String fileName, String fileType) {
        String path = imgConfig.getAbsTmpPath() + imgConfig.getWebImgPath();
        FileWriteUtil.FileInfo fileInfo;
        try {
            fileInfo = FileWriteUtil.saveFileByStream(input, path, fileName, fileType);
        } catch (FileNotFoundException e) {
            log.error("failed to save file to {}", path, e);
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "附件存储异常!");
        }
        return imgConfig.getWebImgPath() + fileInfo.getFilename() + "." + fileInfo.getFileType();
    }

    private InputStream loadFromLocal(String path) {
        String originalPath = path;
        if (imgConfig.getCdnHost() != null && path.startsWith(imgConfig.getCdnHost())) {
            path = imgConfig.getAbsTmpPath() + path.substring(imgConfig.getCdnHost().length());
        } else if (path.startsWith("/") && !path.startsWith(imgConfig.getAbsTmpPath()) && OSUtil.isWinOS()) {
            path = "d:" + imgConfig.getAbsTmpPath() + path;
        } else if (path.startsWith(imgConfig.getWebImgPath())) {
            path = imgConfig.getAbsTmpPath() + path;
        }

        try {
            return FileReadUtil.createByteRead(path);
        } catch (java.nio.file.NoSuchFileException e) {
            log.error("file does not exist: {}, original path: {}", path, originalPath, e);
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "文件不存在: " + originalPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String saveToMinio(InputStream input, String fileName, String fileType) {
        String filename = fileName + "." + fileType;
        String objectName = objectName(filename);
        try {
            ensureMinioBucket();
            getMinioClient().putObject(PutObjectArgs.builder()
                    .bucket(imgConfig.getMinioBucket())
                    .object(objectName)
                    .stream(input, -1, MINIO_PART_SIZE)
                    .contentType(contentType(fileType))
                    .build());
            return imgConfig.getWebImgPath() + filename;
        } catch (Exception e) {
            log.error("failed to save file to minio bucket {}, object {}", imgConfig.getMinioBucket(), objectName, e);
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "附件存储异常!");
        }
    }

    private InputStream loadFromMinio(String path) {
        String objectName = objectNameFromPath(path);
        try {
            return getMinioClient().getObject(GetObjectArgs.builder()
                    .bucket(imgConfig.getMinioBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("failed to load file from minio bucket {}, object {}", imgConfig.getMinioBucket(), objectName, e);
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "文件不存在: " + path);
        }
    }

    private MinioClient getMinioClient() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(imgConfig.getMinioEndpoint())
                            .credentials(imgConfig.getMinioAccessKey(), imgConfig.getMinioSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private void ensureMinioBucket() throws Exception {
        boolean exists = getMinioClient().bucketExists(BucketExistsArgs.builder()
                .bucket(imgConfig.getMinioBucket())
                .build());
        if (!exists) {
            getMinioClient().makeBucket(MakeBucketArgs.builder()
                    .bucket(imgConfig.getMinioBucket())
                    .build());
        }
    }

    private String objectName(String filename) {
        String prefix = imgConfig.getWebImgPath();
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + filename;
    }

    private String objectNameFromPath(String path) {
        if (StringUtils.hasText(imgConfig.getCdnHost()) && path.startsWith(imgConfig.getCdnHost())) {
            path = path.substring(imgConfig.getCdnHost().length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private String contentType(String fileType) {
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }
}
