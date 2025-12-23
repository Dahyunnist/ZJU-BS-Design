package com.zju.imagemanger.repository;

import com.zju.imagemanger.entity.ImageTag;
import com.zju.imagemanger.entity.ImageTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageTagRepository extends JpaRepository<ImageTag, ImageTagId> {
    List<ImageTag> findByImageId(Long imageId);
    void deleteByImageId(Long imageId);
}

