package com.pluchon.meng.content.converter;

import cn.hutool.core.bean.BeanUtil;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.controller.dto.CreateCommentDTO;
import com.pluchon.meng.content.controller.vo.CommentVO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.controller.vo.MentionVO;
import com.pluchon.meng.content.entity.CommentEntity;
import com.pluchon.meng.content.entity.CommentImageEntity;
import com.pluchon.meng.content.entity.CommentMentionEntity;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 评论与配图的字段转换
@Component
public class CommentConverter {

    // 复制评论正文和回复目标，归属信息由业务层校验后填写
    public CommentEntity create(CreateCommentDTO dto) {
        var comment = new CommentEntity();
        BeanUtil.copyProperties(dto, comment);
        return comment;
    }

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
            List<CommentMentionEntity> mentions,
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
                target == null ? null : target.nickname(),
                c.getPinnedTime() != null,
                mentions.stream().map(m -> new MentionVO(m.getUserId(), m.getNickname())).toList());
    }
}
