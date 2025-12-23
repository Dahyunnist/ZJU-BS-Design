package com.zju.imagemanger.entity;

import lombok.Data;
import java.io.Serializable;

@Data
public class ImageTagId implements Serializable {
    private Long imageId;
    private Long tagId;
}

