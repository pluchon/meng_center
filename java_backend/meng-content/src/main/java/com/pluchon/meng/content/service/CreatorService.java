package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.vo.CreatorOverviewVO;
import com.pluchon.meng.content.controller.vo.CreatorPostVO;
import com.pluchon.meng.content.controller.vo.CreatorTopPostVO;
import com.pluchon.meng.content.controller.vo.CreatorTrendVO;
import com.pluchon.meng.content.converter.CreatorConverter;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.AuthorDailyStatEntity;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.entity.enums.PostImageKind;
import com.pluchon.meng.content.mapper.AuthorDailyStatMapper;
import com.pluchon.meng.content.mapper.PostImageMapper;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.mapper.PostRelationMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 当前作者自己的内容、累计计数和每日趋势
@Service
public class CreatorService {

    // 当前帖子的读模型
    @Autowired
    private PostMapper posts;

    // 配图批量读取
    @Autowired
    private PostImageMapper images;

    // 收藏关系统计
    @Autowired
    private PostRelationMapper relations;

    // 每日历史统计
    @Autowired
    private AuthorDailyStatMapper stats;

    // 配图地址转换
    @Autowired
    private PostConverter postConverter;

    // 创作中心条目转换
    @Autowired
    private CreatorConverter converter;

    // 日期来源
    @Autowired
    private Clock clock;

    // 累计值只统计当前用户尚未删除的帖子
    public CreatorOverviewVO overview() {
        long author = UserContext.require().userId();
        var totals = posts.selectCreatorTotals(author);
        return new CreatorOverviewVO(
            totals.postCount(),
            totals.viewCount(),
            totals.likeCount(),
            totals.commentCount(),
            relations.selectCreatorFavoriteCount(author)
        );
    }

    // 分页查自己的帖子并批量读取封面和收藏数
    public PageResult<CreatorPostVO> mine(long page, long size) {
        var result = posts.selectPage(
            new Page<>(page, size),
            Wrappers.<PostEntity>lambdaQuery()
                .select(
                    PostEntity::getId,
                    PostEntity::getCategory,
                    PostEntity::getTitle,
                    PostEntity::getSummary,
                    PostEntity::getCoverImageId,
                    PostEntity::getCreateTime,
                    PostEntity::getViewCount,
                    PostEntity::getLikeCount,
                    PostEntity::getCommentCount
                )
                .eq(PostEntity::getAuthorId, UserContext.require().userId())
                .orderByDesc(PostEntity::getCreateTime)
                .orderByDesc(PostEntity::getId)
        );
        var ids = result.getRecords().stream().map(PostEntity::getId).toList();
        if (ids.isEmpty()) return new PageResult<>(
            List.of(),
            result.getTotal(),
            page,
            size
        );
        var byId = new HashMap<Long, PostImageEntity>();
        var first = new HashMap<Long, PostImageEntity>();
        images
            .selectList(
                Wrappers.<PostImageEntity>lambdaQuery()
                    .in(PostImageEntity::getPostId, ids)
                    .orderByAsc(PostImageEntity::getSort)
                    .orderByAsc(PostImageEntity::getId)
            )
            .forEach(i -> {
                byId.put(i.getId(), i);
                if (
                    PostImageKind.ATTACHMENT.name().equals(i.getKind())
                ) first.putIfAbsent(i.getPostId(), i);
            });
        var favorites = new HashMap<Long, Long>();
        relations
            .selectFavoriteCounts(ids)
            .forEach(c -> favorites.put(c.postId(), c.count()));
        var records = result
            .getRecords()
            .stream()
            .map(p -> {
                var cover =
                    p.getCoverImageId() == null
                        ? first.get(p.getId())
                        : byId.get(p.getCoverImageId());
                return converter.post(
                    p,
                    cover == null ? null : postConverter.image(cover),
                    favorites.getOrDefault(p.getId(), 0L)
                );
            })
            .toList();
        return new PageResult<>(records, result.getTotal(), page, size);
    }

    // 帖子表现不限定近七天，最多返回五条
    public List<CreatorTopPostVO> topPosts() {
        return posts
            .selectPage(
                new Page<PostEntity>(1, 5, false),
                Wrappers.<PostEntity>lambdaQuery()
                    .select(PostEntity::getId, PostEntity::getTitle, PostEntity::getHotScore)
                    .eq(PostEntity::getAuthorId, UserContext.require().userId())
                    .orderByDesc(PostEntity::getHotScore)
                    .orderByDesc(PostEntity::getId)
            )
            .getRecords()
            .stream()
            .map(p ->
                new CreatorTopPostVO(p.getId(), p.getTitle(), p.getHotScore())
            )
            .toList();
    }

    // 连续七天或三十天，每日新增值由服务端补零
    public List<CreatorTrendVO> trend(int days) {
        if (days != 7 && days != 30) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        long author = UserContext.require().userId();
        var today = LocalDate.now(clock.withZone(AuthorStatisticsService.ZONE));
        var start = today.minusDays(days - 1L);
        var daily = new HashMap<LocalDate, AuthorDailyStatEntity>();
        stats
            .selectList(
                Wrappers.<AuthorDailyStatEntity>lambdaQuery()
                    .eq(AuthorDailyStatEntity::getAuthorId, author)
                    .ge(AuthorDailyStatEntity::getStatDate, start)
                    .le(AuthorDailyStatEntity::getStatDate, today)
            )
            .forEach(s -> daily.put(s.getStatDate(), s));
        var published = new HashMap<LocalDate, Long>();
        var from = LocalDateTime.ofInstant(
            start.atStartOfDay(AuthorStatisticsService.ZONE).toInstant(),
            ZoneOffset.UTC
        );
        var to = LocalDateTime.ofInstant(
            today
                .plusDays(1)
                .atStartOfDay(AuthorStatisticsService.ZONE)
                .toInstant(),
            ZoneOffset.UTC
        );
        int offset = AuthorStatisticsService.ZONE.getRules()
            .getOffset(clock.instant())
            .getTotalSeconds();
        posts
            .selectPublicationCounts(author, from, to, offset)
            .forEach(c -> published.put(c.date(), c.count()));
        var result = new ArrayList<CreatorTrendVO>(days);
        for (int i = 0; i < days; i++) {
            var date = start.plusDays(i);
            var stat = daily.getOrDefault(date, new AuthorDailyStatEntity());
            result.add(
                new CreatorTrendVO(
                    date,
                    stat.getViewCount(),
                    stat.getLikeCount(),
                    stat.getCommentCount(),
                    stat.getFavoriteCount(),
                    published.getOrDefault(date, 0L)
                )
            );
        }
        return result;
    }
}
