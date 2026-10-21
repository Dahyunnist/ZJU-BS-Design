package com.zju.imagemanger.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * Placeholder for a future image recognition provider.
 */
@Service
public class TagAnalysisService {
    
    /**
     * No visual model is configured; do not present filename guesses as AI output.
     */
    public Map<String, Object> analyzeImage(File imageFile) {
        Map<String, Object> result = new HashMap<>();
        result.put("tags", Collections.emptyList());
        result.put("source", "not_configured");
        return result;
    }
}

