package com.pluchon.meng.content.entity.event;

import com.pluchon.meng.content.entity.enums.PostCategory;

// 帖子派生数据更新事件
public record PostReadModelEvent(
        // 记录主键
        long id,
        // 帖子分类
        PostCategory category,
        // 配图用途
        Kind kind,
        // 计数或分值增量
        long delta) {

    public enum Kind {
        PUBLISHED,
        UPDATED,
        DELETED,
        SCORE
    }
}
