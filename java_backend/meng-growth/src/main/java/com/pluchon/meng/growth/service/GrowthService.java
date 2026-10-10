package com.pluchon.meng.growth.service;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.growth.config.GrowthProperties;
import com.pluchon.meng.growth.controller.vo.GrowthVO;
import com.pluchon.meng.growth.entity.*;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.mapper.GrowthMapper;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GrowthService {

    private final GrowthMapper mapper;
    private final GrowthProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public GrowthVO me() {
        long user = UserContext.require().userId();
        return view(account(user));
    }

    @Transactional
    public GrowthVO checkIn() {
        long user = UserContext.require().userId();
        var a = account(user);
        var date = today();
        if (mapper.checkIn(user, date) != 1) throw new BusinessException(
            CommonErrorCode.CONFLICT
        );
        award(
            a,
            ExpSource.CHECK_IN,
            date.toString(),
            date,
            properties.checkInExp(),
            1
        );
        return view(a);
    }

    @Transactional
    public void earned(
        long user,
        ExpSource source,
        String sourceId,
        Instant occurredAt
    ) {
        if (
            user <= 0 ||
            sourceId == null ||
            sourceId.isBlank() ||
            source == ExpSource.CHECK_IN ||
            occurredAt == null
        ) throw new IllegalArgumentException("Invalid reward event");
        var a = account(user);
        var date = occurredAt
            .atZone(ZoneId.of(properties.timezone()))
            .toLocalDate();
        award(
            a,
            source,
            sourceId,
            date,
            source == ExpSource.POST
                ? properties.postExp()
                : properties.commentExp(),
            source == ExpSource.POST
                ? properties.postDailyMax()
                : properties.commentDailyMax()
        );
    }

    private GrowthAccountEntity account(long user) {
        mapper.ensure(user);
        var a = mapper.lock(user);
        if (a == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return a;
    }

    private void award(
        GrowthAccountEntity a,
        ExpSource source,
        String id,
        LocalDate date,
        int amount,
        int max
    ) {
        String name = source.name();
        if (
            mapper.exists(a.getUserId(), name, id) > 0 ||
            mapper.count(a.getUserId(), name, date) >= max
        ) return;
        mapper.record(a.getUserId(), name, id, amount, date);
        long exp = Math.addExact(a.getExp(), amount);
        int level = a.getLevel();
        for (int i = 0; i < properties.thresholds().size(); i++) if (
            exp >= properties.thresholds().get(i)
        ) level = Math.max(level, i + 1);
        mapper.update(a.getUserId(), exp, level);
        if (level > a.getLevel()) events.publishEvent(
            new LevelChangedEvent(a.getUserId(), level)
        );
        a.setExp(exp);
        a.setLevel(level);
    }

    private LocalDate today() {
        return clock
            .instant()
            .atZone(ZoneId.of(properties.timezone()))
            .toLocalDate();
    }

    private GrowthVO view(GrowthAccountEntity a) {
        var today = today();
        var monday = today.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        var dates = new HashSet<>(
            mapper.week(a.getUserId(), monday, monday.plusDays(6))
        );
        var week = new ArrayList<GrowthVO.Day>();
        for (int i = 0; i < 7; i++) week.add(
            new GrowthVO.Day(
                monday.plusDays(i),
                dates.contains(monday.plusDays(i))
            )
        );
        int index = Math.min(
            a.getLevel() - 1,
            properties.thresholds().size() - 1
        );
        return new GrowthVO(
            a.getLevel(),
            a.getExp(),
            properties.thresholds().get(index),
            index + 1 < properties.thresholds().size()
                ? properties.thresholds().get(index + 1)
                : null,
            dates.contains(today),
            today,
            week
        );
    }
}
