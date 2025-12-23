package com.zju.imagemanger.repository;

import com.zju.imagemanger.entity.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    Page<Image> findByUserId(Long userId, Pageable pageable);
    
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

