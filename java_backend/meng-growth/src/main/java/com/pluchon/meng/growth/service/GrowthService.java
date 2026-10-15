package com.pluchon.meng.growth.service;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.growth.config.GrowthProperties;
import com.pluchon.meng.growth.controller.vo.GrowthVO;
import com.pluchon.meng.growth.entity.GrowthAccountEntity;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.mapper.GrowthMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;

// 成长账户、签到、经验奖励与升级
@Service
public class GrowthService {

    // 成长数据存取
    @Autowired
    private GrowthMapper mapper;

    // 当前业务域配置
    @Autowired
    private GrowthProperties properties;

    // 发布领域事件
    @Autowired
    private ApplicationEventPublisher events;

    // 业务时钟
    @Autowired
    private Clock clock;

    // 按需创建账户并返回成长概况
    @Transactional(rollbackFor = Exception.class)
    public GrowthVO me() {
        long user = UserContext.require().userId();
        return view(account(user));
    }

    // 签到去重并在同一事务内发放经验
    @Transactional(rollbackFor = Exception.class)
    public GrowthVO checkIn() {
        long user = UserContext.require().userId();
        var a = account(user);
        var date = today();
        if (mapper.insertCheckInIfAbsent(user, date) != 1)
            throw new BusinessException(CommonErrorCode.CONFLICT);
        award(
                a,
                ExpSource.CHECK_IN,
                date.toString(),
                date,
                properties.checkInExp(),
                properties.checkInPoints(),
                1);
        return view(a);
    }

    // 校验业务事件后按每日上限发放经验
    @Transactional(rollbackFor = Exception.class)
    public void earned(long user, ExpSource source, String sourceId, Instant occurredAt) {
        if (user <= 0
                || sourceId == null
                || sourceId.isBlank()
                || source == ExpSource.CHECK_IN
                || occurredAt == null) throw new IllegalArgumentException("Invalid reward event");
        var a = account(user);
        var date = occurredAt.atZone(ZoneId.of(properties.timezone())).toLocalDate();
        award(
                a,
                source,
                sourceId,
                date,
                source == ExpSource.POST ? properties.postExp() : properties.commentExp(),
                source == ExpSource.POST ? properties.postPoints() : properties.commentPoints(),
                source == ExpSource.POST
                        ? properties.postDailyMax()
                        : properties.commentDailyMax());
    }

    // 按需创建并锁定成长账户
    private GrowthAccountEntity account(long user) {
        mapper.insertAccountIfAbsent(user);
        var a = mapper.selectAccountForUpdate(user);
        if (a == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return a;
    }

    // 按事件去重并累计经验、升级和积分；积分和经验同一件事、同一个每日上限、同一个事务
    private void award(
            GrowthAccountEntity a,
            ExpSource source,
            String id,
            LocalDate date,
            int amount,
            int points,
            int max) {
        String name = source.name();
        if (mapper.countSourceRecords(a.getUserId(), name, id) > 0
                || mapper.countDailyRecords(a.getUserId(), name, date) >= max) return;
        mapper.insertExperienceRecord(a.getUserId(), name, id, amount, date);
        mapper.insertPointRecord(a.getUserId(), name, id, points, date);
        mapper.addPointBalance(a.getUserId(), points);
        long exp = Math.addExact(a.getExp(), amount);
        int level = a.getLevel();
        for (int i = 0; i < properties.thresholds().size(); i++)
            if (exp >= properties.thresholds().get(i)) level = Math.max(level, i + 1);
        mapper.updateAccountExperience(a.getUserId(), exp, level);
        if (level > a.getLevel()) events.publishEvent(new LevelChangedEvent(a.getUserId(), level));
        a.setExp(exp);
        a.setLevel(level);
    }

    // 计算业务时区的当前日期
    private LocalDate today() {
        return clock.instant().atZone(ZoneId.of(properties.timezone())).toLocalDate();
    }

    // 组装等级门槛和本周签到状态
    private GrowthVO view(GrowthAccountEntity a) {
        var today = today();
        var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var dates =
                new HashSet<>(mapper.selectCheckInDates(a.getUserId(), monday, monday.plusDays(6)));
        var week = new ArrayList<GrowthVO.Day>();
        for (int i = 0; i < 7; i++)
            week.add(new GrowthVO.Day(monday.plusDays(i), dates.contains(monday.plusDays(i))));
        int index = Math.min(a.getLevel() - 1, properties.thresholds().size() - 1);
        return new GrowthVO(
                a.getLevel(),
                a.getExp(),
                properties.thresholds().get(index),
                index + 1 < properties.thresholds().size()
                        ? properties.thresholds().get(index + 1)
                        : null,
                dates.contains(today),
                today,
                week);
    }
}
