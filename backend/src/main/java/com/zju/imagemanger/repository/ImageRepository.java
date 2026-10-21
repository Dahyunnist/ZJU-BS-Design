package com.zju.imagemanger.repository;

import com.zju.imagemanger.entity.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    java.util.Optional<Image> findByUserIdAndSha256(Long userId, String sha256);
    @Query("SELECT i.objectKey FROM Image i WHERE i.objectKey IS NOT NULL")
    List<String> findAllObjectKeys();
    Page<Image> findByUserId(Long userId, Pageable pageable);
    Page<Image> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    @Query("SELECT i FROM Image i WHERE i.userId = :userId AND " +
           "(:cursorTime IS NULL OR i.uploadTime < :cursorTime OR (i.uploadTime = :cursorTime AND i.id < :cursorId)) " +
           "ORDER BY i.uploadTime DESC, i.id DESC")
    Page<Image> findLatestBeforeCursor(@Param("userId") Long userId,
                                      @Param("cursorTime") LocalDateTime cursorTime,
                                      @Param("cursorId") Long cursorId,
                                      Pageable pageable);

    @Modifying
    @Query(value = "UPDATE images SET status = 'PROCESSING', version = version + 1 WHERE id = :id AND version = :expectedVersion AND status = 'STORED'", nativeQuery = true)
    int claimForProcessing(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    @Modifying
    @Query(value = "UPDATE images SET status = :status, version = version + 1 WHERE id = :id AND version = :expectedVersion AND status = 'PROCESSING'", nativeQuery = true)
    int finishProcessing(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion, @Param("status") String status);
    
    @Query("SELECT i FROM Image i WHERE i.userId = :userId " +
           "AND (:keyword IS NULL OR i.filename LIKE %:keyword%) " +
           "AND (:startDate IS NULL OR i.uploadTime >= :startDate) " +
           "AND (:endDate IS NULL OR i.uploadTime <= :endDate)")
    Page<Image> searchImages(@Param("userId") Long userId,
                             @Param("keyword") String keyword,
                             @Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate,
                             Pageable pageable);
    
    @Query("SELECT DISTINCT i FROM Image i " +
           "JOIN ImageTag it ON i.id = it.imageId " +
           "JOIN Tag t ON it.tagId = t.id " +
           "WHERE i.userId = :userId AND t.name IN :tagNames")
    List<Image> findByTags(@Param("userId") Long userId, @Param("tagNames") List<String> tagNames);
}

