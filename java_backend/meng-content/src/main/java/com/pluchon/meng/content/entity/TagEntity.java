package com.pluchon.meng.content.entity;

import lombok.Data;

// 系统标签持久化实体
@Data
public class TagEntity {

    // 记录主键
    private Long id;

    // 标签显示名称
    private String name;

    // 标签归一化检索名称
    private String nameKey;

    // 标签引用次数
    private Long useCount;
}
