package com.zju.imagemanger.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class ExifExtractor {
    
    public Map<String, Object> extractExifInfo(File imageFile) {
        Map<String, Object> exifData = new HashMap<>();
        
        try {
            // 使用Java标准库提取图片尺寸
            BufferedImage img = ImageIO.read(imageFile);
            if (img != null) {
                exifData.put("width", img.getWidth());
                exifData.put("height", img.getHeight());
            }
            
            // 尝试提取EXIF信息（使用ImageIO）
            try (ImageInputStream iis = ImageIO.createImageInputStream(imageFile)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    reader.setInput(iis);
                    IIOMetadata metadata = reader.getImageMetadata(0);
                    
                    // 标记有元数据
                    if (metadata != null) {
                        exifData.put("hasMetadata", true);
                    }
                }
            }
            
            // 添加文件信息
            exifData.put("fileSize", imageFile.length());
            exifData.put("lastModified", new java.util.Date(imageFile.lastModified()));
            
        } catch (Exception e) {
            // EXIF信息提取失败，至少返回文件大小和修改时间
            try {
                exifData.put("fileSize", imageFile.length());
                exifData.put("lastModified", new java.util.Date(imageFile.lastModified()));
                
                // 尝试用ImageIO读取尺寸
                BufferedImage img = ImageIO.read(imageFile);
                if (img != null) {
                    exifData.put("width", img.getWidth());
                    exifData.put("height", img.getHeight());
                }
            } catch (Exception ex) {
                // 忽略所有错误
            }
        }
        
        return exifData;
    }
}

