package com.git.hui.jobclaw.web.service;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.web.config.WebFileConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileStorageService {
    private static final long MINIO_PART_SIZE = 10L * 1024 * 1024;

    private final WebFileConfig webFileConfig;
    private volatile MinioClient minioClient;

    public FileStorageService(WebFileConfig webFileConfig) {
        this.webFileConfig = webFileConfig;
    }

    public String savePublicFile(byte[] content, String relativeUrl, String contentType) {
        return saveFile(FileStorageScene.PUBLIC_IMAGE, content, relativeUrl, contentType);
    }

    public String saveFile(FileStorageScene scene, byte[] content, String relativeUrl, String contentType) {
        String normalizedUrl = normalizeRelativeUrl(relativeUrl);
        if (isMinioStorage()) {
            saveToMinio(scene, content, normalizedUrl, contentType);
        } else {
            saveToLocal(content, normalizedUrl);
        }
        return webFileConfig.buildImgUrl(normalizedUrl);
    }

    public InputStream loadPublicFile(String path) {
        return loadFile(FileStorageScene.PUBLIC_IMAGE, path);
    }

    public InputStream loadFile(FileStorageScene scene, String path) {
        if (isMinioStorage()) {
            return loadFromMinio(scene, path);
        }
        return loadFromLocal(path);
    }

    public void deletePublicFile(String pathOrUrl) {
        deleteFile(FileStorageScene.PUBLIC_IMAGE, pathOrUrl);
    }

    public void deleteFile(FileStorageScene scene, String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return;
        }
        String managedPath = toManagedPublicPath(pathOrUrl);
        if (managedPath == null) {
            log.debug("Skip deleting unmanaged public file path: {}", pathOrUrl);
            return;
        }
        try {
            if (isMinioStorage()) {
                deleteFromMinio(scene, managedPath);
            } else {
                Files.deleteIfExists(buildLocalPath(managedPath));
            }
        } catch (Exception e) {
            log.warn("Failed to delete public file {}", pathOrUrl, e);
        }
    }

    public void prunePublicDirectory(String relativeDir, String extension, int maxFiles) {
        pruneDirectory(FileStorageScene.PUBLIC_IMAGE, relativeDir, extension, maxFiles);
    }

    public void pruneDirectory(FileStorageScene scene, String relativeDir, String extension, int maxFiles) {
        if (maxFiles <= 0) {
            return;
        }
        if (isMinioStorage()) {
            pruneMinioDirectory(scene, relativeDir, extension, maxFiles);
        } else {
            pruneLocalDirectory(relativeDir, extension, maxFiles);
        }
    }

    public boolean isMinioStorage() {
        return "minio".equalsIgnoreCase(webFileConfig.getStorageType());
    }

    private void saveToLocal(byte[] content, String relativeUrl) {
        Path target = buildLocalPath(relativeUrl);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BizException(StatusEnum.UPLOAD_PIC_FAILED, e.getMessage());
        }
    }

    private InputStream loadFromLocal(String pathOrUrl) {
        try {
            return Files.newInputStream(buildLocalPath(pathOrUrl));
        } catch (IOException e) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "文件不存在: " + pathOrUrl);
        }
    }

    private Path buildLocalPath(String pathOrUrl) {
        String relative = stripPublicPrefix(pathOrUrl);
        Path storageRoot = Path.of(webFileConfig.getAbsTmpPath()).toAbsolutePath().normalize();
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "文件存储路径不合法");
        }
        return target;
    }

    private void pruneLocalDirectory(String relativeDir, String extension, int maxFiles) {
        Path dir = buildLocalPath(normalizeDirectoryUrl(relativeDir));
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> hasExtension(path.getFileName().toString(), extension))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .skip(maxFiles)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to prune local file {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to prune local directory {}", dir, e);
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private void saveToMinio(FileStorageScene scene, byte[] content, String relativeUrl, String contentType) {
        String objectName = objectNameFromPath(relativeUrl);
        String bucket = webFileConfig.bucketFor(scene);
        try {
            ensureMinioBucket(bucket);
            getMinioClient().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, MINIO_PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to save minio file bucket={}, object={}", bucket, objectName, e);
            throw new BizException(StatusEnum.UPLOAD_PIC_FAILED, e.getMessage());
        }
    }

    private InputStream loadFromMinio(FileStorageScene scene, String path) {
        String objectName = objectNameFromPath(path);
        try {
            return getMinioClient().getObject(GetObjectArgs.builder()
                    .bucket(webFileConfig.bucketFor(scene))
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "文件不存在: " + path);
        }
    }

    private void deleteFromMinio(FileStorageScene scene, String pathOrUrl) throws Exception {
        getMinioClient().removeObject(RemoveObjectArgs.builder()
                .bucket(webFileConfig.bucketFor(scene))
                .object(objectNameFromPath(pathOrUrl))
                .build());
    }

    private void pruneMinioDirectory(FileStorageScene scene, String relativeDir, String extension, int maxFiles) {
        String prefix = objectNameFromPath(normalizeDirectoryUrl(relativeDir));
        String bucket = webFileConfig.bucketFor(scene);
        try {
            ensureMinioBucket(bucket);
            Iterable<Result<Item>> objects = getMinioClient().listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(false)
                    .build());
            Stream<Item> stream = streamMinioItems(objects);
            stream.filter(item -> hasExtension(item.objectName(), extension))
                    .sorted(Comparator.comparing(Item::lastModified, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .skip(maxFiles)
                    .forEach(item -> {
                        try {
                            deleteFromMinio(scene, item.objectName());
                        } catch (Exception e) {
                            log.warn("Failed to prune minio object {}", item.objectName(), e);
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to prune minio directory {}", relativeDir, e);
        }
    }

    private Stream<Item> streamMinioItems(Iterable<Result<Item>> objects) {
        return java.util.stream.StreamSupport.stream(objects.spliterator(), false)
                .map(result -> {
                    try {
                        return result.get();
                    } catch (Exception e) {
                        log.warn("Failed to read minio list item", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    private MinioClient getMinioClient() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(webFileConfig.getMinioEndpoint())
                            .credentials(webFileConfig.getMinioAccessKey(), webFileConfig.getMinioSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private void ensureMinioBucket(String bucket) throws Exception {
        boolean exists = getMinioClient().bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());
        if (!exists) {
            getMinioClient().makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket)
                    .build());
        }
    }

    private String objectNameFromPath(String pathOrUrl) {
        return stripPublicPrefix(pathOrUrl);
    }

    private String stripPublicPrefix(String pathOrUrl) {
        String path = stripQuery(pathOrUrl.trim());
        if (webFileConfig.getCdnHost() != null && !webFileConfig.getCdnHost().isBlank()
                && path.startsWith(webFileConfig.getCdnHost())) {
            path = path.substring(webFileConfig.getCdnHost().length());
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private String toManagedPublicPath(String pathOrUrl) {
        String path = stripQuery(pathOrUrl.trim());
        if (webFileConfig.getCdnHost() != null && !webFileConfig.getCdnHost().isBlank()
                && path.startsWith(webFileConfig.getCdnHost())) {
            path = path.substring(webFileConfig.getCdnHost().length());
        } else if (isExternalUrl(path)) {
            return null;
        }

        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        String publicPrefix = normalizeDirectoryUrl(webFileConfig.getWebImgPath());
        while (publicPrefix.startsWith("/")) {
            publicPrefix = publicPrefix.substring(1);
        }
        return path.startsWith(publicPrefix) ? path : null;
    }

    private boolean isExternalUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//");
    }

    private String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        return queryIndex >= 0 ? value.substring(0, queryIndex) : value;
    }

    private String normalizeRelativeUrl(String relativeUrl) {
        String normalized = stripQuery(relativeUrl.trim());
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private String normalizeDirectoryUrl(String relativeDir) {
        String normalized = normalizeRelativeUrl(relativeDir);
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private boolean hasExtension(String value, String extension) {
        if (extension == null || extension.isBlank()) {
            return true;
        }
        return value.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT));
    }
}
