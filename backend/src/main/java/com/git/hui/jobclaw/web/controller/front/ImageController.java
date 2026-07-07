package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.web.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
@ConditionalOnProperty(prefix = "jobclaw.img", name = "storage-type", havingValue = "minio")
@RequestMapping("${jobclaw.img.web-img-path:/oc/img/}")
public class ImageController {
    private final FileStorageService fileStorageService;

    public ImageController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("**")
    public ResponseEntity<byte[]> image(HttpServletRequest request) throws Exception {
        String requestUri = request.getRequestURI();
        String path = requestUri.substring(request.getContextPath().length());
        try (InputStream input = fileStorageService.loadPublicFile(path)) {
            return ResponseEntity.ok()
                    .contentType(mediaType(path))
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                    .body(StreamUtils.copyToByteArray(input));
        }
    }

    private MediaType mediaType(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerPath.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerPath.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lowerPath.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (lowerPath.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
