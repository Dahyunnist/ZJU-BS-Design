package com.zju.imagemanger.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Component
public class UploadDeduplicationLock {
    private final JdbcTemplate jdbc;

    public UploadDeduplicationLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> acquire(Long ownerId, String sha256, String idempotencyKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Upload deduplication lock requires an active transaction");
        }
        Set<String> keys = new TreeSet<>();
        keys.add(lockKey("content", ownerId, sha256));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            keys.add(lockKey("idempotency", ownerId, idempotencyKey));
        }
        List<String> acquired = new ArrayList<>();
        try {
            for (String key : keys) {
                Integer result = jdbc.queryForObject("SELECT GET_LOCK(?, 30)", Integer.class, key);
                if (result == null || result != 1) {
                    throw new IllegalStateException("Timed out acquiring upload deduplication lock");
                }
                acquired.add(key);
            }
        } catch (RuntimeException error) {
            release(acquired);
            throw error;
        }
        List<String> heldLocks = List.copyOf(acquired);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                release(heldLocks);
            }
        });
        return heldLocks;
    }

    private void release(List<String> keys) {
        for (int index = keys.size() - 1; index >= 0; index--) {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, keys.get(index));
        }
    }

    private String lockKey(String type, Long ownerId, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String scopedValue = type + ":" + ownerId + ":" + value;
            return java.util.HexFormat.of().formatHex(digest.digest(scopedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
