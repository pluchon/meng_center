package com.pluchon.meng.growth.entity;

import com.pluchon.meng.growth.entity.enums.ExpSource;
import java.time.LocalDateTime;

// 数据库经验明细投影，时间按数据库统一的 UTC 存储解释
public record ExperienceRecordRow(
        // 奖励来源
        ExpSource source,
        // 单笔经验
        int amount,
        // 数据库统一的 UTC 创建时间
        LocalDateTime createTime) {}
