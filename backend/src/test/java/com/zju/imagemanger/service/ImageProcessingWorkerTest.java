package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.repository.ImageRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import com.zju.imagemanger.storage.StorageService;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ImageProcessingWorkerTest {
    @Test
    void duplicateOrStaleMessageDoesNotOverwriteDeletedImage() throws Exception {
        ImageRepository images = mock(ImageRepository.class);
        ThumbnailGenerator thumbnails = mock(ThumbnailGenerator.class);
        ImageProcessingJobService jobs = mock(ImageProcessingJobService.class);
        StorageService storage = mock(StorageService.class);
        Image image = new Image();
        image.setId(5L);
        image.setStatus("PROCESSING");
        when(images.findById(5L)).thenReturn(Optional.of(image));
        when(jobs.claim(5L, 3L)).thenReturn(true);
        image.setFilePath("missing-worker-test-image.png");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                new ImageProcessingWorker(images, thumbnails, jobs, storage, 4)
                        .process(new OutboxPublisher.ImageCreatedMessage(8L, 5L, 3L)));

        verify(jobs).finish(5L, 4L, "FAILED");
    }

    @Test
    void rabbitMessageIsSerializable() {
        assertInstanceOf(java.io.Serializable.class,
                new OutboxPublisher.ImageCreatedMessage(1L, 2L, 3L));
    }

    @Test
    void duplicateOrStaleMessageDoesNotGenerateThumbnails() throws Exception {
        ImageRepository images = mock(ImageRepository.class);
        ThumbnailGenerator thumbnails = mock(ThumbnailGenerator.class);
        ImageProcessingJobService jobs = mock(ImageProcessingJobService.class);
        StorageService storage = mock(StorageService.class);
        Image image = new Image();
        image.setId(4L);
        image.setStatus("PROCESSING");
        when(images.findById(4L)).thenReturn(Optional.of(image));
        when(jobs.claim(4L, 2L)).thenReturn(false);

        new ImageProcessingWorker(images, thumbnails, jobs, storage, 4)
                .process(new OutboxPublisher.ImageCreatedMessage(7L, 4L, 2L));

        verifyNoInteractions(thumbnails);
        verify(jobs).claim(4L, 2L);
    }

    @Test
    void failedMessageIsRetriedForDeadLetterRouting() {
        ImageRepository images = mock(ImageRepository.class);
        ThumbnailGenerator thumbnails = mock(ThumbnailGenerator.class);
        ImageProcessingJobService jobs = mock(ImageProcessingJobService.class);
        StorageService storage = mock(StorageService.class);
        Image image = new Image();
        image.setId(9L);
        image.setStatus("FAILED");
        image.setVersion(5L);
        when(images.findById(9L)).thenReturn(Optional.of(image));
        when(jobs.claim(9L, 3L)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                new ImageProcessingWorker(images, thumbnails, jobs, storage, 4)
                        .process(new OutboxPublisher.ImageCreatedMessage(11L, 9L, 3L)));
    }
}
