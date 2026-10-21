package com.zju.imagemanger.controller;

import com.zju.imagemanger.dto.ApiResponse;
import com.zju.imagemanger.dto.ImageEditRequest;
import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.entity.Tag;
import com.zju.imagemanger.service.ImageEditService;
import com.zju.imagemanger.service.ImageService;
import com.zju.imagemanger.service.UserService;
import com.zju.imagemanger.service.UploadRateLimiter;
import com.zju.imagemanger.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/images")
public class ImageController {
    @Autowired
    private ImageService imageService;

    @Autowired
    private UserService userService;

    @Autowired
    private StorageService storageService;
    
    @Autowired
    private ImageEditService imageEditService;

    @Autowired
    private UploadRateLimiter uploadRateLimiter;

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    @PostMapping("/upload")
    public ApiResponse<ImageResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) throws java.io.IOException {
        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();
        uploadRateLimiter.check(userId);
        Image image = imageService.uploadImage(file, userId, tags, idempotencyKey);
        return ApiResponse.success("上传成功", toResponse(image));
    }

    @GetMapping
    public ApiResponse<Page<ImageResponse>> getImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();
        
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Image> images = imageService.getUserImages(userId, pageable);
        Page<ImageResponse> resp = images.map(this::toResponse);
        return ApiResponse.success(resp);
    }

    @GetMapping("/cursor")
    public ApiResponse<CursorPage> getImagesByCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        int pageSize = Math.max(1, Math.min(size, 100));
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                if (cursor.length() > 512) throw new IllegalArgumentException("cursor too long");
                String decoded = new String(java.util.Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", 3);
                if (parts.length != 3 || !Long.valueOf(parts[0]).equals(userId)) throw new IllegalArgumentException("bad cursor");
                cursorTime = LocalDateTime.parse(parts[1]);
                cursorId = Long.parseLong(parts[2]);
            } catch (RuntimeException invalidCursor) {
                throw new IllegalArgumentException("游标无效");
            }
        }
        Page<Image> images = imageService.getUserImagesAfter(userId, cursorTime, cursorId, PageRequest.of(0, pageSize));
        List<Image> content = images.getContent();
        String next = images.hasNext() && !content.isEmpty()
                ? java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (userId + "|" + content.get(content.size() - 1).getUploadTime() + "|" + content.get(content.size() - 1).getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : null;
        return ApiResponse.success(new CursorPage(content.stream().map(this::toResponse).toList(), next));
    }

    @GetMapping("/processing/failed")
    public ApiResponse<Page<ImageResponse>> getFailedImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        Page<ImageResponse> failed = imageService.getFailedImages(userId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(this::toResponse);
        return ApiResponse.success(failed);
    }

    @PostMapping("/{id}/retry-processing")
    public ApiResponse<ImageResponse> retryImageProcessing(@PathVariable Long id, Authentication authentication) {
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        return ApiResponse.success("已重新排队", toResponse(imageService.retryFailedProcessing(id, userId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ImageResponse> getImage(@PathVariable Long id, Authentication authentication) {
        try {
            String username = authentication.getName();
            Long userId = userService.getUserByUsername(username).getId();
            
            Image image = imageService.getImageById(id, userId);
            return ApiResponse.success(toResponse(image));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "600") String size,
            Authentication authentication) {
        try {
            Long userId = userService.getUserByUsername(authentication.getName()).getId();
            Image image = imageService.getImageById(id, userId);
            if (!List.of("300", "600", "1200").contains(size)) {
                return ResponseEntity.badRequest().build();
            }
            
            String thumbnailPath = String.format("%s/%s/thumb_%s.jpg", thumbnailDir, id, size);
            File thumbnailFile = new File(thumbnailPath);
            
            if (!thumbnailFile.exists()) {
                // 如果缩略图不存在，返回原图
                if (image.getObjectKey() != null) {
                    return ResponseEntity.notFound().build();
                }
                File originalFile = new File(image.getFilePath());
                if (originalFile.exists()) {
                    Resource resource = new FileSystemResource(originalFile);
                    return ResponseEntity.ok()
                            .contentType(safeMediaType(image.getMimeType()))
                            .header("X-Content-Type-Options", "nosniff")
                            .body(resource);
                }
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(thumbnailFile);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                            .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 原图访问接口：详情页使用，避免缩略图生成失败导致无法查看
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getOriginalFile(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = userService.getUserByUsername(authentication.getName()).getId();
            Image image = imageService.getImageById(id, userId);
            boolean objectStorage = image.getObjectKey() != null;
            Resource resource = objectStorage
                    ? new org.springframework.core.io.InputStreamResource(storageService.get(image.getObjectKey()))
                    : new FileSystemResource(new File(image.getFilePath()));
            if (!objectStorage && !resource.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(safeMediaType(image.getMimeType()))
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType safeMediaType(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase()) {
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "image/png" -> MediaType.IMAGE_PNG;
            case "image/gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    @GetMapping("/search")
    public ApiResponse<Page<ImageResponse>> searchImages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();
        
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Image> images = keyword == null || keyword.isBlank()
                ? imageService.searchImages(userId, keyword, startDate, endDate, pageable)
                : imageService.searchImagesWithNegativeCache(userId, keyword, startDate, endDate, pageable);
        Page<ImageResponse> resp = images.map(this::toResponse);
        return ApiResponse.success(resp);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id, Authentication authentication) throws java.io.IOException {
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        imageService.deleteImage(id, userId);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/edit")
    public ApiResponse<ImageResponse> editImage(
            @PathVariable Long id,
            @RequestBody ImageEditRequest request,
            Authentication authentication) throws java.io.IOException {
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        Long newImageId;
        if (request != null && request.getType() == ImageEditRequest.EditType.CROP && request.getCrop() != null) {
            var crop = request.getCrop();
            newImageId = imageEditService.cropImage(id, userId, crop.getX(), crop.getY(),
                    crop.getWidth(), crop.getHeight());
        } else if (request != null && request.getType() == ImageEditRequest.EditType.ADJUST && request.getAdjust() != null) {
            var adjust = request.getAdjust();
            newImageId = imageEditService.adjustImageColors(id, userId,
                    adjust.getBrightness(), adjust.getContrast(), adjust.getSaturation());
        } else {
            throw new IllegalArgumentException("编辑参数无效");
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            Image newImage = imageService.getImageById(newImageId, userId);
            imageService.saveTags(newImage, request.getTags(), Tag.TagType.CUSTOM);
        }
        Image newImage = imageService.getImageById(newImageId, userId);
        return ApiResponse.success("编辑成功", toResponse(newImage));
    }

    @PutMapping("/{id}/tags")
    public ApiResponse<ImageResponse> updateImageTags(
            @PathVariable Long id,
            @RequestBody Map<String, List<String>> request,
            Authentication authentication) {
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        List<String> tags = request.get("tags");
        imageService.updateImageTags(id, userId, tags);
        Image image = imageService.getImageById(id, userId);
        return ApiResponse.success("标签更新成功", toResponse(image));
    }

    private ImageResponse toResponse(Image image) {
        List<Tag> tags = imageService.getImageTags(image.getId());
        List<String> customTags = tags.stream()
                .filter(t -> t.getType() == Tag.TagType.CUSTOM)
                .map(Tag::getName)
                .collect(Collectors.toList());
        
        List<String> exifTags = tags.stream()
                .filter(t -> t.getType() == Tag.TagType.EXIF)
                .map(Tag::getName)
                .collect(Collectors.toList());
        
        List<String> aiTags = tags.stream()
                .filter(t -> t.getType() == Tag.TagType.AI)
                .map(Tag::getName)
                .collect(Collectors.toList());

        // 合并所有标签
        List<String> allTags = new ArrayList<>();
        allTags.addAll(customTags);
        allTags.addAll(exifTags);
        allTags.addAll(aiTags);

        Map<String, Object> safeExifData = image.getExifData() == null
                ? new java.util.HashMap<>() : new java.util.HashMap<>(image.getExifData());
        safeExifData.keySet().removeIf(key -> key.toLowerCase().contains("gps")
                || key.toLowerCase().contains("latitude") || key.toLowerCase().contains("longitude")
                || key.toLowerCase().contains("location"));

        return new ImageResponse(
                image.getId(),
                image.getUserId(),
                image.getFilename(),
                image.getFileSize(),
                image.getMimeType(),
                image.getWidth(),
                image.getHeight(),
                image.getUploadTime(),
                safeExifData,
                image.getAiTags(),
                customTags,
                allTags,
                image.getStatus()
        );
    }

    public record ImageResponse(
            Long id,
            Long userId,
            String filename,
            Long fileSize,
            String mimeType,
            Integer width,
            Integer height,
            java.time.LocalDateTime uploadTime,
            Map<String, Object> exifData,
            Map<String, Object> aiTags,
            List<String> customTags,
            List<String> tags,
            String processingStatus
    ) {}

    public record CursorPage(List<ImageResponse> items, String nextCursor) {}
}
