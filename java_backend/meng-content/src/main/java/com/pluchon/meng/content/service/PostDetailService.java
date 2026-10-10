package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.controller.vo.PostDetailVO;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.entity.enums.PostImageKind;
import com.pluchon.meng.content.mapper.*;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostDetailService {

    private final PostMapper posts;
    private final PostImageMapper images;
    private final PostRelationMapper relations;
    private final PostConverter converter;
    private final AuthorService authors;
    private final TagService tags;
    private final StringRedisTemplate redis;
    private final ContentProperties properties;

    public PostDetailVO get(long id) {
        long user = UserContext.require().userId();
        var post = posts.selectById(id);
        if (post == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        String key = "meng:content:viewed:" + id + ":" + user;
        if (
            Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24))
            )
        ) {
            try {
                if (
                    posts.view(id, properties.viewWeight()) != 1
                ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            } catch (RuntimeException error) {
                redis.delete(key);
                throw error;
            }
            post = posts.selectById(id);
        }
        if (post == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
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
        var counts = relations.favoriteCounts(List.of(id));
        long favorites = counts.isEmpty() ? 0 : counts.get(0).count();
        var feed = converter.feed(
            post,
            attachments,
            authors.batch(List.of(post.getAuthorId())).get(post.getAuthorId()),
            !relations.liked(user, List.of(id)).isEmpty(),
            !relations.favorited(user, List.of(id)).isEmpty(),
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
            feed.author()
        );
    }
}
