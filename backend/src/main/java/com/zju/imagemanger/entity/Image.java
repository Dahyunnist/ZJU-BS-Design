package com.zju.imagemanger.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "images", uniqueConstraints = @UniqueConstraint(name = "uk_images_user_sha256", columnNames = {"user_id", "sha256"}),
        indexes = @Index(name = "idx_images_owner_time_id", columnList = "user_id, upload_time, id"))
@Data
public class Image implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(length = 500)
    private String filePath;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(length = 64)
    private String sha256;

    @Column(length = 20)
    private String status;

    @Version
    private Long version = 0L;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 50)
    private String mimeType;

    private Integer width;
    private Integer height;

    @Column(name = "upload_time")
    private LocalDateTime uploadTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> exifData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> aiTags;

    @PrePersist
    protected void onCreate() {
        uploadTime = LocalDateTime.now();
    }
}

