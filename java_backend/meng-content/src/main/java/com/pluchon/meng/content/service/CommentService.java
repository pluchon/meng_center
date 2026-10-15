package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.common.web.region.IpRegionService;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.controller.dto.CreateCommentDTO;
import com.pluchon.meng.content.controller.vo.CommentLocationVO;
import com.pluchon.meng.content.controller.vo.CommentVO;
import com.pluchon.meng.content.controller.vo.MentionCandidateVO;
import com.pluchon.meng.content.converter.CommentConverter;
import com.pluchon.meng.content.entity.CommentEntity;
import com.pluchon.meng.content.entity.CommentImageEntity;
import com.pluchon.meng.content.entity.CommentMentionEntity;
import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.CommentImageMapper;
import com.pluchon.meng.content.mapper.CommentMentionMapper;
import com.pluchon.meng.content.mapper.CommentMapper;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.validation.CommentRules;
import com.pluchon.meng.content.validation.ContentErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 评论创建、回复、点赞与删除
@Service
public class CommentService {

    // 从通知跳转到评论时按当前可见记录计算分页位置
    public CommentLocationVO location(long id, long size) {
        if (size < 1 || size > 50) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        var comment = require(id);
        long root =
            comment.getRootId() == 0 ? comment.getId() : comment.getRootId();
        var rootComment =
            comment.getRootId() == 0 ? comment : comments.selectById(root);
        // 置顶的一级评论永远在第一页
        long before =
            rootComment.getPinnedTime() != null
                ? 0
                : comments.countBeforeRoot(comment.getPostId(), root);
        return new CommentLocationVO(
            comment.getPostId(),
            root,
            before / size + 1,
            comment.getRootId() == 0
                ? 0
                : comments.countBeforeReply(root, id) / size + 1
        );
    }

    // 作者的每日互动统计
    @Autowired
    private AuthorStatisticsService statistics;

    // 评论数据存取
    @Autowired
    private CommentMapper comments;

    // 帖子配图数据存取
    @Autowired
    private CommentImageMapper images;

    // 帖子数据存取
    @Autowired
    private PostMapper posts;

    // 作者公开资料批量查询
    @Autowired
    private AuthorService authors;

    // 展示数据和实体转换
    @Autowired
    private CommentConverter converter;

    // 评论里 @ 到的用户数据存取
    @Autowired
    private CommentMentionMapper mentions;

    // 内容操作频率和配额校验
    @Autowired
    private ContentLimitService limits;

    // 请求地址属地解析
    @Autowired
    private IpRegionService regions;

    // 当前业务域配置
    @Autowired
    private ContentProperties properties;

    // 发布领域事件
    @Autowired
    private ApplicationEventPublisher events;

    // 查询存在的评论并校验状态
    public CommentEntity require(long id) {
        var c = comments.selectById(id);
        if (
            c == null ||
            posts.selectById(c.getPostId()) == null ||
            (c.getRootId() != 0 && comments.selectById(c.getRootId()) == null)
        ) throw new BusinessException(ContentErrorCode.COMMENT_DELETED);
        return c;
    }

    // 分页查询一级评论
    public PageResult<CommentVO> list(long post, long page, long size) {
        var p = posts.selectById(post);
        if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var result = comments.selectPage(
            new Page<>(page, size),
            Wrappers.<CommentEntity>lambdaQuery()
                .eq(CommentEntity::getPostId, post)
                .eq(CommentEntity::getRootId, 0)
                // 置顶的排最前，没有置顶时间的（空值）排在后面
                .orderByDesc(CommentEntity::getPinnedTime)
                .orderByDesc(CommentEntity::getId)
        );
        return new PageResult<>(
            convert(result.getRecords(), p.getAuthorId()),
            result.getTotal(),
            page,
            size
        );
    }

    // 分页查询指定评论的回复
    public PageResult<CommentVO> replies(long id, long page, long size) {
        var root = require(id);
        if (root.getRootId() != 0) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        var result = comments.selectPage(
            new Page<>(page, size),
            Wrappers.<CommentEntity>lambdaQuery()
                .eq(CommentEntity::getRootId, id)
                .orderByAsc(CommentEntity::getId)
        );
        return new PageResult<>(
            convert(
                result.getRecords(),
                posts.selectById(root.getPostId()).getAuthorId()
            ),
            result.getTotal(),
            page,
            size
        );
    }

