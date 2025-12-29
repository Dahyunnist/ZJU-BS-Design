package com.zju.imagemanger.dto;

import lombok.Data;
import java.util.List;

/**
 * 图片编辑请求DTO
 */
@Data
public class ImageEditRequest {
    /**
     * 编辑类型：CROP（裁剪）或 ADJUST（色调调整）
     */
    private EditType type;
    
    /**
     * 裁剪参数
     */
    private CropParams crop;
    
    /**
     * 色调调整参数
     */
    private ColorAdjustParams adjust;
    
    /**
     * 标签列表（可选，用于编辑后添加标签）
     */
    private List<String> tags;
    
    public enum EditType {
        CROP, ADJUST
    }
    
    @Data
    public static class CropParams {
        private int x;
        private int y;
        private int width;
        private int height;
    }
    
    @Data
    public static class ColorAdjustParams {
        private int brightness = 0;  // -100 到 100
        private int contrast = 0;     // -100 到 100
        private int saturation = 0;   // -100 到 100
    }
}

