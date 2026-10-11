package com.pluchon.meng.content.converter;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.controller.vo.*;
import com.pluchon.meng.content.entity.*;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CommentConverter {
    public ImageVO image(CommentImageEntity image) {
        return new ImageVO(
                image.getId(),
                PostConverter.FILE_ROOT + image.getFileName(),
                PostConverter.FILE_ROOT + image.getThumbName(),
                image.getWidth(),
                image.getHeight());
    }

    public CommentVO comment(
            CommentEntity c,
            List<CommentImageEntity> images,
            Map<Long, PublicUserVO> authors,
            boolean liked,
            long postAuthor) {
        var author =
                authors.getOrDefault(
                        c.getAuthorId(), new PublicUserVO(c.getAuthorId(), null, null, 1));
        var target = c.getReplyToUserId() == null ? null : authors.get(c.getReplyToUserId());
        return new CommentVO(
                c.getId(),
                c.getContent(),
                images.stream().map(this::image).toList(),
                c.getLikeCount(),
                liked,
                c.getIpRegion(),
                c.getCreateTime(),
                author,
                c.getAuthorId() == postAuthor,
                c.getReplyCount(),
                c.getReplyToUserId(),
                target == null ? null : target.nickname());
    }
}
