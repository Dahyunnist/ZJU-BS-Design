package com.zju.imagemanger.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_tags")
@Data
@IdClass(ImageTagId.class)
public class ImageTag {
    @Id
    @Column(name = "image_id")
    private Long imageId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

