package com.zju.imagemanger.service;

import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

class StorageOrphanReaperTest {
    @Test
    void deletesOnlyOldUnreferencedApplicationObjects() throws Exception {
        ImageRepository images = mock(ImageRepository.class);
        StorageService storage = mock(StorageService.class);
        String known = "7/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png";
        String orphan = "8/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg";
        String recent = "9/cccccccc-cccc-cccc-cccc-cccccccccccc.gif";
        when(images.findAllObjectKeys()).thenReturn(List.of(known));
        when(storage.listObjects()).thenReturn(List.of(
                new StorageService.StoredObject(known, Instant.now().minus(Duration.ofDays(2))),
                new StorageService.StoredObject(orphan, Instant.now().minus(Duration.ofDays(2))),
                new StorageService.StoredObject(recent, Instant.now()),
                new StorageService.StoredObject("unmanaged/file.jpg", Instant.now().minus(Duration.ofDays(2)))));

        new StorageOrphanReaper(images, storage, Duration.ofHours(24)).reapOrphans();

        verify(storage).delete(orphan);
        verify(storage, never()).delete(known);
        verify(storage, never()).delete(recent);
        verify(storage, never()).delete("unmanaged/file.jpg");
    }
}
