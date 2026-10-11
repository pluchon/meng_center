package com.pluchon.meng.content.service;

import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.mapper.AuthorDailyStatMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 互动写入和当天取消与帖子计数共用同一个事务
@Service
public class AuthorStatisticsService {

    // 统计使用的业务时区
    public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    // 日期来源
    @Autowired
    private Clock clock;

    // 统计持久化
    @Autowired
    private AuthorDailyStatMapper stats;

    // 成功新增互动后原子加一
    @Transactional(
        propagation = Propagation.MANDATORY,
        rollbackFor = Exception.class
    )
    public void record(long author, CreatorMetric metric) {
        stats.incrementCount(
            author,
            LocalDate.now(clock.withZone(ZONE)),
            metric.column(),
            1
        );
    }

    // 只回减当天建立的关系，历史日期保持原值
    @Transactional(
        propagation = Propagation.MANDATORY,
        rollbackFor = Exception.class
    )
    public void cancel(
        long author,
        CreatorMetric metric,
        LocalDateTime created
    ) {
        var today = LocalDate.now(clock.withZone(ZONE));
        if (
            created != null &&
            created
                .toInstant(ZoneOffset.UTC)
                .atZone(ZONE)
                .toLocalDate()
                .equals(today)
        ) {
            stats.incrementCount(author, today, metric.column(), -1);
        }
    }
}
