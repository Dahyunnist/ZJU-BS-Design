package com.zju.imagemanger.service;

import com.zju.imagemanger.config.QueueConfiguration;
import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.storage.StorageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

@Component
public class ImageProcessingWorker {
    private final ImageRepository images;
    private final ThumbnailGenerator thumbnails;
    private final ImageProcessingJobService jobs;
    private final StorageService storage;
    private final Semaphore workerSlots;

    public ImageProcessingWorker(ImageRepository images, ThumbnailGenerator thumbnails,
                                 ImageProcessingJobService jobs, StorageService storage,
                                 @Value("${app.worker.max-pool-size:4}") int maxConcurrency) {
        this.images = images;
        this.thumbnails = thumbnails;
        this.jobs = jobs;
        this.storage = storage;
        this.workerSlots = new Semaphore(Math.max(1, maxConcurrency));
    }

    @RabbitListener(queues = QueueConfiguration.PROCESSING_QUEUE,
            autoStartup = "${app.worker.enabled:true}")
    @CacheEvict(cacheNames = {"userImagePages", "emptySearches"}, allEntries = true, beforeInvocation = true)
    public void process(OutboxPublisher.ImageCreatedMessage message) {
        boolean acquired = false;
        try {
            workerSlots.acquire();
            acquired = true;
            processMessage(message);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for worker capacity", interrupted);
        } finally {
            if (acquired) workerSlots.release();
        }
    }

    private void processMessage(OutboxPublisher.ImageCreatedMessage message) {
        Image image = images.findById(message.imageId()).orElse(null);
        if (image == null || message.aggregateVersion() == null) return;
        if (!jobs.claim(image.getId(), message.aggregateVersion())) {
            if ("FAILED".equals(image.getStatus())
                    && image.getVersion() != null
                    && image.getVersion().equals(message.aggregateVersion() + 2)) {
                throw new IllegalStateException("Processing failed; retry policy will route event to DLQ");
            }
            return;
        }

        Long processingVersion = message.aggregateVersion() + 1;
        Path materialized = null;
        try {
            Path source = image.getFilePath() == null ? null : new File(image.getFilePath()).toPath();
            if (image.getObjectKey() != null && (source == null || !Files.isRegularFile(source)
                    || source.getFileName().toString().startsWith("image-object-"))) {
                source = storage.materialize(image.getObjectKey());
                if (source.getFileName().toString().startsWith("image-object-")) materialized = source;
            }
            if (source == null || !Files.isRegularFile(source)) {
                throw new IllegalStateException("图片源文件不存在");
            }
            thumbnails.generateThumbnails(source.toFile(), image.getId().toString());
            if (!jobs.finish(image.getId(), processingVersion, "READY")) {
                throw new IllegalStateException("Processing result is stale");
            }
        } catch (Exception exception) {
            jobs.finish(image.getId(), processingVersion, "FAILED");
            throw new IllegalStateException("Image processing failed", exception);
        } finally {
            if (materialized != null) {
                try { Files.deleteIfExists(materialized); } catch (IOException ignored) { }
            }
        }
    }
}
