package com.zju.imagemanger.repository;

import com.zju.imagemanger.entity.UploadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UploadReceiptRepository extends JpaRepository<UploadReceipt, Long> {
    Optional<UploadReceipt> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);
}
