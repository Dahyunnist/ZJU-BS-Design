package com.zju.imagemanger.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "upload_receipts", uniqueConstraints = @UniqueConstraint(name = "uk_upload_receipt_owner_key", columnNames = {"owner_id", "idempotency_key"}))
@Data
public class UploadReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "image_id", nullable = false)
    private Long imageId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
