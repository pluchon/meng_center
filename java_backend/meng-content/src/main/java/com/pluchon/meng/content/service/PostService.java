package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.common.web.region.IpRegionService;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.controller.dto.CreatePostDTO;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.PostDraftEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostImageKind;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.entity.enums.ReviewState;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.PostImageMapper;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.mapper.PostRelationMapper;
import com.pluchon.meng.content.validation.PostRules;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 帖子发布、展示、编辑与互动
@Service
public class PostService {

    @Autowired
    private AiEventService aiEvents;

    // 作者的每日互动统计
    @Autowired
    private AuthorStatisticsService statistics;

    // 帖子数据存取
    @Autowired
    private PostMapper posts;

    // 帖子配图数据存取
    @Autowired
    private PostImageMapper images;

    // 帖子点赞与收藏关系存取
    @Autowired
    private PostRelationMapper relations;

    // 展示数据和实体转换
    @Autowired
    private PostConverter converter;

    // 作者公开资料批量查询
    @Autowired
    private AuthorService authors;

    // 正文摘要
    @Autowired
    private PostSummary summary;

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

    // 业务时钟
    @Autowired
    private Clock clock;

    // 帖子标签业务操作
    @Autowired
    private TagService tags;

    // 帖子草稿业务操作
    @Autowired
    private DraftService drafts;

    // 热榜维护
    @Autowired
    private HotRankService hot;

