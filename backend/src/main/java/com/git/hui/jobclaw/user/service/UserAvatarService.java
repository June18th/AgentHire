package com.git.hui.jobclaw.user.service;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.web.config.WebFileConfig;
import com.git.hui.jobclaw.web.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAvatarService {
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final int AVATAR_SIZE = 512;
    private static final int MAX_HISTORY_FILES = 3;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final WebFileConfig webFileConfig;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserAvatarService(WebFileConfig webFileConfig, UserService userService, FileStorageService fileStorageService) {
        this.webFileConfig = webFileConfig;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        validateFile(file);

        byte[] bytes = readBytes(file);
        validateImageSignature(bytes);
        BufferedImage source = readImage(bytes);
        validateImageDimension(source);

        BufferedImage avatar = resizeCenterCrop(source);
        byte[] avatarBytes = writeJpeg(avatar);
        String relativeUrl = buildRelativeUrl(userId);
        String avatarDir = buildAvatarDir(userId);
        String oldAvatarUrl = userService.getUserAvatar(userId);

        String avatarUrl = fileStorageService.savePublicFile(avatarBytes, relativeUrl, "image/jpeg")
                + "?v=" + System.currentTimeMillis();
        userService.updateUserAvatar(userId, avatarUrl);
        fileStorageService.deletePublicFile(oldAvatarUrl);
        fileStorageService.prunePublicDirectory(avatarDir, "jpg", MAX_HISTORY_FILES);
        return avatarUrl;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "头像文件不能超过 5MB");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "仅支持 jpg、png 图片");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "头像文件类型不支持");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "仅支持 jpg、png 图片");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new BizException(StatusEnum.UPLOAD_PIC_FAILED, e.getMessage());
        }
    }

    private void validateImageSignature(byte[] bytes) {
        if (isJpeg(bytes) || isPng(bytes)) {
            return;
        }
        throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "头像文件内容不是有效图片");
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length > 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length > 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private BufferedImage readImage(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "无法识别头像图片");
            }
            return image;
        } catch (IOException e) {
            throw new BizException(StatusEnum.UPLOAD_PIC_FAILED, e.getMessage());
        }
    }

    private void validateImageDimension(BufferedImage image) {
        if (image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "头像图片尺寸不能超过 4096x4096");
        }
    }

    // AIDEV-NOTE: normalize avatar image
    private BufferedImage resizeCenterCrop(BufferedImage source) {
        int cropSize = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - cropSize) / 2;
        int cropY = (source.getHeight() - cropSize) / 2;

        BufferedImage target = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, AVATAR_SIZE, AVATAR_SIZE, cropX, cropY, cropX + cropSize, cropY + cropSize, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] writeJpeg(BufferedImage avatar) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(avatar, "jpg", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BizException(StatusEnum.UPLOAD_PIC_FAILED, e.getMessage());
        }
    }

    private String buildRelativeUrl(Long userId) {
        return buildAvatarDir(userId) + UUID.randomUUID() + ".jpg";
    }

    private String buildAvatarDir(Long userId) {
        return normalizeWebPath(webFileConfig.getWebImgPath()) + "avatars/" + userId + "/";
    }

    private String normalizeWebPath(String value) {
        String path = value == null || value.isBlank() ? "/oc/img/" : value.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }
}
