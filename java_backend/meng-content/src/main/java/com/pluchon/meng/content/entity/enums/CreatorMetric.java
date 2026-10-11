package com.pluchon.meng.content.entity.enums;

// 可更新的作者统计列，只允许枚举声明的列名
public enum CreatorMetric {
    VIEW("view_count"),
    LIKE("like_count"),
    COMMENT("comment_count"),
    FAVORITE("favorite_count");

    // 数据库统计列
    private final String column;

    CreatorMetric(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
