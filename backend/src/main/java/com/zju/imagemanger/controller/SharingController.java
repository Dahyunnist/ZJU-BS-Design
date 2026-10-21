package com.zju.imagemanger.controller;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.dto.ApiResponse;
import com.zju.imagemanger.service.SharingService;
import com.zju.imagemanger.service.UserService;
import com.zju.imagemanger.storage.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@RestController
public class SharingController {
    private final SharingService sharing;
    private final UserService users;
    private final StorageService storage;

    public SharingController(SharingService sharing, UserService users, StorageService storage) {
        this.sharing = sharing;
        this.users = users;
        this.storage = storage;
    }

    @GetMapping("/organizations")
    public ApiResponse<List<SharingService.OrganizationView>> organizations(Authentication auth) {
        return ApiResponse.success(sharing.listOrganizations(userId(auth)));
    }

    @PostMapping("/organizations")
    public ApiResponse<SharingService.OrganizationView> createOrganization(@RequestBody NameRequest body, Authentication auth) {
        return ApiResponse.success(sharing.createOrganization(body.name(), userId(auth)));
    }

    @PostMapping("/organizations/{id}/members")
    public ApiResponse<Void> addMember(@PathVariable Long id, @RequestBody AddMemberRequest body, Authentication auth) {
        sharing.addMember(id, userId(auth), body.username(), body.role());
        return ApiResponse.success("成员已添加", null);
    }

    @DeleteMapping("/organizations/{id}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId, Authentication auth) {
        sharing.removeMember(id, userId(auth), memberId);
        return ApiResponse.success("成员已移除", null);
    }

    @GetMapping("/organizations/{id}/audit")
    public ApiResponse<List<SharingService.AuditView>> organizationAudit(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(sharing.organizationAudit(id, userId(auth)));
    }

    @PostMapping("/albums")
    public ApiResponse<SharingService.AlbumView> createAlbum(@RequestBody CreateAlbumRequest body, Authentication auth) {
        return ApiResponse.success(sharing.createAlbum(body.title(), body.organizationId(), userId(auth)));
    }

    @GetMapping("/albums/{id}")
    public ApiResponse<SharingService.AlbumView> getAlbum(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(sharing.getAlbum(id, userId(auth)));
    }

    @PostMapping("/albums/{albumId}/images/{imageId}")
    public ApiResponse<Void> addImage(@PathVariable Long albumId, @PathVariable Long imageId, Authentication auth) {
        sharing.addImage(albumId, imageId, userId(auth));
        return ApiResponse.success("图片已加入相册", null);
    }

    @DeleteMapping("/albums/{albumId}/images/{imageId}")
    public ApiResponse<Void> removeImage(@PathVariable Long albumId, @PathVariable Long imageId, Authentication auth) {
        sharing.removeImage(albumId, imageId, userId(auth));
        return ApiResponse.success("图片已移出相册", null);
    }

    @PostMapping("/albums/{id}/shares")
    public ApiResponse<SharingService.ShareView> createShare(@PathVariable Long id, @RequestBody ShareRequest body, Authentication auth) {
        return ApiResponse.success(sharing.createShare(id, userId(auth), body.expiryHours()));
    }

    @DeleteMapping("/shares/{id}")
    public ApiResponse<Void> revokeShare(@PathVariable Long id, Authentication auth) {
        sharing.revokeShare(id, userId(auth));
        return ApiResponse.success("分享已撤销", null);
    }

    @GetMapping("/shares/{token}")
    public ApiResponse<SharingService.AlbumView> getSharedAlbum(@PathVariable String token) {
        return ApiResponse.success(sharing.getSharedAlbum(token));
    }

    @GetMapping("/shares/{token}/images/{imageId}/file")
    public ResponseEntity<Resource> getSharedImage(@PathVariable String token, @PathVariable Long imageId) throws Exception {
        Image image = sharing.getSharedImage(token, imageId);
        Resource resource;
        if (image.getObjectKey() != null) {
            resource = new InputStreamResource(storage.get(image.getObjectKey()));
        } else {
            if (image.getFilePath() == null || !Files.isRegularFile(new File(image.getFilePath()).toPath())) {
                return ResponseEntity.notFound().build();
            }
            resource = new FileSystemResource(new File(image.getFilePath()));
        }
        return ResponseEntity.ok()
                .contentType(safeMediaType(image.getMimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    private Long userId(Authentication auth) {
        return users.getUserByUsername(auth.getName()).getId();
    }

    private MediaType safeMediaType(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase()) {
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "image/png" -> MediaType.IMAGE_PNG;
            case "image/gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    public record NameRequest(String name) {}
    public record AddMemberRequest(String username, String role) {}
    public record CreateAlbumRequest(String title, Long organizationId) {}
    public record ShareRequest(int expiryHours) {}
}