    // 批量组装作者、配图和点赞状态
    private List<CommentVO> convert(List<CommentEntity> list, long author) {
        if (list.isEmpty()) return List.of();
        var ids = list.stream().map(CommentEntity::getId).toList();
        var liked = new HashSet<>(
            comments.selectLikedCommentIds(UserContext.require().userId(), ids)
        );
        var userIds = new HashSet<Long>();
        for (var c : list) {
            userIds.add(c.getAuthorId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        }
        var users = authors.batch(new ArrayList<>(userIds));
        var grouped = new HashMap<Long, List<CommentImageEntity>>();
        for (var i : images.selectList(
            Wrappers.<CommentImageEntity>lambdaQuery()
                .in(CommentImageEntity::getCommentId, ids)
                .orderByAsc(CommentImageEntity::getSort)
        ))
            grouped
                .computeIfAbsent(i.getCommentId(), key -> new ArrayList<>())
                .add(i);
        var mentioned = new HashMap<Long, List<CommentMentionEntity>>();
        for (var m : mentions.selectList(
            Wrappers.<CommentMentionEntity>lambdaQuery()
                .in(CommentMentionEntity::getCommentId, ids)
                .orderByAsc(CommentMentionEntity::getId)
        ))
            mentioned
                .computeIfAbsent(m.getCommentId(), key -> new ArrayList<>())
                .add(m);
        return list
            .stream()
            .map(c ->
                converter.comment(
                    c,
                    grouped.getOrDefault(c.getId(), List.of()),
                    mentioned.getOrDefault(c.getId(), List.of()),
                    users,
                    liked.contains(c.getId()),
                    author
                )
            )
            .toList();
    }

    // 校验回复目标并创建评论
    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(long post, CreateCommentDTO dto, String ip) {
        long user = UserContext.require().userId();
        CommentRules.validate(dto.content(), dto.imageIds());
        var p = posts.selectByIdForUpdate(post);
        if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        CommentEntity target =
            dto.replyToCommentId() == null
                ? null
                : require(dto.replyToCommentId());
        if (
            target != null && target.getPostId() != post
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        limits.comment(user);
        var mentioned = resolveMentions(
            post,
            p.getAuthorId(),
            user,
            dto.content(),
            dto.mentionUserIds()
        );
        var c = converter.create(dto);
        c.setPostId(post);
        c.setAuthorId(user);
        c.setRootId(
            target == null
                ? 0L
                : target.getRootId() == 0
                  ? target.getId()
                  : target.getRootId()
        );
        c.setReplyToCommentId(target == null ? null : target.getId());
        c.setReplyToUserId(target == null ? null : target.getAuthorId());
        c.setImageCount(dto.imageIds().size());
        c.setIpRegion(regions.resolve(ip));
        comments.insert(c);
        for (int i = 0; i < dto.imageIds().size(); i++) if (
            images.attachToComment(dto.imageIds().get(i), c.getId(), user, i) !=
            1
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        for (var m : mentioned) {
            m.setCommentId(c.getId());
            mentions.insert(m);
        }
        if (c.getRootId() != 0) comments.incrementReplyCount(c.getRootId(), 1);
        posts.incrementCommentCount(post, 1, properties.commentWeight());
        statistics.record(p.getAuthorId(), CreatorMetric.COMMENT);
        events.publishEvent(
            new PostReadModelEvent(
                post,
                null,
                PostReadModelEvent.Kind.SCORE,
                properties.commentWeight()
            )
        );
        events.publishEvent(
            new CommentCreatedEvent(
                c.getId(),
                post,
                p.getTitle(),
                user,
                target == null ? p.getAuthorId() : target.getAuthorId(),
                target == null ? null : target.getId(),
                excerpt(c.getContent()),
                target == null ? null : excerpt(target.getContent()),
                mentioned.stream().map(CommentMentionEntity::getUserId).toList()
            )
        );
        return convert(
            List.of(comments.selectById(c.getId())),
            p.getAuthorId()
        ).get(0);
    }

    // 只保留本帖作者和评论过本帖的人，而且正文里确实写了 @昵称；昵称以服务端查到的为准
    private List<CommentMentionEntity> resolveMentions(
        long post,
        long postAuthor,
        long user,
        String content,
        List<Long> requested
    ) {
        var wanted = new LinkedHashSet<>(requested);
        wanted.remove(user);
        if (wanted.isEmpty()) return List.of();
        var allowed = new HashSet<>(
            comments.selectCommenterAmong(post, new ArrayList<>(wanted))
        );
        if (wanted.contains(postAuthor)) allowed.add(postAuthor);
        var users = authors.batch(new ArrayList<>(allowed));
        var result = new ArrayList<CommentMentionEntity>();
        for (var id : wanted) {
            var target = users.get(id);
            if (
                !allowed.contains(id) ||
                target == null ||
                target.nickname() == null ||
                target.nickname().isBlank() ||
                !content.contains("@" + target.nickname())
            ) continue;
            var row = new CommentMentionEntity();
            row.setUserId(id);
            row.setNickname(target.nickname());
            result.add(row);
        }
        return result;
    }

    // 输入 @ 时的候选：本帖作者和最近评论过的人，按昵称过滤由前端做
    public List<MentionCandidateVO> mentionCandidates(long post) {
        var p = posts.selectById(post);
        if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        long user = UserContext.require().userId();
        var ids = new LinkedHashSet<Long>();
        ids.add(p.getAuthorId());
        ids.addAll(comments.selectRecentCommenters(post, 50));
        ids.remove(user);
        var users = authors.batch(new ArrayList<>(ids));
        return ids
            .stream()
            .map(users::get)
            .filter(u ->
                u != null &&
                u.nickname() != null &&
                !u.nickname().isBlank()
            )
            .limit(30)
            .map(u ->
                new MentionCandidateVO(
                    u.id(),
                    u.nickname(),
                    u.avatar(),
                    u.level(),
                    u.id() == p.getAuthorId()
                )
            )
            .toList();
    }

    // 帖子作者置顶或取消置顶一级评论；一篇帖子同时只置顶一条，幂等
    @Transactional(rollbackFor = Exception.class)
    public void pin(long id, boolean on) {
        var initial = require(id);
        var post = posts.selectByIdForUpdate(initial.getPostId());
        var c = require(id);
        if (
            post == null ||
            post.getAuthorId() != UserContext.require().userId()
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        if (c.getRootId() != 0) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        if (on && c.getPinnedTime() == null) {
            comments.clearPinsExcept(post.getId(), id);
            comments.updatePinnedTime(id, LocalDateTime.now());
        } else if (!on && c.getPinnedTime() != null) {
            comments.updatePinnedTime(id, null);
        }
    }

    // 校验权限并删除评论及相关回复
    @Transactional(rollbackFor = Exception.class)
    public long delete(long id) {
        var initial = require(id);
        posts.selectByIdForUpdate(initial.getPostId());
        var c = require(id);
        if (
            c.getAuthorId() != UserContext.require().userId()
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        remove(c);
        return posts.selectById(c.getPostId()).getCommentCount();
    }

    // 删除评论配图并同步计数
    private void remove(CommentEntity c) {
        comments.deleteById(c.getId());
        images.delete(
            Wrappers.<CommentImageEntity>lambdaQuery().eq(
                CommentImageEntity::getCommentId,
                c.getId()
            )
        );
        long removed = c.getRootId() == 0 ? 1 + c.getReplyCount() : 1;
        if (c.getRootId() != 0) comments.incrementReplyCount(c.getRootId(), -1);
        posts.incrementCommentCount(
            c.getPostId(),
            -removed,
            -removed * properties.commentWeight()
        );
        events.publishEvent(
            new PostReadModelEvent(
                c.getPostId(),
                null,
                PostReadModelEvent.Kind.SCORE,
                -removed * properties.commentWeight()
            )
        );
    }

    // 幂等更新评论点赞关系与计数
    @Transactional(rollbackFor = Exception.class)
    public void like(long id, boolean on) {
        var initial = require(id);
        posts.selectByIdForUpdate(initial.getPostId());
        require(id);
        long user = UserContext.require().userId();
        int changed = on
            ? comments.insertLike(id, user)
            : comments.deleteLike(id, user);
        if (changed == 1) {
            comments.incrementLikeCount(id, on ? 1 : -1);
            if (on) {
                var post = posts.selectById(initial.getPostId());
                events.publishEvent(
                    new ContentInteractionEvent(
                        "comment.liked",
                        post.getId(),
                        post.getTitle(),
                        id,
                        excerpt(initial.getContent()),
                        user,
                        initial.getAuthorId()
                    )
                );
            }
        }
    }

    // 按 Unicode 字符截取通知摘要
    private static String excerpt(String content) {
        return content.substring(
            0,
            content.offsetByCodePoints(
                0,
                Math.min(60, content.codePointCount(0, content.length()))
            )
        );
    }
}
