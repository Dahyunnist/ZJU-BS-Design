package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.Image;
import com.zju.imagemanger.repository.ImageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SharingService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RowMapper<AlbumAccess> ALBUM_ACCESS = (rs, row) ->
            new AlbumAccess(rs.getLong("id"), rs.getLong("owner_id"), (Long) rs.getObject("organization_id"), rs.getString("title"));

    private final JdbcTemplate jdbc;
    private final ImageRepository images;

    public SharingService(JdbcTemplate jdbc, ImageRepository images) {
        this.jdbc = jdbc;
        this.images = images;
    }

    @Transactional
    public OrganizationView createOrganization(String name, Long actorId) {
        String safeName = validateText(name, 120, "组织名称");
        jdbc.update("INSERT INTO organizations(name, created_by, created_at) VALUES (?, ?, ?)", safeName, actorId, LocalDateTime.now());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO organization_memberships(organization_id,user_id,role,created_at) VALUES(?,?,?,?)",
                id, actorId, "ADMIN", LocalDateTime.now());
        audit(actorId, "ORGANIZATION_CREATED", "ORGANIZATION", id, safeName);
        return new OrganizationView(id, safeName, "ADMIN");
    }

    public List<OrganizationView> listOrganizations(Long userId) {
        return jdbc.query("SELECT o.id,o.name,m.role FROM organizations o JOIN organization_memberships m ON m.organization_id=o.id WHERE m.user_id=? ORDER BY o.id",
                (rs, row) -> new OrganizationView(rs.getLong("id"), rs.getString("name"), rs.getString("role")), userId);
    }

    @Transactional
    public void addMember(Long organizationId, Long actorId, String username, String role) {
        requireOrganizationRole(organizationId, actorId, "ADMIN");
        if (username == null || username.isBlank() || username.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名无效");
        }
        if (!List.of("ADMIN", "MEMBER").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "成员角色无效");
        Long targetId = jdbc.query("SELECT id FROM users WHERE username=?", rs -> rs.next() ? rs.getLong(1) : null, username);
        if (targetId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        if (Objects.equals(targetId, actorId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "不能重复添加自己");
        try {
            jdbc.update("INSERT INTO organization_memberships(organization_id,user_id,role,created_at) VALUES(?,?,?,?)",
                    organizationId, targetId, role, LocalDateTime.now());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户已是组织成员");
        }
        audit(actorId, "MEMBER_ADDED", "ORGANIZATION", organizationId, username + ":" + role);
    }

    @Transactional
    public void removeMember(Long organizationId, Long actorId, Long memberId) {
        requireOrganizationRole(organizationId, actorId, "ADMIN");
        if (Objects.equals(actorId, memberId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "管理员不能移除自己");
        String role = jdbc.query("SELECT role FROM organization_memberships WHERE organization_id=? AND user_id=?",
                rs -> rs.next() ? rs.getString(1) : null, organizationId, memberId);
        if (role == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在");
        if ("ADMIN".equals(role)) {
            Integer admins = jdbc.queryForObject("SELECT COUNT(*) FROM organization_memberships WHERE organization_id=? AND role='ADMIN'", Integer.class, organizationId);
            if (admins != null && admins <= 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "组织必须保留至少一名管理员");
        }
        jdbc.update("DELETE FROM organization_memberships WHERE organization_id=? AND user_id=?", organizationId, memberId);
        audit(actorId, "MEMBER_REMOVED", "ORGANIZATION", organizationId, "userId=" + memberId);
    }

    @Transactional
    public AlbumView createAlbum(String title, Long organizationId, Long actorId) {
        String safeTitle = validateText(title, 160, "相册名称");
        if (organizationId != null) requireOrganizationMember(organizationId, actorId);
        jdbc.update("INSERT INTO albums(organization_id,owner_id,title,created_at) VALUES(?,?,?,?)",
                organizationId, actorId, safeTitle, LocalDateTime.now());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit(actorId, "ALBUM_CREATED", "ALBUM", id, organizationId == null ? "private" : "organizationId=" + organizationId);
        return new AlbumView(id, safeTitle, organizationId, List.of());
    }

    @Transactional
    public void addImage(Long albumId, Long imageId, Long actorId) {
        AlbumAccess album = requireAlbumMember(albumId, actorId);
        Image image = images.findById(imageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
        if (!image.getUserId().equals(actorId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        jdbc.update("INSERT IGNORE INTO album_images(album_id,image_id,added_by,created_at) VALUES(?,?,?,?)",
                albumId, imageId, actorId, LocalDateTime.now());
        audit(actorId, "IMAGE_ADDED", "ALBUM", album.id(), "imageId=" + imageId);
    }

    @Transactional
    public void removeImage(Long albumId, Long imageId, Long actorId) {
        AlbumAccess album = requireAlbumManager(albumId, actorId);
        jdbc.update("DELETE FROM album_images WHERE album_id=? AND image_id=?", albumId, imageId);
        audit(actorId, "IMAGE_REMOVED", "ALBUM", album.id(), "imageId=" + imageId);
    }

    public AlbumView getAlbum(Long albumId, Long actorId) {
        AlbumAccess album = requireAlbumMember(albumId, actorId);
        return albumView(album, listAlbumImages(albumId));
    }

    @Transactional
    public ShareView createShare(Long albumId, Long actorId, int expiryHours) {
        AlbumAccess album = requireAlbumManager(albumId, actorId);
        if (expiryHours < 1 || expiryHours > 720) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分享有效期须为1至720小时");
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusHours(expiryHours);
        jdbc.update("INSERT INTO share_links(album_id,created_by,token_hash,expires_at,created_at) VALUES(?,?,?,?,?)",
                albumId, actorId, sha256(token), expires, now);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit(actorId, "SHARE_CREATED", "ALBUM", albumId, "shareId=" + id + ";expiresAt=" + expires);
        return new ShareView(id, token, expires);
    }

    @Transactional
    public void revokeShare(Long shareId, Long actorId) {
        ShareOwner share = jdbc.query("SELECT s.id,s.album_id,a.owner_id,a.organization_id FROM share_links s JOIN albums a ON a.id=s.album_id WHERE s.id=?",
                rs -> rs.next() ? new ShareOwner(rs.getLong("id"), rs.getLong("album_id"), rs.getLong("owner_id"), (Long) rs.getObject("organization_id")) : null,
                shareId);
        if (share == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分享不存在");
        requireCanManage(share.ownerId(), share.organizationId(), actorId);
        jdbc.update("UPDATE share_links SET revoked_at=COALESCE(revoked_at,?) WHERE id=?", LocalDateTime.now(), shareId);
        audit(actorId, "SHARE_REVOKED", "ALBUM", share.albumId(), "shareId=" + shareId);
    }

    public AlbumView getSharedAlbum(String token) {
        ShareAccess share = findValidShare(token);
        return albumView(share.album(), listAlbumImages(share.album().id()));
    }

    public Image getSharedImage(String token, Long imageId) {
        ShareAccess share = findValidShare(token);
        Integer inAlbum = jdbc.queryForObject("SELECT COUNT(*) FROM album_images WHERE album_id=? AND image_id=?",
                Integer.class, share.album().id(), imageId);
        if (inAlbum == null || inAlbum == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        Image image = images.findById(imageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
        if (!Objects.equals(image.getUserId(), share.album().ownerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return image;
    }

    public List<AuditView> organizationAudit(Long organizationId, Long actorId) {
        requireOrganizationRole(organizationId, actorId, "ADMIN");
        return jdbc.query("SELECT e.id,e.actor_id,e.action,e.resource_type,e.resource_id,e.detail,e.created_at " +
                        "FROM audit_events e WHERE (e.resource_type='ORGANIZATION' AND e.resource_id=?) " +
                        "OR (e.resource_type='ALBUM' AND e.resource_id IN (SELECT id FROM albums WHERE organization_id=?)) " +
                        "ORDER BY e.created_at DESC LIMIT 500",
                (rs, row) -> new AuditView(rs.getLong("id"), (Long) rs.getObject("actor_id"), rs.getString("action"),
                        rs.getString("resource_type"), rs.getLong("resource_id"), rs.getString("detail"), rs.getTimestamp("created_at").toLocalDateTime()),
                organizationId, organizationId);
    }

    private ShareAccess findValidShare(String token) {
        if (token == null || token.length() > 128) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分享无效或已过期");
        ShareAccess share = jdbc.query("SELECT a.id,a.owner_id,a.organization_id,a.title,s.expires_at,s.revoked_at " +
                        "FROM share_links s JOIN albums a ON a.id=s.album_id " +
                        "WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6)",
                rs -> rs.next() ? new ShareAccess(new AlbumAccess(rs.getLong("id"), rs.getLong("owner_id"),
                        (Long) rs.getObject("organization_id"), rs.getString("title")),
                        rs.getTimestamp("expires_at").toLocalDateTime()) : null,
                sha256(token));
        if (share == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分享无效或已过期");
        return share;
    }

    private List<Image> listAlbumImages(Long albumId) {
        List<Long> ids = jdbc.query("SELECT image_id FROM album_images WHERE album_id=? ORDER BY created_at DESC,image_id DESC",
                (rs, row) -> rs.getLong(1), albumId);
        Map<Long, Image> byId = new HashMap<>();
        images.findAllById(ids).forEach(image -> byId.put(image.getId(), image));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private AlbumView albumView(AlbumAccess album, List<Image> imagesInAlbum) {
        return new AlbumView(album.id(), album.title(), album.organizationId(), imagesInAlbum.stream().map(image ->
                new SharedImageView(image.getId(), image.getFilename(), image.getMimeType(), image.getFileSize(),
                        image.getWidth(), image.getHeight(), image.getStatus())).toList());
    }

    private AlbumAccess requireAlbumMember(Long albumId, Long userId) {
        AlbumAccess album = jdbc.query("SELECT id,owner_id,organization_id,title FROM albums WHERE id=?",
                rs -> rs.next() ? ALBUM_ACCESS.mapRow(rs, 0) : null, albumId);
        if (album == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "相册不存在");
        if (!Objects.equals(album.ownerId(), userId) && (album.organizationId() == null || !isMember(album.organizationId(), userId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "相册不存在");
        }
        return album;
    }

    private AlbumAccess requireAlbumManager(Long albumId, Long userId) {
        AlbumAccess album = requireAlbumMember(albumId, userId);
        requireCanManage(album.ownerId(), album.organizationId(), userId);
        return album;
    }

    private void requireCanManage(Long ownerId, Long organizationId, Long userId) {
        if (Objects.equals(ownerId, userId)) return;
        if (organizationId == null || !hasRole(organizationId, userId, "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在");
        }
    }

    private void requireOrganizationMember(Long organizationId, Long userId) {
        if (!isMember(organizationId, userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "组织不存在");
    }

    private void requireOrganizationRole(Long organizationId, Long userId, String role) {
        if (!hasRole(organizationId, userId, role)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "组织不存在");
    }

    private boolean isMember(Long organizationId, Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM organization_memberships WHERE organization_id=? AND user_id=?",
                Integer.class, organizationId, userId);
        return count != null && count > 0;
    }

    private boolean hasRole(Long organizationId, Long userId, String role) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM organization_memberships WHERE organization_id=? AND user_id=? AND role=?",
                Integer.class, organizationId, userId, role);
        return count != null && count > 0;
    }

    private void audit(Long actorId, String action, String type, Long resourceId, String detail) {
        jdbc.update("INSERT INTO audit_events(actor_id,action,resource_type,resource_id,detail,created_at) VALUES(?,?,?,?,?,?)",
                actorId, action, type, resourceId, detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)), LocalDateTime.now());
    }

    private String validateText(String text, int max, String field) {
        if (text == null || text.isBlank() || text.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "不能为空且长度不能超过" + max);
        }
        return text.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record OrganizationView(Long id, String name, String role) {}
    public record AlbumView(Long id, String title, Long organizationId, List<SharedImageView> images) {}
    public record SharedImageView(Long id, String filename, String mimeType, Long fileSize, Integer width, Integer height, String processingStatus) {}
    public record ShareView(Long id, String token, LocalDateTime expiresAt) {}
    public record AuditView(Long id, Long actorId, String action, String resourceType, Long resourceId, String detail, LocalDateTime createdAt) {}
    private record AlbumAccess(Long id, Long ownerId, Long organizationId, String title) {}
    private record ShareAccess(AlbumAccess album, LocalDateTime expiresAt) {}
    private record ShareOwner(Long id, Long albumId, Long ownerId, Long organizationId) {}
}