    // 校验草稿、标签和配图后发布帖子
    @Transactional(rollbackFor = Exception.class)
    public long create(CreatePostDTO dto, String ip) {
        long user = UserContext.require().userId();
        PostRules.images(dto.imageIds(), dto.coverImageId());
        var tagNames = PostRules.tags(dto.tags());
        drafts.lockForPublication(dto.draftId());
        limits.post(user);
        var p = converter.create(dto);
        p.setAuthorId(user);
        p.setSummary(summary.summarize(dto.content()));
        p.setImageCount(dto.imageIds().size());
        p.setIpRegion(regions.resolve(ip));
        posts.insert(p);
        for (int i = 0; i < dto.imageIds().size(); i++) if (
            images.attachToPost(
                dto.imageIds().get(i),
                p.getId(),
                user,
                i,
                PostImageKind.ATTACHMENT.name()
            ) != 1
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        if (
            dto.coverImageId() != null &&
            images.attachToPost(
                dto.coverImageId(),
                p.getId(),
                user,
                0,
                PostImageKind.COVER.name()
            ) != 1
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        tags.attach(p.getId(), tagNames);
        drafts.published(dto.draftId());
        aiEvents.post(p.getId(), "post.published", true);
        events.publishEvent(new PostPublishedEvent(p.getId(), user));
        events.publishEvent(
            new PostReadModelEvent(p.getId(), p.getCategory(), PostReadModelEvent.Kind.PUBLISHED, 0)
        );
        return p.getId();
    }

    // 按分类和排序分页查询帖子流
    public PageResult<PostFeedVO> feed(PostCategory category, PostSort sort, long page, long size) {
        long user = UserContext.require().userId();
        if (sort == PostSort.HOT) {
            var rank = hot.read(category, page, size);
            if (rank != null) {
                var byId = new HashMap<Long, PostEntity>();
                if (!rank.ids().isEmpty()) posts
                    .selectByIds(rank.ids())
                    .forEach(p -> byId.put(p.getId(), p));
                return new PageResult<>(
                    hydrate(rank.ids().stream().map(byId::get).filter(Objects::nonNull).toList()),
                    rank.total(),
                    page,
                    size
                );
            }
        }
        var query = Wrappers.<PostEntity>lambdaQuery().eq(
            category != null,
            PostEntity::getCategory,
            category
        );
        query.eq(PostEntity::getReviewState, "NORMAL");
        if (sort == PostSort.HOT) query
            .ge(
                PostEntity::getCreateTime,
                LocalDateTime.ofInstant(clock.instant().minus(Duration.ofDays(7)), ZoneOffset.UTC)
            )
            .orderByDesc(PostEntity::getHotScore);
        if (sort == PostSort.LATEST) query.orderByDesc(PostEntity::getCreateTime);
        query.orderByDesc(PostEntity::getId);
        var result = posts.selectPage(new Page<>(page, size), query);
        return new PageResult<>(hydrate(result.getRecords()), result.getTotal(), page, size);
    }

    // 某个作者发布的帖子，新的在前，别人主页的「发布」页用
    public PageResult<PostFeedVO> byAuthor(long author, long page, long size) {
        var result = posts.selectPage(
            new Page<>(page, size),
            Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getAuthorId, author)
                .eq(author != UserContext.require().userId(), PostEntity::getReviewState, ReviewState.NORMAL)
                .orderByDesc(PostEntity::getCreateTime)
                .orderByDesc(PostEntity::getId)
        );
        return new PageResult<>(hydrate(result.getRecords()), result.getTotal(), page, size);
    }

    // 批量组装作者、配图和互动状态
    public List<PostFeedVO> hydrate(List<PostEntity> list) {
        long user = UserContext.require().userId();
        list = list
            .stream()
            .filter(p -> "NORMAL".equals(p.getReviewState().name()) || p.getAuthorId() == user)
            .toList();
        if (list.isEmpty()) return List.of();
        var ids = list.stream().map(PostEntity::getId).toList();
        var liked = new HashSet<>(relations.selectLikedPostIds(user, ids));
        var favorites = new HashSet<>(relations.selectFavoritedPostIds(user, ids));
        var favoriteCounts = new HashMap<Long, Long>();
        for (var count : relations.selectFavoriteCounts(ids))
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
            imageMap.computeIfAbsent(image.getPostId(), x -> new ArrayList<>()).add(image);
        return list
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
            .toList();
    }

    // 幂等更新点赞关系和帖子热度
    @Transactional(rollbackFor = Exception.class)
    public void like(long id, boolean enabled) {
        long user = UserContext.require().userId();
        var post = requirePost(id);
        if (!post.visibleTo(user)) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var created = enabled ? null : relations.selectLikeCreateTime(id, user);
        int changed = enabled ? relations.insertLike(id, user) : relations.deleteLike(id, user);
        if (changed == 1) {
            if (enabled) statistics.record(post.getAuthorId(), CreatorMetric.LIKE);
            else statistics.cancel(post.getAuthorId(), CreatorMetric.LIKE, created);
            long delta = (enabled ? 1L : -1L) * properties.likeWeight();
            posts.incrementLikeCount(id, enabled ? 1 : -1, delta);
            if (enabled) events.publishEvent(
                new ContentInteractionEvent(
                    "post.liked",
                    id,
                    post.getTitle(),
                    null,
                    null,
                    user,
                    post.getAuthorId()
                )
            );
            events.publishEvent(
                new PostReadModelEvent(id, null, PostReadModelEvent.Kind.SCORE, delta)
            );
        }
    }

    // 校验权限后删除帖子及关联数据
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        delete(id, false);
    }

