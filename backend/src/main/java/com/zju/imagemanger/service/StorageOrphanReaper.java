package com.zju.imagemanger.service;

import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.storage.orphan-scan-enabled", havingValue = "true", matchIfMissing = true)
public class StorageOrphanReaper {
    private static final Logger logger = LoggerFactory.getLogger(StorageOrphanReaper.class);
    private static final Pattern APPLICATION_OBJECT_KEY =
            Pattern.compile("^[0-9]+/[0-9a-fA-F-]{36}\\.(jpg|png|gif)$");

    private final ImageRepository images;
    private final StorageService storage;
    private final Duration minimumAge;

    public StorageOrphanReaper(ImageRepository images, StorageService storage,
                               @Value("${app.storage.orphan-min-age:24h}") Duration minimumAge) {
        this.images = images;
        this.storage = storage;
        this.minimumAge = minimumAge;
    }

    @Scheduled(initialDelayString = "${app.storage.orphan-scan-initial-delay:600000}",
            fixedDelayString = "${app.storage.orphan-scan-delay:604800000}")
    public void reapOrphans() {
        try {
            Set<String> knownKeys = new HashSet<>(images.findAllObjectKeys());
            Instant cutoff = Instant.now().minus(minimumAge);
            int deleted = 0;
            for (StorageService.StoredObject object : storage.listObjects()) {
                if (!APPLICATION_OBJECT_KEY.matcher(object.objectKey()).matches()
                        || !object.lastModified().isBefore(cutoff)
                        || knownKeys.contains(object.objectKey())) continue;
                storage.delete(object.objectKey());
                deleted++;
                logger.warn("Removed orphan storage object key={}", object.objectKey());
            }
            logger.info("Storage orphan scan completed known={} deleted={}", knownKeys.size(), deleted);
        } catch (IOException error) {
            logger.error("Storage orphan scan failed; objects were left unchanged", error);
        }
    }
}
