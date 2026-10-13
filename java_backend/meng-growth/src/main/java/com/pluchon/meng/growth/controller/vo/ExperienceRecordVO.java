package com.pluchon.meng.growth.controller.vo;

import com.pluchon.meng.growth.entity.enums.ExpSource;
import java.time.OffsetDateTime;

// 经验明细只暴露来源、数量和时间，不暴露业务来源标识
public record ExperienceRecordVO(
        // 经验来源
        ExpSource source,
        // 获得经验
        int amount,
        // 流水建立时间
        OffsetDateTime time) {}
