package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.controller.dto.CreateCommentDTO;
import com.pluchon.meng.content.controller.vo.CommentVO;
import com.pluchon.meng.content.converter.CommentConverter;
import com.pluchon.meng.content.entity.*;
import com.pluchon.meng.content.mapper.*;
import com.pluchon.meng.content.validation.*;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentMapper comments;
    private final CommentImageMapper images;
    private final PostMapper posts;
    private final AuthorService authors;
    private final CommentConverter converter;
    private final ContentLimitService limits;
    private final IpRegionService regions;
    private final ContentProperties properties;
    private final ApplicationEventPublisher events;

    public CommentEntity require(long id) {
        var c = comments.selectById(id);
        if (c == null
                || posts.selectById(c.getPostId()) == null
                || (c.getRootId() != 0 && comments.selectById(c.getRootId()) == null))
            throw new BusinessException(ContentErrorCode.COMMENT_DELETED);
        return c;
    }

    public PageResult<CommentVO> list(long post, long page, long size) {
        var p = posts.selectById(post);
        if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var result =
                comments.selectPage(
                        new Page<>(page, size),
                        Wrappers.<CommentEntity>lambdaQuery()
                                .eq(CommentEntity::getPostId, post)
                                .eq(CommentEntity::getRootId, 0)
                                .orderByDesc(CommentEntity::getId));
        return new PageResult<>(
                convert(result.getRecords(), p.getAuthorId()), result.getTotal(), page, size);
    }

    public PageResult<CommentVO> replies(long id, long page, long size) {
        var root = require(id);
        if (root.getRootId() != 0) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var result =
                comments.selectPage(
                        new Page<>(page, size),
                        Wrappers.<CommentEntity>lambdaQuery()
                                .eq(CommentEntity::getRootId, id)
                                .orderByAsc(CommentEntity::getId));
        return new PageResult<>(
                convert(result.getRecords(), posts.selectById(root.getPostId()).getAuthorId()),
                result.getTotal(),
                page,
                size);
    }

    private List<CommentVO> convert(List<CommentEntity> list, long author) {
        if (list.isEmpty()) return List.of();
        var ids = list.stream().map(CommentEntity::getId).toList();
        var liked = new HashSet<>(comments.liked(UserContext.require().userId(), ids));
        var userIds = new HashSet<Long>();
        for (var c : list) {
            userIds.add(c.getAuthorId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        }
        var users = authors.batch(new ArrayList<>(userIds));
        var grouped = new HashMap<Long, List<CommentImageEntity>>();
        for (var i :
                images.selectList(
                        Wrappers.<CommentImageEntity>lambdaQuery()
                                .in(CommentImageEntity::getCommentId, ids)
                                .orderByAsc(CommentImageEntity::getSort)))
            grouped.computeIfAbsent(i.getCommentId(), key -> new ArrayList<>()).add(i);
        return list.stream()
                .map(
                        c ->
                                converter.comment(
                                        c,
                                        grouped.getOrDefault(c.getId(), List.of()),
                                        users,
                                        liked.contains(c.getId()),
                                        author))
                .toList();
    }

    @Transactional
    public CommentVO create(long post, CreateCommentDTO dto, String ip) {
        long user = UserContext.require().userId();
        CommentRules.validate(dto.content(), dto.imageIds());
        var p = posts.lock(post);
        if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        CommentEntity target =
                dto.replyToCommentId() == null ? null : require(dto.replyToCommentId());
        if (target != null && target.getPostId() != post)
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        limits.comment(user);
        var c = new CommentEntity();
        c.setPostId(post);
        c.setAuthorId(user);
        c.setRootId(
                target == null
                        ? 0L
                        : target.getRootId() == 0 ? target.getId() : target.getRootId());
        c.setReplyToCommentId(target == null ? null : target.getId());
        c.setReplyToUserId(target == null ? null : target.getAuthorId());
        c.setContent(dto.content());
        c.setImageCount(dto.imageIds().size());
        c.setLikeCount(0L);
        c.setReplyCount(0L);
        c.setIpRegion(regions.resolve(ip));
        comments.insert(c);
        for (int i = 0; i < dto.imageIds().size(); i++)
            if (images.attach(dto.imageIds().get(i), c.getId(), user, i) != 1)
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
        if (c.getRootId() != 0) comments.replies(c.getRootId(), 1);
        posts.changeComments(post, 1, properties.commentWeight());
        events.publishEvent(
                new CommentCreatedEvent(
                        c.getId(),
                        post,
                        p.getTitle(),
                        user,
                        target == null ? p.getAuthorId() : target.getAuthorId()));
        return convert(List.of(comments.selectById(c.getId())), p.getAuthorId()).get(0);
    }

    @Transactional
    public long delete(long id) {
        var initial = require(id);
        posts.lock(initial.getPostId());
        var c = require(id);
        if (c.getAuthorId() != UserContext.require().userId())
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        remove(c);
        return posts.selectById(c.getPostId()).getCommentCount();
    }

    private void remove(CommentEntity c) {
        comments.deleteById(c.getId());
        images.delete(
                Wrappers.<CommentImageEntity>lambdaQuery()
                        .eq(CommentImageEntity::getCommentId, c.getId()));
        long removed = c.getRootId() == 0 ? 1 + c.getReplyCount() : 1;
        if (c.getRootId() != 0) comments.replies(c.getRootId(), -1);
        posts.changeComments(c.getPostId(), -removed, -removed * properties.commentWeight());
    }

    @Transactional
    public void like(long id, boolean on) {
        var initial = require(id);
        posts.lock(initial.getPostId());
        require(id);
        long user = UserContext.require().userId();
        int changed = on ? comments.like(id, user) : comments.unlike(id, user);
        if (changed == 1) comments.likes(id, on ? 1 : -1);
    }
}
