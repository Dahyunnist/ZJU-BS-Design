package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.repository.ImageTagRepository;
import com.zju.imagemanger.repository.TagRepository;
import com.zju.imagemanger.repository.UploadReceiptRepository;
import com.zju.imagemanger.storage.StorageService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import com.zju.imagemanger.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageServiceSecurityTest {
    private final ImageRepository imageRepository = mock(ImageRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final ImageTagRepository imageTagRepository = mock(ImageTagRepository.class);
    private final ExifExtractor exifExtractor = mock(ExifExtractor.class);
    private final ThumbnailGenerator thumbnailGenerator = mock(ThumbnailGenerator.class);
    private final TagAnalysisService tagAnalysisService = mock(TagAnalysisService.class);
    private final StorageService storageService = mock(StorageService.class);
    private final UploadReceiptRepository uploadReceiptRepository = mock(UploadReceiptRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final UploadDeduplicationLock uploadDeduplicationLock = mock(UploadDeduplicationLock.class);
    private final ImageService imageService = new ImageService();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(imageService, "imageRepository", imageRepository);
        ReflectionTestUtils.setField(imageService, "tagRepository", tagRepository);
        ReflectionTestUtils.setField(imageService, "imageTagRepository", imageTagRepository);
        ReflectionTestUtils.setField(imageService, "exifExtractor", exifExtractor);
        ReflectionTestUtils.setField(imageService, "thumbnailGenerator", thumbnailGenerator);
        ReflectionTestUtils.setField(imageService, "tagAnalysisService", tagAnalysisService);
        ReflectionTestUtils.setField(imageService, "storageService", storageService);
        ReflectionTestUtils.setField(imageService, "uploadReceiptRepository", uploadReceiptRepository);
        ReflectionTestUtils.setField(imageService, "outboxEventRepository", outboxEventRepository);
        ReflectionTestUtils.setField(imageService, "uploadDeduplicationLock", uploadDeduplicationLock);
        when(uploadDeduplicationLock.acquire(anyLong(), anyString(), nullable(String.class))).thenReturn(List.of());
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        ReflectionTestUtils.setField(imageService, "cacheManager", cacheManager);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Path target = tempDir.resolve("uploads").resolve(key);
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy((java.io.InputStream) invocation.getArgument(1), target);
            return null;
        }).when(storageService).put(any(), any(), anyLong(), any());
        when(storageService.materialize(any())).thenAnswer(invocation -> tempDir.resolve("uploads").resolve(invocation.getArgument(0, String.class)));
        when(uploadReceiptRepository.findByOwnerIdAndIdempotencyKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(imageRepository.findByUserIdAndSha256(anyLong(), anyString())).thenReturn(Optional.empty());
        when(outboxEventRepository.countByStatus(anyString())).thenReturn(0L);
        ReflectionTestUtils.setField(imageService, "uploadDir", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(imageService, "thumbnailDir", tempDir.resolve("thumbnails").toString());
        ReflectionTestUtils.setField(imageService, "storageProvider", "local");
        ReflectionTestUtils.setField(imageService, "queueCapacity", 10_000L);
    }

    @Test
    void imageOwnerCanReadImage() {
        Image image = image(7L);
        when(imageRepository.findById(3L)).thenReturn(Optional.of(image));
        assertSame(image, imageService.getImageById(3L, 7L));
    }

    @Test
    void otherOwnerGetsNotFound() {
        when(imageRepository.findById(3L)).thenReturn(Optional.of(image(7L)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> imageService.getImageById(3L, 8L));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void missingImageGetsNotFound() {
        when(imageRepository.findById(3L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> imageService.getImageById(3L, 7L));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void retryProcessingIsScopedToImageOwnerAndCreatesNewOutboxEvent() {
        Image failed = image(7L);
        failed.setId(3L);
        failed.setStatus("FAILED");
        failed.setVersion(3L);
        when(imageRepository.findById(3L)).thenReturn(Optional.of(failed));
        when(imageRepository.saveAndFlush(failed)).thenAnswer(invocation -> {
            failed.setVersion(4L);
            return failed;
        });
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Image retried = imageService.retryFailedProcessing(3L, 7L);

        assertEquals("STORED", retried.getStatus());
        verify(outboxEventRepository).save(argThat(event -> event != null
                && Long.valueOf(3L).equals(event.getAggregateId())
                && Long.valueOf(4L).equals(event.getAggregateVersion())));
    }

    @Test
    void retryProcessingCannotAccessOtherOwnersImage() {
        Image failed = image(7L);
        failed.setId(3L);
        failed.setStatus("FAILED");
        when(imageRepository.findById(3L)).thenReturn(Optional.of(failed));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> imageService.retryFailedProcessing(3L, 8L));

        assertEquals(404, error.getStatusCode().value());
        verify(imageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void emptyUploadIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> imageService.uploadImage(file, 7L, List.of()));
    }

    @Test
    void oversizedUploadIsRejectedBeforeReading() throws Exception {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        assertThrows(IllegalArgumentException.class, () -> imageService.uploadImage(file, 7L, List.of()));
        verify(file, never()).getInputStream();
    }

    @Test
    void unsupportedMagicIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "<html>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> imageService.uploadImage(file, 7L, List.of()));
    }

    @Test
    void svgPayloadIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.svg", "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> imageService.uploadImage(file, 7L, List.of()));
    }

    @Test
    void webpSignatureIsRejectedUntilDecoderSupportExists() {
        MockMultipartFile file = new MockMultipartFile("file", "x.webp", "image/webp",
                new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'});
        assertThrows(IllegalArgumentException.class, () -> imageService.uploadImage(file, 7L, List.of()));
    }

    @Test
    void truncatedPngIsRejectedAndRemoved() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png",
                new byte[]{(byte) 0x89,'P','N','G',13,10,26,10});
        when(exifExtractor.extractExifInfo(any())).thenReturn(Map.of());
        assertThrows(RuntimeException.class, () -> imageService.uploadImage(file, 7L, List.of()));
        verify(imageRepository, never()).save(any(Image.class));
    }

    @Test
    void validPngAtOneHundredMegabyteLimitIsAccepted() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pV0AAAAASUVORK5CYII=");
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(100L * 1024 * 1024);
        when(file.getOriginalFilename()).thenReturn("limit.png");
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(png));
        when(exifExtractor.extractExifInfo(any())).thenReturn(new HashMap<>(Map.of("width", 1, "height", 1)));
        when(exifExtractor.extractTagsFromExif(any())).thenReturn(List.of());
        when(tagAnalysisService.analyzeImage(any())).thenReturn(Map.of("tags", List.of(), "source", "not_configured"));
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image saved = invocation.getArgument(0);
            saved.setId(13L);
            return saved;
        });

        assertEquals(13L, imageService.uploadImage(file, 7L, List.of()).getId());
    }

    @Test
    void uploadAboveOneHundredMegabytesIsRejectedBeforeReading() throws Exception {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        assertThrows(IllegalArgumentException.class, () -> imageService.uploadImage(file, 7L, List.of()));
        verify(file, never()).getInputStream();
    }

    @Test
    void windowsPathSegmentsAreRemovedFromDisplayName() throws Exception {
        Image image = upload("..\\..\\private.png", pngFile());
        assertEquals("private.png", image.getFilename());
        assertTrue(Path.of(image.getFilePath()).startsWith(tempDir.resolve("uploads").resolve("7")));
    }

    @Test
    void emptyDisplayNameGetsSafeFallback() throws Exception {
        Image image = upload("../", pngFile());
        assertEquals("image", image.getFilename());
    }

    @Test
    void longDisplayNameGetsSafeFallback() throws Exception {
        Image image = upload("a".repeat(256) + ".png", pngFile());
        assertEquals("image", image.getFilename());
    }

    @Test
    void controlCharactersAreRemovedFromDisplayName() throws Exception {
        Image image = upload("bad" + (char) 0 + "name.png", pngFile());
        assertEquals("bad_name.png", image.getFilename());
    }

    @Test
    void uploadedPathUsesGeneratedNameAndSanitizedDisplayName() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pV0AAAAASUVORK5CYII=");
        MockMultipartFile file = new MockMultipartFile("file", "../../private.png", "text/html", png);
        when(exifExtractor.extractExifInfo(any())).thenReturn(new HashMap<>(Map.of("width", 1, "height", 1)));
        when(exifExtractor.extractTagsFromExif(any())).thenReturn(List.of());
        when(tagAnalysisService.analyzeImage(any())).thenReturn(Map.of("tags", List.of(), "source", "not_configured"));
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        Image result = imageService.uploadImage(file, 7L, List.of());

        assertEquals("private.png", result.getFilename());
        assertTrue(Path.of(result.getFilePath()).startsWith(tempDir.resolve("uploads").resolve("7")));
        assertNotEquals("../../private.png", Path.of(result.getFilePath()).getFileName().toString());
        assertEquals("image/png", result.getMimeType());
        verify(outboxEventRepository).save(any());
    }

    private Image image(Long ownerId) {
        Image image = new Image();
        image.setUserId(ownerId);
        return image;
    }

    private byte[] pngFile() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pV0AAAAASUVORK5CYII=");
    }

    private Image upload(String filename, byte[] png) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/html", png);
        when(exifExtractor.extractExifInfo(any())).thenReturn(new HashMap<>(Map.of("width", 1, "height", 1)));
        when(exifExtractor.extractTagsFromExif(any())).thenReturn(List.of());
        when(tagAnalysisService.analyzeImage(any())).thenReturn(Map.of("tags", List.of(), "source", "not_configured"));
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image saved = invocation.getArgument(0);
            saved.setId(14L);
            return saved;
        });
        return imageService.uploadImage(file, 7L, List.of());
    }
}
