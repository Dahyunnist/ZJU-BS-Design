package com.zju.imagemanger.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 图片编辑服务
 * 支持裁剪和色调调整
 */
@Service
public class ImageEditService {
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private ThumbnailGenerator thumbnailGenerator;
    
    @Autowired
    private com.zju.imagemanger.repository.ImageRepository imageRepository;
    
    @Autowired
    private ExifExtractor exifExtractor;
    
    @Value("${file.upload-dir}")
    private String uploadDir;
    
    /**
     * 裁剪图片
     * @param imageId 图片ID
     * @param userId 用户ID
     * @param x 裁剪起始X坐标
     * @param y 裁剪起始Y坐标
     * @param width 裁剪宽度
     * @param height 裁剪高度
     * @return 编辑后的图片ID（新图片）
     */
    @Transactional
    public Long cropImage(Long imageId, Long userId, int x, int y, int width, int height) throws IOException {
        var image = imageService.getImageById(imageId, userId);
        File originalFile = new File(image.getFilePath());
        
        if (!originalFile.exists()) {
            throw new IllegalArgumentException("原图文件不存在");
        }
        
        BufferedImage original = ImageIO.read(originalFile);
        if (original == null) {
            throw new IllegalArgumentException("无法读取图片");
        }
        
        // 验证裁剪参数
        if (x < 0 || y < 0 || width <= 0 || height <= 0 ||
            x + width > original.getWidth() || y + height > original.getHeight()) {
            throw new IllegalArgumentException("裁剪参数无效");
        }
        
        // 执行裁剪
        BufferedImage cropped = original.getSubimage(x, y, width, height);
        
        // 保存裁剪后的图片
        return saveEditedImage(cropped, image, "cropped");
    }
    
    /**
     * 调整图片色调
     * @param imageId 图片ID
     * @param userId 用户ID
     * @param brightness 亮度调整 (-100 到 100)
     * @param contrast 对比度调整 (-100 到 100)
     * @param saturation 饱和度调整 (-100 到 100)
     * @return 编辑后的图片ID（新图片）
     */
    @Transactional
    public Long adjustImageColors(Long imageId, Long userId, 
                                   int brightness, int contrast, int saturation) throws IOException {
        var image = imageService.getImageById(imageId, userId);
        File originalFile = new File(image.getFilePath());
        
        if (!originalFile.exists()) {
            throw new IllegalArgumentException("原图文件不存在");
        }
        
        BufferedImage original = ImageIO.read(originalFile);
        if (original == null) {
            throw new IllegalArgumentException("无法读取图片");
        }
        
        // 应用色调调整
        BufferedImage adjusted = applyColorAdjustments(original, brightness, contrast, saturation);
        
        // 保存调整后的图片
        return saveEditedImage(adjusted, image, "adjusted");
    }
    
    /**
     * 应用颜色调整
     */
    private BufferedImage applyColorAdjustments(BufferedImage original, 
                                                int brightness, int contrast, int saturation) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // 转换参数范围
        double brightnessFactor = 1.0 + (brightness / 100.0);
        double contrastFactor = (100.0 + contrast) / 100.0;
        double saturationFactor = 1.0 + (saturation / 100.0);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = original.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // 应用亮度
                r = (int) Math.min(255, Math.max(0, r * brightnessFactor));
                g = (int) Math.min(255, Math.max(0, g * brightnessFactor));
                b = (int) Math.min(255, Math.max(0, b * brightnessFactor));
                
                // 应用对比度
                r = (int) Math.min(255, Math.max(0, 128 + (r - 128) * contrastFactor));
                g = (int) Math.min(255, Math.max(0, 128 + (g - 128) * contrastFactor));
                b = (int) Math.min(255, Math.max(0, 128 + (b - 128) * contrastFactor));
                
                // 应用饱和度（简化实现）
                if (saturationFactor != 1.0) {
                    int gray = (r + g + b) / 3;
                    r = (int) Math.min(255, Math.max(0, gray + (r - gray) * saturationFactor));
                    g = (int) Math.min(255, Math.max(0, gray + (g - gray) * saturationFactor));
                    b = (int) Math.min(255, Math.max(0, gray + (b - gray) * saturationFactor));
                }
                
                int newRgb = (r << 16) | (g << 8) | b;
                result.setRGB(x, y, newRgb);
            }
        }
        
        return result;
    }
    
    /**
     * 保存编辑后的图片
     */
    private Long saveEditedImage(BufferedImage editedImage, 
                                 com.zju.imagemanger.entity.Image originalImage,
                                 String suffix) throws IOException {
        // 生成新文件名
        String filename = UUID.randomUUID().toString() + "_" + suffix + "_" + originalImage.getFilename();
        Path uploadPath = Paths.get(uploadDir, String.valueOf(originalImage.getUserId()));
        Files.createDirectories(uploadPath);
        
        Path filePath = uploadPath.resolve(filename);
        
        // 保存图片
        String format = originalImage.getMimeType() != null && originalImage.getMimeType().contains("png") 
            ? "png" : "jpg";
        ImageIO.write(editedImage, format, filePath.toFile());
        
        // 创建新的图片记录
        com.zju.imagemanger.entity.Image newImage = new com.zju.imagemanger.entity.Image();
        newImage.setUserId(originalImage.getUserId());
        newImage.setFilename(suffix + "_" + originalImage.getFilename());
        newImage.setFilePath(filePath.toString());
        newImage.setFileSize(Files.size(filePath));
        newImage.setMimeType(originalImage.getMimeType());
        newImage.setWidth(editedImage.getWidth());
        newImage.setHeight(editedImage.getHeight());
        
        // 复制EXIF和AI标签数据
        newImage.setExifData(originalImage.getExifData());
        newImage.setAiTags(originalImage.getAiTags());
        
        newImage = imageRepository.save(newImage);
        
        // 生成缩略图
        thumbnailGenerator.generateThumbnails(filePath.toFile(), newImage.getId().toString());
        
        return newImage.getId();
    }
    
}

