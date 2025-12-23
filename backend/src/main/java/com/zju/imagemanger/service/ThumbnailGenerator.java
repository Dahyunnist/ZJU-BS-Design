package com.zju.imagemanger.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class ThumbnailGenerator {
    
    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;
    
    public void generateThumbnails(File originalFile, String imageId) throws IOException {
        File thumbDir = new File(thumbnailDir, imageId);
        thumbDir.mkdirs();
        
        // 生成小图 (300x300)
        Thumbnails.of(originalFile)
                .size(300, 300)
                .outputFormat("jpg")
                .toFile(new File(thumbDir, "thumb_300.jpg"));
        
        // 生成中图 (600x600)
        Thumbnails.of(originalFile)
                .size(600, 600)
                .outputFormat("jpg")
                .toFile(new File(thumbDir, "thumb_600.jpg"));
        
        // 生成大图 (1200x1200)
        Thumbnails.of(originalFile)
                .size(1200, 1200)
                .outputFormat("jpg")
                .toFile(new File(thumbDir, "thumb_1200.jpg"));
    }
    
    public String getThumbnailPath(String imageId, String size) {
        return String.format("%s/%s/thumb_%s.jpg", thumbnailDir, imageId, size);
    }
}

