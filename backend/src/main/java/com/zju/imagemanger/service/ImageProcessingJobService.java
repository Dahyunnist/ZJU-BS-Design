package com.zju.imagemanger.service;

import com.zju.imagemanger.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageProcessingJobService {
    private final ImageRepository images;

    public ImageProcessingJobService(ImageRepository images) {
        this.images = images;
    }

    @Transactional
    public boolean claim(Long imageId, Long expectedVersion) {
        return images.claimForProcessing(imageId, expectedVersion) == 1;
    }

    @Transactional
    public boolean finish(Long imageId, Long expectedVersion, String status) {
        return images.finishProcessing(imageId, expectedVersion, status) == 1;
    }
}
