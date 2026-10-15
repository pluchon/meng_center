package com.pluchon.meng.growth.controller.vo;

import com.pluchon.meng.growth.entity.enums.PointSource;
import java.time.OffsetDateTime;

// 积分明细只暴露来源、数量和时间，不暴露业务来源标识
public record PointRecordVO(
        // 积分来源
        PointSource source,
        // 单笔积分
        int amount,
        // 流水建立时间
        OffsetDateTime time) {}