    // AI 审核不通过：帖子原样退回作者的草稿箱，并摘掉标签、配图关系和各处索引；调用方已锁定帖子行
    @Transactional(rollbackFor = Exception.class)
    public long withdrawToDraft(PostEntity post) {
        var rows = images.selectList(
            Wrappers.<PostImageEntity>lambdaQuery()
                .eq(PostImageEntity::getPostId, post.getId())
                .orderByAsc(PostImageEntity::getSort)
                .orderByAsc(PostImageEntity::getId)
        );
        var draft = new PostDraftEntity();
        draft.setAuthorId(post.getAuthorId());
        draft.setCategory(post.getCategory());
        draft.setTitle(post.getTitle());
        draft.setContent(post.getContent());
        draft.setImageIds(
            rows
                .stream()
                .filter(i -> !PostImageKind.COVER.name().equals(i.getKind()))
                .map(PostImageEntity::getId)
                .toList()
        );
        draft.setCoverImageId(
            rows
                .stream()
                .filter(i -> PostImageKind.COVER.name().equals(i.getKind()))
                .map(PostImageEntity::getId)
                .findFirst()
                .orElse(null)
        );
        draft.setTags(tags.forPost(post.getId()));
        long draftId = drafts.addWithdrawn(draft);
        tags.detach(post.getId());
        images.detachFromPost(post.getId());
        posts.deleteById(post.getId());
        events.publishEvent(
            new PostReadModelEvent(
                post.getId(),
                post.getCategory(),
                PostReadModelEvent.Kind.DELETED,
                0
            )
        );
        return draftId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteForReview(long id) {
        if (!"ADMIN".equals(UserContext.require().role())) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        delete(id, true);
    }

    private void delete(long id, boolean reviewed) {
        var post = posts.selectByIdForUpdate(id);
        if (post == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (
            !reviewed && post.getAuthorId() != UserContext.require().userId()
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        tags.detach(id);
        images.delete(Wrappers.<PostImageEntity>lambdaQuery().eq(PostImageEntity::getPostId, id));
        posts.deleteById(id);
        events.publishEvent(
            new PostReadModelEvent(id, post.getCategory(), PostReadModelEvent.Kind.DELETED, 0)
        );
    }

    // 校验归属和配图后保存帖子修改
    @Transactional(rollbackFor = Exception.class)
    public void update(long id, CreatePostDTO dto) {
        long user = UserContext.require().userId();
        var post = posts.selectByIdForUpdate(id);
        if (post == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (post.getAuthorId() != user) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        if (dto.draftId() != null) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        PostRules.images(dto.imageIds(), dto.coverImageId());
        var names = PostRules.tags(dto.tags());
        var retained = new HashSet<>(dto.imageIds());
        if (dto.coverImageId() != null) retained.add(dto.coverImageId());
        var old = images.selectList(
            Wrappers.<PostImageEntity>lambdaQuery().eq(PostImageEntity::getPostId, id)
        );
        for (var image : old)
            if (!retained.contains(image.getId())) images.deleteById(image.getId());
        for (int i = 0; i < dto.imageIds().size(); i++) retainImage(
            dto.imageIds().get(i),
            id,
            user,
            i,
            PostImageKind.ATTACHMENT
        );
        if (dto.coverImageId() != null) retainImage(
            dto.coverImageId(),
            id,
            user,
            0,
            PostImageKind.COVER
        );
        var previousCategory = post.getCategory();
        post.setCategory(dto.category());
        post.setTitle(dto.title().trim());
        post.setContent(dto.content());
        post.setSummary(summary.summarize(dto.content()));
        post.setImageCount(dto.imageIds().size());
        posts.updateById(post);
        posts.updateCoverImage(id, dto.coverImageId());
        tags.replace(id, names);
        posts.touchContentVersion(id);
        aiEvents.post(id, "post.updated", aiEvents.needsImageReview(id));
        events.publishEvent(
            new PostReadModelEvent(id, previousCategory, PostReadModelEvent.Kind.UPDATED, 0)
        );
    }

    // 保留已有配图或关联新上传配图
    private void retainImage(long imageId, long post, long user, int sort, PostImageKind kind) {
        var image = images.selectById(imageId);
        if (
            image == null ||
            image.getUploaderId() != user ||
            (image.getPostId() != null && image.getPostId() != post)
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        if (image.getPostId() == null) {
            if (
                images.attachToPost(imageId, post, user, sort, kind.name()) != 1
            ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        } else {
            image.setSort(sort);
            image.setKind(kind.name());
            images.updateById(image);
        }
    }

    // 查询帖子并校验当前用户操作权限
    private PostEntity requirePost(long id) {
        var post = posts.selectByIdForUpdate(id);
        if (post == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return post;
    }
}
