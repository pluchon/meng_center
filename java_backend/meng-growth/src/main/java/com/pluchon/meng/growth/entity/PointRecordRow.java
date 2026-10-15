package com.pluchon.meng.growth.entity;

import com.pluchon.meng.growth.entity.enums.PointSource;
import java.time.LocalDateTime;

// 数据库积分明细投影，时间按数据库统一的 UTC 存储解释
public record PointRecordRow(
        // 积分来源
        PointSource source,
        // 单笔积分，正数是获得，负数是花掉
        int amount,
        // 数据库统一的 UTC 创建时间
        LocalDateTime createTime) {}
