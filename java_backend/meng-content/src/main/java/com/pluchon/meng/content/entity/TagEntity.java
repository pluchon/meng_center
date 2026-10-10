package com.pluchon.meng.content.entity;

import lombok.Data;

@Data
public class TagEntity {

    private Long id;
    private String name;
    private String nameKey;
    private Long useCount;
}
