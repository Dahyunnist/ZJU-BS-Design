package com.zju.imagemanger.controller;

import com.zju.imagemanger.dto.ApiResponse;
import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.service.ImageService;
import com.zju.imagemanger.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keyword-based natural-language search endpoint. This is not an MCP protocol server.
 */
@RestController
@RequestMapping("/search/natural-language")
public class NaturalLanguageSearchController {
    @Autowired
    private ImageService imageService;

    @Autowired
    private UserService userService;

    /**
     * 自然语言搜索图片
     * 示例查询：
     * - "找出上周拍的风景照片"
     * - "显示所有包含人物的图片"
     * - "查找最近上传的图片"
     */
    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> naturalLanguageSearch(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String query = request.get("query");
            String username = authentication.getName();
            Long userId = userService.getUserByUsername(username).getId();

            // 简单的自然语言解析（实际应该使用NLP模型）
            Map<String, Object> searchParams = parseQuery(query);
            
            Pageable pageable = PageRequest.of(0, 20);
            Page<Image> images = imageService.searchImages(
                    userId,
                    (String) searchParams.get("keyword"),
                    (LocalDateTime) searchParams.get("startDate"),
                    (LocalDateTime) searchParams.get("endDate"),
                    pageable
            );

            Map<String, Object> result = new HashMap<>();
            result.put("images", images.getContent().stream().map(this::toResponse).collect(Collectors.toList()));
            result.put("total", images.getTotalElements());
            result.put("explanation", generateExplanation(query, searchParams));

            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error("搜索失败: " + e.getMessage());
        }
    }

    private ImageController.ImageResponse toResponse(Image image) {
        List<com.zju.imagemanger.entity.Tag> tags = imageService.getImageTags(image.getId());
        List<String> customTags = tags.stream().filter(tag -> tag.getType() == com.zju.imagemanger.entity.Tag.TagType.CUSTOM)
                .map(com.zju.imagemanger.entity.Tag::getName).collect(Collectors.toList());
        List<String> allTags = tags.stream().map(com.zju.imagemanger.entity.Tag::getName).collect(Collectors.toList());
        return new ImageController.ImageResponse(image.getId(), image.getUserId(), image.getFilename(),
                image.getFileSize(), image.getMimeType(), image.getWidth(), image.getHeight(), image.getUploadTime(),
                Map.of(), image.getAiTags(), customTags, allTags, image.getStatus());
    }

    /**
     * 简单的查询解析（示例实现）
     * 实际应该使用NLP模型进行更智能的解析
     */
    private Map<String, Object> parseQuery(String query) {
        Map<String, Object> params = new HashMap<>();
        
        // 时间相关关键词
        if (query.contains("上周") || query.contains("最近一周")) {
            params.put("startDate", LocalDateTime.now().minusWeeks(1));
        } else if (query.contains("上个月") || query.contains("最近一月")) {
            params.put("startDate", LocalDateTime.now().minusMonths(1));
        } else if (query.contains("最近") || query.contains("最新")) {
            params.put("startDate", LocalDateTime.now().minusDays(7));
        }

        // 标签相关关键词
        if (query.contains("风景") || query.contains("landscape")) {
            params.put("keyword", "风景");
        } else if (query.contains("人物") || query.contains("人像") || query.contains("person")) {
            params.put("keyword", "人物");
        } else if (query.contains("动物") || query.contains("animal")) {
            params.put("keyword", "动物");
        }

        return params;
    }

    /**
     * 生成搜索说明
     */
    private String generateExplanation(String query, Map<String, Object> params) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("根据查询 \"").append(query).append("\" 的解析结果：");
        
        if (params.containsKey("startDate")) {
            explanation.append(" 时间范围：").append(params.get("startDate"));
        }
        if (params.containsKey("keyword")) {
            explanation.append(" 关键词：").append(params.get("keyword"));
        }
        
        return explanation.toString();
    }
}

