package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.entity.Tag;
import com.zju.imagemanger.entity.ImageTag;
import com.zju.imagemanger.entity.UploadReceipt;
import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.repository.TagRepository;
import com.zju.imagemanger.repository.ImageTagRepository;
import com.zju.imagemanger.repository.UploadReceiptRepository;
import com.zju.imagemanger.repository.OutboxEventRepository;
import com.zju.imagemanger.entity.OutboxEvent;
import com.zju.imagemanger.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.Cacheable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImageService {
    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ImageTagRepository imageTagRepository;

    @Autowired
    private ExifExtractor exifExtractor;

    @Autowired
    private ThumbnailGenerator thumbnailGenerator;

    @Autowired
    private TagAnalysisService tagAnalysisService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private UploadReceiptRepository uploadReceiptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UploadDeduplicationLock uploadDeduplicationLock;

    @Autowired
    private CacheManager cacheManager;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    @Value("${storage.provider:local}")
    private String storageProvider;

    @Value("${app.queue.max-length:10000}")
    private long queueCapacity;

    @Transactional(rollbackFor = Exception.class)
    public Image uploadImage(MultipartFile file, Long userId, List<String> customTags) throws IOException {
        return uploadImage(file, userId, customTags, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Image uploadImage(MultipartFile file, Long userId, List<String> customTags, String idempotencyKey) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        if (file.getSize() > 100L * 1024 * 1024) {
            throw new IllegalArgumentException("图片不能超过100MB");
        }
        if (idempotencyKey != null && idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key 不能超过128字符");
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("image");
        String basename = originalName.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        String safeName = basename.replaceAll("[\\p{Cntrl}]", "_");
        if (safeName.isBlank() || safeName.length() > 255) {
            safeName = "image";
        }
        String extension = detectImageExtension(file);
        Path staged = Files.createTempFile("image-upload-", "." + extension);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (InputStream input = file.getInputStream(); var output = Files.newOutputStream(staged)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (IOException e) { Files.deleteIfExists(staged); throw e; }
        String sha256 = HexFormat.of().formatHex(digest.digest());
        try {
        uploadDeduplicationLock.acquire(userId, sha256, idempotencyKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<UploadReceipt> receipt = uploadReceiptRepository.findByOwnerIdAndIdempotencyKey(userId, idempotencyKey);
            if (receipt.isPresent()) {
                Files.deleteIfExists(staged);
                if (!receipt.get().getSha256().equals(sha256)) throw new ResponseStatusException(HttpStatus.CONFLICT, "幂等键已用于其他文件");
                return getImageById(receipt.get().getImageId(), userId);
            }
        }
        Optional<Image> duplicate = imageRepository.findByUserIdAndSha256(userId, sha256);
        if (duplicate.isPresent()) {
            Files.deleteIfExists(staged);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) saveReceipt(userId, idempotencyKey, sha256, duplicate.get().getId());
            return duplicate.get();
        }
        long activeJobs = outboxEventRepository.countByStatus("PENDING")
                + outboxEventRepository.countByStatus("PUBLISHING");
        if (activeJobs >= queueCapacity) {
            Files.deleteIfExists(staged);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "图片处理队列已满，请稍后重试");
        }
        String objectKey = userId + "/" + UUID.randomUUID() + "." + extension;
        Path filePath;
        try {
            try (InputStream input = Files.newInputStream(staged)) {
                storageService.put(objectKey, input, Files.size(staged), "image/" + extension);
            }
            filePath = isObjectStorage()
                    ? null : storageService.materialize(objectKey);
        } catch (IOException e) {
            Files.deleteIfExists(staged);
            try { storageService.delete(objectKey); } catch (IOException ignored) { }
            throw e;
        }

        Long savedImageId = null;
        try {
            File metadataSource = filePath == null ? staged.toFile() : filePath.toFile();
            Map<String, Object> exifData = exifExtractor.extractExifInfo(metadataSource);
            Integer width = (Integer) exifData.get("width");
            Integer height = (Integer) exifData.get("height");
            if (width == null || height == null || width <= 0 || height <= 0
                    || (long) width * height > 20_000_000L) {
                throw new IllegalArgumentException("图片损坏或像素数超过2000万");
            }

            // EXIF extractor validates image decoding before metadata persistence.

            Map<String, Object> aiTags = tagAnalysisService.analyzeImage(
                    metadataSource);

            Image image = new Image();
            image.setUserId(userId);
            image.setFilename(safeName);
            image.setFilePath(filePath == null ? null : filePath.toString());
            image.setObjectKey(objectKey);
            image.setSha256(sha256);
            image.setStatus("STORED");
            image.setFileSize(Files.size(staged));
            image.setMimeType("image/" + extension);
            image.setWidth(width);
            image.setHeight(height);
            image.setExifData(exifData);
            image.setAiTags(aiTags);
            image = imageRepository.save(image);
            savedImageId = image.getId();
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(image.getId());
            event.setAggregateVersion(image.getVersion());
            event.setType("IMAGE_PROCESSING_REQUESTED");
            outboxEventRepository.save(event);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) saveReceipt(userId, idempotencyKey, sha256, image.getId());

            List<String> customTagList = new ArrayList<>();
            if (customTags != null) {
                customTags.stream().filter(Objects::nonNull).map(String::trim)
                        .filter(tag -> !tag.isEmpty()).forEach(customTagList::add);
            }
            saveTags(image, customTagList, Tag.TagType.CUSTOM);
            List<String> exifTags = exifExtractor.extractTagsFromExif(exifData);
            if (!exifTags.isEmpty()) saveTags(image, exifTags, Tag.TagType.EXIF);
            evictSearchCaches();
            return image;
        } catch (IOException | RuntimeException e) {
            if (filePath != null && !isObjectStorage()) Files.deleteIfExists(filePath);
            if (savedImageId == null) {
                try { storageService.delete(objectKey); } catch (IOException ignored) { }
            }
            if (savedImageId != null) {
                Path thumbnails = Paths.get(thumbnailDir, savedImageId.toString());
                if (Files.exists(thumbnails)) {
                    try (var paths = Files.walk(thumbnails)) {
                        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                        });
                    }
                }
            }
            throw e;
        } finally {
            Files.deleteIfExists(staged);
        }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private void saveReceipt(Long ownerId, String key, String sha256, Long imageId) {
        UploadReceipt receipt = new UploadReceipt();
        receipt.setOwnerId(ownerId);
        receipt.setIdempotencyKey(key);
        receipt.setSha256(sha256);
        receipt.setImageId(imageId);
        uploadReceiptRepository.save(receipt);
    }

    private boolean isObjectStorage() {
        return "minio".equalsIgnoreCase(storageProvider);
    }

    private void evictSearchCaches() {
        for (String cacheName : List.of("userImagePages", "emptySearches")) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }

    @jakarta.annotation.PostConstruct
    void validateStorageConfiguration() {
        if (!List.of("local", "minio").contains(storageProvider.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Unsupported storage provider: " + storageProvider);
        }
    }

    private String detectImageExtension(MultipartFile file) throws IOException {
        byte[] header = new byte[12];
        try (var input = file.getInputStream()) {
            int read = input.read(header);
            if (read >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff) return "jpeg";
            if (read >= 8 && header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N'
                    && header[3] == 'G' && header[4] == 13 && header[5] == 10
                    && header[6] == 26 && header[7] == 10) return "png";
            if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') return "gif";
        }
        throw new IllegalArgumentException("仅支持有效的 JPEG、PNG 或 GIF 图片");
    }

    public Page<Image> getUserImages(Long userId, Pageable pageable) {
        return imageRepository.findByUserId(userId, pageable);
    }

    public Page<Image> getUserImagesAfter(Long userId, LocalDateTime cursorTime, Long cursorId, Pageable pageable) {
        return imageRepository.findLatestBeforeCursor(userId, cursorTime, cursorId, pageable);
    }

    public Page<Image> getFailedImages(Long userId, Pageable pageable) {
        return imageRepository.findByUserIdAndStatus(userId, "FAILED", pageable);
    }

    @Transactional
    @CacheEvict(cacheNames = {"userImagePages", "emptySearches"}, allEntries = true, beforeInvocation = true)
    public Image retryFailedProcessing(Long imageId, Long userId) {
        Image image = getImageById(imageId, userId);
        if (!"FAILED".equals(image.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有失败的图片可以重试");
        }
        image.setStatus("STORED");
        image = imageRepository.saveAndFlush(image);
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(image.getId());
        event.setAggregateVersion(image.getVersion());
        event.setType("IMAGE_PROCESSING_REQUESTED");
        outboxEventRepository.save(event);
        return image;
    }

    public Image getImageById(Long id, Long userId) {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
        if (!image.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return image;
    }

    public Page<Image> searchImages(Long userId, String keyword, LocalDateTime startDate, 
                                   LocalDateTime endDate, Pageable pageable) {
        return imageRepository.searchImages(userId, keyword, startDate, endDate, pageable);
    }

    @Cacheable(cacheNames = "emptySearches", key = "#userId + ':' + #keyword + ':' + #startDate + ':' + #endDate + ':' + #pageable.pageNumber + ':' + #pageable.pageSize", unless = "#result != null && !#result.isEmpty()")
    public Page<Image> searchImagesWithNegativeCache(Long userId, String keyword, LocalDateTime startDate,
                                                       LocalDateTime endDate, Pageable pageable) {
        return imageRepository.searchImages(userId, keyword, startDate, endDate, pageable);
    }

    @Transactional
    @CacheEvict(cacheNames = {"userImagePages", "emptySearches"}, allEntries = true, beforeInvocation = true)
    public void deleteImage(Long id, Long userId) throws IOException {
        Image image = getImageById(id, userId);
        
        // 删除文件
        if (image.getFilePath() != null && image.getObjectKey() == null) {
            Files.deleteIfExists(Paths.get(image.getFilePath()));
        }
        if (image.getObjectKey() != null) storageService.delete(image.getObjectKey());
        
        // 删除缩略图目录
        Path thumbDir = Paths.get("thumbnails", id.toString());
        if (Files.exists(thumbDir)) {
            try (var paths = Files.walk(thumbDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
        
        // 删除标签关联
        imageTagRepository.deleteByImageId(id);
        
        // 删除图片记录
        imageRepository.delete(image);
    }

    public List<Tag> getImageTags(Long imageId) {
        List<ImageTag> imageTags = imageTagRepository.findByImageId(imageId);
        List<Long> tagIds = imageTags.stream()
                .map(ImageTag::getTagId)
                .collect(Collectors.toList());
        return tagRepository.findAllById(tagIds);
    }

    @Transactional
    public void updateImageTags(Long imageId, Long userId, List<String> customTags) {
        Image image = getImageById(imageId, userId);
        
        // 获取所有现有的标签关联
        List<ImageTag> allImageTags = imageTagRepository.findByImageId(imageId);
        List<Tag> allTags = tagRepository.findAllById(
            allImageTags.stream().map(ImageTag::getTagId).collect(Collectors.toList())
        );
        
        // 删除旧的用户自定义标签关联
        for (ImageTag imageTag : allImageTags) {
            Tag tag = allTags.stream()
                    .filter(t -> t.getId().equals(imageTag.getTagId()))
                    .findFirst()
                    .orElse(null);
            if (tag != null && tag.getType() == Tag.TagType.CUSTOM) {
                imageTagRepository.delete(imageTag);
            }
        }
        
        // 保存新的自定义标签
        if (customTags != null && !customTags.isEmpty()) {
            saveTags(image, customTags, Tag.TagType.CUSTOM);
        }
        evictSearchCaches();
    }

    public void saveTags(Image image, List<String> names, Tag.TagType type) {
        if (names == null || names.isEmpty()) {
            return;
        }
        // 去重
        Set<String> unique = new LinkedHashSet<>(names);
        for (String tagName : unique) {
            Tag tag = tagRepository.findByNameAndType(tagName, type)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(tagName);
                        newTag.setType(type);
                        return tagRepository.save(newTag);
                    });

            ImageTag imageTag = new ImageTag();
            imageTag.setImageId(image.getId());
            imageTag.setTagId(tag.getId());
            imageTagRepository.save(imageTag);
        }
    }
}

