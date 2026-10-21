package com.zju.imagemanger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UploadRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(UploadRateLimiter.class);
    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); " +
                    "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; " +
                    "return count", Long.class);

    private final StringRedisTemplate redis;
    private final int maxUploadsPerMinute;

    public UploadRateLimiter(StringRedisTemplate redis,
                             @Value("${app.upload.max-per-minute:20}") int maxUploadsPerMinute) {
        this.redis = redis;
        this.maxUploadsPerMinute = maxUploadsPerMinute;
    }

    public void check(Long userId) {
        long window = System.currentTimeMillis() / 60_000;
        try {
            Long count = redis.execute(INCREMENT_WINDOW,
                    List.of("upload-rate:" + userId + ":" + window), "120");
            if (count != null && count > maxUploadsPerMinute) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "上传过于频繁，请稍后重试");
            }
        } catch (DataAccessException unavailable) {
            logger.warn("Upload rate limiter unavailable; relying on ingress limit");
        }
    }
}
