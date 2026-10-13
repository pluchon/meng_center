package com.pluchon.meng.growth.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.growth.config.GrowthProperties;
import com.pluchon.meng.growth.controller.vo.CheckInMonthVO;
import com.pluchon.meng.growth.controller.vo.ExperienceRecordVO;
import com.pluchon.meng.growth.controller.vo.ExperienceStatsVO;
import com.pluchon.meng.growth.controller.vo.GrowthOverviewVO;
import com.pluchon.meng.growth.entity.SourceExperience;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.mapper.GrowthMapper;
import com.pluchon.meng.growth.mapper.GrowthPageMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 成长页查询不建账户、不加缓存，也不访问其他服务
@Service
@Transactional(readOnly = true, rollbackFor = Exception.class)
public class GrowthPageService {
    // 成长页只读聚合
    @Autowired
    private GrowthPageMapper pageMapper;
    // 共用签到日期查询
    @Autowired
    private GrowthMapper mapper;
    // 奖励与时区配置
    @Autowired
    private GrowthProperties properties;
    // 可替换的业务时钟
    @Autowired
    private Clock clock;
    // 连续签到按游标读取的批大小
    private static final int STREAK_BATCH = 128;

    // 返回配置等级和当前用户三项任务统计
    public GrowthOverviewVO overview() {
        long user = UserContext.require().userId();
        var today = today();
        var totals = new EnumMap<ExpSource, SourceExperience>(ExpSource.class);
        pageMapper.selectTaskExperience(user, today).forEach(row -> totals.put(row.source(), row));
        var tasks = new ArrayList<GrowthOverviewVO.Task>();
        for (var source : ExpSource.values()) {
            var row = totals.getOrDefault(source, new SourceExperience(source, 0, 0, 0));
            int reward = switch (source) {
                case CHECK_IN -> properties.checkInExp();
                case POST -> properties.postExp();
                case COMMENT -> properties.commentExp();
            };
            int maximum = switch (source) {
                case CHECK_IN -> 1;
                case POST -> properties.postDailyMax();
                case COMMENT -> properties.commentDailyMax();
            };
            tasks.add(new GrowthOverviewVO.Task(source, reward, maximum, row.count(), row.earned(), row.activeDays()));
        }
        var levels = new ArrayList<GrowthOverviewVO.Level>();
        for (int index = 0; index < properties.thresholds().size(); index++) {
            levels.add(new GrowthOverviewVO.Level(index + 1, properties.thresholds().get(index)));
        }
        return new GrowthOverviewVO(today, properties.timezone(), List.copyOf(levels), List.copyOf(tasks));
    }

    // 校验月份并返回日历和与选定月份无关的连续签到
    public CheckInMonthVO checkIns(String value) {
        long user = UserContext.require().userId();
        var today = today();
        YearMonth month;
        try {
            if (value != null && !value.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("Invalid month", value, 0);
            month = value == null ? YearMonth.from(today) : YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        if (month.isAfter(YearMonth.from(today))) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var dates = mapper.selectCheckInDates(user, month.atDay(1), month.atEndOfMonth());
        return new CheckInMonthVO(month.toString(), today, List.copyOf(dates), dates.size(), streak(user, today), properties.checkInExp());
    }

    // 固定业务时区计算连续经验日期并补全零值
    public ExperienceStatsVO stats(int days) {
        long user = UserContext.require().userId();
        if (days != 7 && days != 30) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var today = today();
        var start = today.minusDays(days - 1L);
        var end = today.plusDays(1);
        var daily = new HashMap<LocalDate, Long>();
        pageMapper.selectDailyExperience(user, start, end).forEach(row -> daily.put(row.date(), row.amount()));
        var bySource = new EnumMap<ExpSource, Long>(ExpSource.class);
        pageMapper.selectSourceExperience(user, start, end).forEach(row -> bySource.put(row.source(), row.earned()));
        var rows = new ArrayList<ExperienceStatsVO.Day>(days);
        for (int index = 0; index < days; index++) {
            var date = start.plusDays(index);
            rows.add(new ExperienceStatsVO.Day(date, daily.getOrDefault(date, 0L)));
        }
        var sources = new ArrayList<ExperienceStatsVO.Source>();
        for (var source : ExpSource.values()) sources.add(new ExperienceStatsVO.Source(source, bySource.getOrDefault(source, 0L)));
        return new ExperienceStatsVO(List.copyOf(rows), List.copyOf(sources));
    }

    // 来源筛选和稳定分页，不对外返回帖子或评论标识
    public PageResult<ExperienceRecordVO> records(ExpSource source, long page, long size) {
        long user = UserContext.require().userId();
        if (page < 1 || size < 1 || size > 50 || page - 1 > Long.MAX_VALUE / size) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        long count = pageMapper.countExperienceRecords(user, source);
        long offset = (page - 1) * size;
        if (offset >= count) return new PageResult<>(List.of(), count, page, size);
        var records = pageMapper.selectExperienceRecords(user, source, offset, size).stream()
                .map(row -> new ExperienceRecordVO(row.source(), row.amount(), row.createTime()
                        .atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of(properties.timezone())).toOffsetDateTime())).toList();
        return new PageResult<>(records, count, page, size);
    }

    // 今天未签时从昨天开始，遇到缺口即停止后续批次查询
    private long streak(long user, LocalDate today) {
        var expected = today;
        var before = today.plusDays(1);
        long count = 0;
        while (true) {
            var dates = pageMapper.selectEarlierCheckIns(user, before, STREAK_BATCH);
            if (dates.isEmpty()) return count;
            if (count == 0 && dates.get(0).equals(today.minusDays(1))) expected = today.minusDays(1);
            for (var date : dates) {
                if (!date.equals(expected)) return count;
                count++;
                expected = expected.minusDays(1);
            }
            if (dates.size() < STREAK_BATCH) return count;
            before = dates.get(dates.size() - 1);
        }
    }

    // 所有成长页查询共用配置时区的日期
    private LocalDate today() {
        return clock.instant().atZone(ZoneId.of(properties.timezone())).toLocalDate();
    }
}
