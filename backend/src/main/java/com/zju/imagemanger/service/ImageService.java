package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.entity.Tag;
import com.zju.imagemanger.entity.ImageTag;
import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.repository.TagRepository;
import com.zju.imagemanger.repository.ImageTagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
    private AiTagService aiTagService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Transactional
    public Image uploadImage(MultipartFile file, Long userId, List<String> customTags) throws IOException {
        // 保存文件
        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path uploadPath = Paths.get(uploadDir, String.valueOf(userId));
        Files.createDirectories(uploadPath);
        
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, file.getBytes());

        // 提取EXIF信息，并校验图片是否可被解析（需有宽高）
        Map<String, Object> exifData = exifExtractor.extractExifInfo(filePath.toFile());
        boolean hasImageSize = exifData.containsKey("width") && exifData.containsKey("height");
        if (!hasImageSize) {
            Files.deleteIfExists(filePath);
            throw new IllegalArgumentException("图片格式不支持或文件损坏，请使用常见的 JPEG/PNG/GIF 图片重试");
        }

        // AI分析图片（增强功能）
        Map<String, Object> aiTags = aiTagService.analyzeImage(filePath.toFile());

        // 创建图片记录
        Image image = new Image();
        image.setUserId(userId);
        image.setFilename(file.getOriginalFilename());
        image.setFilePath(filePath.toString());
        image.setFileSize(file.getSize());
        image.setMimeType(file.getContentType());
        
        // 从EXIF获取尺寸
        if (exifData.containsKey("width")) {
            Object width = exifData.get("width");
            if (width instanceof Integer) {
                image.setWidth((Integer) width);
            } else if (width instanceof Number) {
                image.setWidth(((Number) width).intValue());
            }
        }
        if (exifData.containsKey("height")) {
            Object height = exifData.get("height");
            if (height instanceof Integer) {
                image.setHeight((Integer) height);
            } else if (height instanceof Number) {
                image.setHeight(((Number) height).intValue());
            }
        }
        
        image.setExifData(exifData);
        image.setAiTags(aiTags);
        image = imageRepository.save(image);

        // 生成缩略图
        thumbnailGenerator.generateThumbnails(filePath.toFile(), image.getId().toString());

        // 保存用户自定义标签
        List<String> customTagList = new ArrayList<>();
        if (customTags != null) {
            customTags.forEach(t -> {
                if (t != null) {
                    String v = t.trim();
                    if (!v.isEmpty()) {
                        customTagList.add(v);
                    }
                }
            });
        }
        saveTags(image, customTagList, Tag.TagType.CUSTOM);

        // 从EXIF信息自动创建标签
        List<String> exifTags = exifExtractor.extractTagsFromExif(exifData);
        if (!exifTags.isEmpty()) {
            saveTags(image, exifTags, Tag.TagType.EXIF);
        }

        return image;
    }

    public Page<Image> getUserImages(Long userId, Pageable pageable) {
        return imageRepository.findByUserId(userId, pageable);
    }

    public Image getImageById(Long id, Long userId) {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("图片不存在"));
        if (userId != null && !image.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该图片");
        }
        return image;
    }

    public Page<Image> searchImages(Long userId, String keyword, LocalDateTime startDate, 
                                   LocalDateTime endDate, Pageable pageable) {
        return imageRepository.searchImages(userId, keyword, startDate, endDate, pageable);
    }

    @Transactional
    public void deleteImage(Long id, Long userId) throws IOException {
        Image image = getImageById(id, userId);
        
        // 删除文件
        Files.deleteIfExists(Paths.get(image.getFilePath()));
        
        // 删除缩略图目录
        Path thumbDir = Paths.get("thumbnails", id.toString());
        if (Files.exists(thumbDir)) {
            Files.walk(thumbDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
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

