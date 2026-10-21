package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.entity.User;
import com.zju.imagemanger.repository.ImageRepository;
import com.zju.imagemanger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cache.type=none",
        "app.cache.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "app.worker.enabled=false",
        "app.outbox.publisher-enabled=false",
        "app.outbox.poll-ms=60000",
        "jwt.secret=test-secret-key-with-at-least-32-characters-for-jwt"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SharingAclIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("image_db")
            .withUsername("imageuser")
            .withPassword("test-password");
    static final java.nio.file.Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("file.upload-dir", () -> STORAGE_ROOT.resolve("uploads").toString());
        registry.add("file.thumbnail-dir", () -> STORAGE_ROOT.resolve("thumbnails").toString());
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired SharingService sharing;
    @Autowired JdbcTemplate jdbc;
    @Autowired ImageRepository images;
    @Autowired UserRepository users;
    @Autowired ImageService imageService;
    @Autowired MockMvc mockMvc;

    private Long ownerId;
    private Long outsiderId;
    private Long organizationId;
    private Long albumId;
    private Long imageId;

    @BeforeEach
    void seedScenario() {
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM upload_receipts");
        jdbc.update("DELETE FROM share_links");
        jdbc.update("DELETE FROM album_images");
        jdbc.update("DELETE FROM albums");
        jdbc.update("DELETE FROM organization_memberships");
        jdbc.update("DELETE FROM organizations");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM users");

        ownerId = createUser("acl-owner").getId();
        outsiderId = createUser("acl-outsider").getId();
        organizationId = sharing.createOrganization("ACL test", ownerId).id();
        albumId = sharing.createAlbum("Private album", null, ownerId).id();

        Image image = new Image();
        image.setUserId(ownerId);
        image.setFilename("shared.png");
        image.setFilePath("/tmp/shared.png");
        image.setObjectKey("acl-test/shared.png");
        image.setSha256("a".repeat(64));
        image.setStatus("READY");
        image.setFileSize(1L);
        image.setMimeType("image/png");
        image.setWidth(1);
        image.setHeight(1);
        image.setExifData(Map.of());
        image.setAiTags(Map.of());
        imageId = images.saveAndFlush(image).getId();
    }

    @Test
    void privateAlbumAndShareAreScopedToOwnerAndMembership() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.getAlbum(albumId, outsiderId));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.createShare(albumId, outsiderId, 1));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.addImage(albumId, imageId, outsiderId));
    }

    @Test
    void organizationAdminMemberAndOutsiderFollowRoleMatrix() {
        User member = createUser("acl-member");
        User secondAdmin = createUser("acl-second-admin");
        sharing.addMember(organizationId, ownerId, member.getUsername(), "MEMBER");
        sharing.addMember(organizationId, ownerId, secondAdmin.getUsername(), "ADMIN");
        Long organizationAlbumId = sharing.createAlbum("Organization album", organizationId, ownerId).id();

        assertEquals(organizationAlbumId, sharing.getAlbum(organizationAlbumId, member.getId()).id());
        assertEquals("MEMBER", sharing.listOrganizations(member.getId()).stream()
                .filter(org -> org.id().equals(organizationId)).findFirst().orElseThrow().role());
        assertEquals("ADMIN", sharing.listOrganizations(secondAdmin.getId()).stream()
                .filter(org -> org.id().equals(organizationId)).findFirst().orElseThrow().role());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.addMember(organizationId, member.getId(), "acl-outsider", "MEMBER"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.organizationAudit(organizationId, member.getId()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.getAlbum(organizationAlbumId, outsiderId));

        Image memberImage = new Image();
        memberImage.setUserId(member.getId());
        memberImage.setFilename("member.png");
        memberImage.setFilePath("/tmp/member.png");
        memberImage.setObjectKey("acl-test/member.png");
        memberImage.setSha256("b".repeat(64));
        memberImage.setStatus("READY");
        memberImage.setFileSize(1L);
        memberImage.setMimeType("image/png");
        memberImage.setWidth(1);
        memberImage.setHeight(1);
        memberImage.setExifData(Map.of());
        memberImage.setAiTags(Map.of());
        Long memberImageId = images.saveAndFlush(memberImage).getId();
        try {
            sharing.addImage(organizationAlbumId, memberImageId, member.getId());
            assertTrue(sharing.getAlbum(organizationAlbumId, secondAdmin.getId()).images().stream()
                    .anyMatch(image -> image.id().equals(memberImageId)));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> sharing.removeImage(organizationAlbumId, memberImageId, member.getId()));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> sharing.createShare(organizationAlbumId, member.getId(), 1));

            SharingService.ShareView share = sharing.createShare(organizationAlbumId, secondAdmin.getId(), 1);
            sharing.revokeShare(share.id(), ownerId);
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> sharing.getSharedAlbum(share.token()));
            assertFalse(sharing.organizationAudit(organizationId, secondAdmin.getId()).isEmpty());

            sharing.removeMember(organizationId, ownerId, member.getId());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> sharing.getAlbum(organizationAlbumId, member.getId()));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> sharing.removeMember(organizationId, secondAdmin.getId(), secondAdmin.getId()));
        } finally {
            jdbc.update("DELETE FROM album_images WHERE image_id=?", memberImageId);
            jdbc.update("DELETE FROM images WHERE id=?", memberImageId);
        }
    }

    @Test
    @Transactional
    void sharedImageMustBelongToSharedAlbumAndRevokedShareFailsClosed() {
        sharing.addImage(albumId, imageId, ownerId);
        SharingService.ShareView share = sharing.createShare(albumId, ownerId, 1);
        assertEquals(imageId, sharing.getSharedImage(share.token(), imageId).getId());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.getSharedImage(share.token(), imageId + 1000));
        sharing.revokeShare(share.id(), ownerId);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> sharing.getSharedAlbum(share.token()));
    }

    @Test
    void concurrentSameContentUploadsWithSameIdempotencyKeyReturnOneImage() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8l8AAAAASUVORK5CYII=");
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<Image>> uploads = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> imageService.uploadImage(
                            new MockMultipartFile("file", "same.png", "image/png", png),
                            ownerId, List.of("concurrent-dedup"), "same-concurrent-key")))
                    .toList();
            List<Long> ids = new java.util.ArrayList<>();
            for (var upload : uploads) ids.add(upload.get(30, TimeUnit.SECONDS).getId());

            assertEquals(1, ids.stream().distinct().count());
            String sha256 = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(png));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM images WHERE user_id=? AND sha256=?", Integer.class, ownerId, sha256));
            for (Long imageId : ids.stream().distinct().toList()) {
                imageService.deleteImage(imageId, ownerId);
                jdbc.update("DELETE FROM outbox_events WHERE aggregate_id=?", imageId);
            }
            jdbc.update("DELETE FROM upload_receipts WHERE owner_id=?", ownerId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anonymousShareDownloadStreamsOriginalImageBytes() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8l8AAAAASUVORK5CYII=");
        Image uploaded = imageService.uploadImage(
                new MockMultipartFile("file", "shared-original.png", "image/png", png),
                ownerId, List.of(), null);
        SharingService.ShareView share = null;
        try {
            sharing.addImage(albumId, uploaded.getId(), ownerId);
            share = sharing.createShare(albumId, ownerId, 1);

            String servletPath = "/shares/" + share.token() + "/images/" + uploaded.getId() + "/file";
            mockMvc.perform(get(servletPath))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"))
                    .andExpect(content().bytes(png));
        } finally {
            if (share != null) sharing.revokeShare(share.id(), ownerId);
            imageService.deleteImage(uploaded.getId(), ownerId);
            jdbc.update("DELETE FROM outbox_events WHERE aggregate_id=?", uploaded.getId());
        }
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("unused-test-hash");
        return users.saveAndFlush(user);
    }

    private static java.nio.file.Path createStorageRoot() {
        try {
            return java.nio.file.Files.createTempDirectory("image-upload-lock-test-");
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void removeStorageRoot() throws Exception {
        if (java.nio.file.Files.exists(STORAGE_ROOT)) {
            try (var paths = java.nio.file.Files.walk(STORAGE_ROOT)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { java.nio.file.Files.deleteIfExists(path); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
            }
        }
    }
}
