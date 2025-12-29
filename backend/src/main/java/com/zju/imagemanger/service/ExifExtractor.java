package com.zju.imagemanger.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ExifExtractor {
    
    /**
     * 提取EXIF信息
     */
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
            Date lastModified = new Date(imageFile.lastModified());
            exifData.put("lastModified", lastModified);
            
            // 从文件名或修改时间推断可能的拍摄时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            exifData.put("date", sdf.format(lastModified));
            
        } catch (Exception e) {
            // EXIF信息提取失败，至少返回文件大小和修改时间
            try {
                exifData.put("fileSize", imageFile.length());
                Date lastModified = new Date(imageFile.lastModified());
                exifData.put("lastModified", lastModified);
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                exifData.put("date", sdf.format(lastModified));
                
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
    
    /**
     * 从EXIF信息提取标签名称列表
     * 根据EXIF数据创建标签，如：日期、分辨率、文件类型等
     */
    public List<String> extractTagsFromExif(Map<String, Object> exifData) {
        List<String> tags = new ArrayList<>();
        
        if (exifData == null || exifData.isEmpty()) {
            return tags;
        }
        
        // 从日期创建标签（年份、月份）
        if (exifData.containsKey("date")) {
            String dateStr = exifData.get("date").toString();
            if (dateStr.length() >= 4) {
                String year = dateStr.substring(0, 4);
                tags.add(year + "年");
                
                if (dateStr.length() >= 7) {
                    String month = dateStr.substring(5, 7);
                    try {
                        int monthNum = Integer.parseInt(month);
                        String[] months = {"一月", "二月", "三月", "四月", "五月", "六月",
                                         "七月", "八月", "九月", "十月", "十一月", "十二月"};
                        if (monthNum >= 1 && monthNum <= 12) {
                            tags.add(months[monthNum - 1]);
                        }
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
        }
        
        // 从分辨率创建标签
        if (exifData.containsKey("width") && exifData.containsKey("height")) {
            Object widthObj = exifData.get("width");
            Object heightObj = exifData.get("height");
            
            if (widthObj instanceof Number && heightObj instanceof Number) {
                int width = ((Number) widthObj).intValue();
                int height = ((Number) heightObj).intValue();
                
                // 根据分辨率分类
                if (width >= 3840 || height >= 2160) {
                    tags.add("4K");
                } else if (width >= 1920 || height >= 1080) {
                    tags.add("高清");
                } else if (width >= 1280 || height >= 720) {
                    tags.add("标清");
                }
                
                // 根据宽高比分类
                double ratio = (double) width / height;
                if (Math.abs(ratio - 16.0/9) < 0.1) {
                    tags.add("16:9");
                } else if (Math.abs(ratio - 4.0/3) < 0.1) {
                    tags.add("4:3");
                } else if (Math.abs(ratio - 1.0) < 0.1) {
                    tags.add("1:1");
                } else if (ratio > 1.5) {
                    tags.add("横向");
                } else if (ratio < 0.7) {
                    tags.add("纵向");
                }
            }
        }
        
        // 从文件大小创建标签
        if (exifData.containsKey("fileSize")) {
            Object sizeObj = exifData.get("fileSize");
            if (sizeObj instanceof Number) {
                long size = ((Number) sizeObj).longValue();
                if (size > 5 * 1024 * 1024) { // 大于5MB
                    tags.add("大文件");
                } else if (size < 500 * 1024) { // 小于500KB
                    tags.add("小文件");
                }
            }
        }
        
        return tags;
    }
}

