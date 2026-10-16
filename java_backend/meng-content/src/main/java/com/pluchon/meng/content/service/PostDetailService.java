package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.controller.vo.PostDetailVO;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.entity.enums.PostImageKind;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.PostImageMapper;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.mapper.PostRelationMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// 帖子详情读取与浏览计数
@Service
public class PostDetailService {

    @Autowired
    private RecommendationProfileService profiles;

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

    // 帖子标签业务操作
    @Autowired
    private TagService tags;

    // 缓存和操作计数
    @Autowired
    private StringRedisTemplate redis;

    // 当前业务域配置
    @Autowired
    private ContentProperties properties;

    // 发布领域事件
    @Autowired
    private ApplicationEventPublisher events;

    // 浏览计数使用的短事务模板
    @Autowired
    private TransactionTemplate transactions;

    // 批量组装帖子详情并在短事务内累计浏览
    public PostDetailVO get(long id) {
        long user = UserContext.require().userId();
        var post = posts.selectById(id);
        if (post == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (
            !"NORMAL".equals(post.getReviewState().name()) &&
            post.getAuthorId() != user &&
            !"ADMIN".equals(UserContext.require().role())
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        profiles.viewed(user, id);
        String key = "meng:content:viewed:" + id + ":" + user;
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24)))) {
            long author = post.getAuthorId();
            try {
                transactions.executeWithoutResult(status -> {
                    if (
                        posts.incrementViews(id, properties.viewWeight()) != 1
                    ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    statistics.record(author, CreatorMetric.VIEW);
                    events.publishEvent(
                        new PostReadModelEvent(
                            id,
                            null,
                            PostReadModelEvent.Kind.SCORE,
                            properties.viewWeight()
                        )
                    );
                });
            } catch (RuntimeException error) {
                redis.delete(key);
                throw error;
            }
            post = posts.selectById(id);
        }
        if (post == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var attachments = images.selectList(
            Wrappers.<PostImageEntity>lambdaQuery()
                .eq(PostImageEntity::getPostId, id)
                .eq(PostImageEntity::getKind, PostImageKind.ATTACHMENT.name())
                .orderByAsc(PostImageEntity::getSort)
        );
        var cover =
            post.getCoverImageId() == null
                ? attachments.isEmpty()
                    ? null
                    : attachments.get(0)
                : images.selectById(post.getCoverImageId());
        var counts = relations.selectFavoriteCounts(List.of(id));
        long favorites = counts.isEmpty() ? 0 : counts.get(0).count();
        var feed = converter.feed(
            post,
            attachments,
            authors.batch(List.of(post.getAuthorId())).get(post.getAuthorId()),
            !relations.selectLikedPostIds(user, List.of(id)).isEmpty(),
            !relations.selectFavoritedPostIds(user, List.of(id)).isEmpty(),
            favorites
        );
        return new PostDetailVO(
            feed.id(),
            feed.category(),
            feed.title(),
            feed.summary(),
            post.getContent(),
            attachments.stream().map(converter::image).toList(),
            feed.imageCount(),
            cover == null ? null : converter.image(cover),
            tags.forPost(id),
            feed.viewCount(),
            feed.likeCount(),
            feed.commentCount(),
            feed.favoriteCount(),
            feed.liked(),
            feed.favorited(),
            feed.ipRegion(),
            feed.createTime(),
            feed.author(),
            post.getReviewState().name()
        );
    }
}
