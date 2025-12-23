package com.zju.imagemanger.repository;

import com.zju.imagemanger.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByNameAndType(String name, Tag.TagType type);
    boolean existsByNameAndType(String name, Tag.TagType type);
}

