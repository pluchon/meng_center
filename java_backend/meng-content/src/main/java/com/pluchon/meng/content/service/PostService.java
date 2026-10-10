package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.controller.dto.CreatePostDTO;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.*;
import com.pluchon.meng.content.entity.enums.*;
import com.pluchon.meng.content.mapper.*;
import com.pluchon.meng.content.validation.PostRules;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper posts;
    private final PostImageMapper images;
    private final PostRelationMapper relations;
    private final PostConverter converter;
    private final AuthorService authors;
    private final PostSummary summary;
    private final ContentLimitService limits;
    private final IpRegionService regions;
    private final ContentProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TagService tags;
    private final DraftService drafts;

    @Transactional
    public long create(CreatePostDTO dto, String ip) {
        long user = UserContext.require().userId();
        PostRules.images(
            dto.imageIds(),
            dto.coverImageId()
        );
        var tagNames = PostRules.tags(
            dto.tags()
        );
        drafts.lockForPublication(dto.draftId());
        limits.post(user);
        var p = new PostEntity();
        p.setAuthorId(user);
        p.setCategory(dto.category());
        p.setTitle(dto.title().trim());
        p.setContent(dto.content());
        p.setSummary(summary.summarize(dto.content()));
        p.setImageCount(dto.imageIds().size());
        p.setCoverImageId(dto.coverImageId());
        p.setViewCount(0L);
        p.setLikeCount(0L);
        p.setCommentCount(0L);
        p.setHotScore(0L);
        p.setIpRegion(regions.resolve(ip));
        posts.insert(p);
        for (int i = 0; i < dto.imageIds().size(); i++) if (
            images.attach(
                dto.imageIds().get(i),
                p.getId(),
                user,
                i,
                PostImageKind.ATTACHMENT.name()
            ) != 1
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        if (
            dto.coverImageId() != null &&
            images.attach(dto.coverImageId(), p.getId(), user, 0, PostImageKind.COVER.name()) != 1
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        tags.attach(p.getId(), tagNames);
        drafts.published(dto.draftId());
        events.publishEvent(new PostPublishedEvent(p.getId(), user));
        return p.getId();
    }

    public PageResult<PostFeedVO> feed(
        PostCategory category,
        PostSort sort,
        long page,
        long size
    ) {
        long user = UserContext.require().userId();
        var query = Wrappers.<PostEntity>lambdaQuery().eq(
            category != null,
            PostEntity::getCategory,
            category
        );
        if (sort == PostSort.HOT) query
            .ge(
                PostEntity::getCreateTime,
                LocalDateTime.ofInstant(
                    clock.instant().minus(Duration.ofDays(7)),
                    ZoneOffset.UTC
                )
            )
            .orderByDesc(PostEntity::getHotScore);
        query
            .orderByDesc(PostEntity::getCreateTime)
            .orderByDesc(PostEntity::getId);
        var result = posts.selectPage(new Page<>(page, size), query);
        var list = result.getRecords();
        if (list.isEmpty()) return new PageResult<>(
            List.of(),
            result.getTotal(),
            page,
            size
        );
        var ids = list.stream().map(PostEntity::getId).toList();
        var liked = new HashSet<>(relations.liked(user, ids));
        var favorites = new HashSet<>(relations.favorited(user, ids));
        var favoriteCounts = new HashMap<Long, Long>();
        for (var count : relations.favoriteCounts(ids))
            favoriteCounts.put(count.postId(), count.count());
        var authorMap = authors.batch(
            list.stream().map(PostEntity::getAuthorId).distinct().toList()
        );
        var imageMap = new HashMap<Long, List<PostImageEntity>>();
        for (var image : images.selectList(
            Wrappers.<PostImageEntity>lambdaQuery()
                .in(PostImageEntity::getPostId, ids)
                .eq(PostImageEntity::getKind, PostImageKind.ATTACHMENT.name())
                .orderByAsc(PostImageEntity::getSort)
        ))
            imageMap
                .computeIfAbsent(image.getPostId(), x -> new ArrayList<>())
                .add(image);
        return new PageResult<>(
            list
                .stream()
                .map(p ->
                    converter.feed(
                        p,
                        imageMap.getOrDefault(p.getId(), List.of()),
                        authorMap.get(p.getAuthorId()),
                        liked.contains(p.getId()),
                        favorites.contains(p.getId()),
                        favoriteCounts.getOrDefault(p.getId(), 0L)
                    )
                )
                .toList(),
            result.getTotal(),
            page,
            size
        );
    }

    @Transactional
    public void like(long id, boolean enabled) {
        long user = UserContext.require().userId();
        requirePost(id);
        int changed = enabled
            ? relations.like(id, user)
            : relations.unlike(id, user);
        if (changed == 1) posts.changeLikes(
            id,
            enabled ? 1 : -1,
            (enabled ? 1L : -1L) * properties.likeWeight()
        );
    }

    @Transactional
    public void favorite(long id, boolean enabled) {
        long user = UserContext.require().userId();
        requirePost(id);
        if (enabled) relations.favorite(id, user);
        else relations.unfavorite(id, user);
    }

    private void requirePost(long id) {
        if (posts.lock(id) == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
    }
}
