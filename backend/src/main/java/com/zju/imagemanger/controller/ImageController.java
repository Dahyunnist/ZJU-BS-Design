package com.zju.imagemanger.controller;

import com.zju.imagemanger.dto.ApiResponse;
import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.entity.Tag;
import com.zju.imagemanger.service.ImageService;
import com.zju.imagemanger.service.UserService;
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

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    @PostMapping("/upload")
    public ApiResponse<Image> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            Long userId = userService.getUserByUsername(username).getId();
            
            Image image = imageService.uploadImage(file, userId, tags);
            return ApiResponse.success("上传成功", image);
        } catch (Exception e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<Page<ImageResponse>> getImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Image> images = imageService.getUserImages(userId, pageable);
        Page<ImageResponse> resp = images.map(this::toResponse);
        return ApiResponse.success(resp);
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
            @RequestParam(defaultValue = "600") String size) {
        try {
            // 缩略图允许直接访问，用于前端展示
            Image image = imageService.getImageById(id, null);
            
            String thumbnailPath = String.format("%s/%s/thumb_%s.jpg", thumbnailDir, id, size);
            File thumbnailFile = new File(thumbnailPath);
            
            if (!thumbnailFile.exists()) {
                // 如果缩略图不存在，返回原图
                File originalFile = new File(image.getFilePath());
                if (originalFile.exists()) {
                    Resource resource = new FileSystemResource(originalFile);
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(image.getMimeType()))
                            .body(resource);
                }
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(thumbnailFile);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 原图访问接口：详情页使用，避免缩略图生成失败导致无法查看
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getOriginalFile(@PathVariable Long id) {
        try {
            Image image = imageService.getImageById(id, null);
            File originalFile = new File(image.getFilePath());
            if (!originalFile.exists()) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(originalFile);
            String mime = image.getMimeType() != null ? image.getMimeType() : MediaType.IMAGE_JPEG_VALUE;
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Image> images = imageService.searchImages(userId, keyword, startDate, endDate, pageable);
        Page<ImageResponse> resp = images.map(this::toResponse);
        return ApiResponse.success(resp);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id, Authentication authentication) {
        try {
            String username = authentication.getName();
            Long userId = userService.getUserByUsername(username).getId();
            
            imageService.deleteImage(id, userId);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    private ImageResponse toResponse(Image image) {
        List<Tag> tags = imageService.getImageTags(image.getId());
        List<String> customTags = tags.stream()
                .filter(t -> t.getType() == Tag.TagType.CUSTOM)
                .map(Tag::getName)
                .collect(Collectors.toList());

        // 仅返回用户自定义标签
        return new ImageResponse(
                image.getId(),
                image.getUserId(),
                image.getFilename(),
                image.getFilePath(),
                image.getFileSize(),
                image.getMimeType(),
                image.getWidth(),
                image.getHeight(),
                image.getUploadTime(),
                image.getExifData(),
                image.getAiTags(),
                customTags,
                customTags // tags字段与customTags保持一致
        );
    }

    public record ImageResponse(
            Long id,
            Long userId,
            String filename,
            String filePath,
            Long fileSize,
            String mimeType,
            Integer width,
            Integer height,
            java.time.LocalDateTime uploadTime,
            Map<String, Object> exifData,
            Map<String, Object> aiTags,
            List<String> customTags,
            List<String> tags
    ) {}
}
