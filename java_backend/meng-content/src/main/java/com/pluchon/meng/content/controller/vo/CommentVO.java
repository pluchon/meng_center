package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.api.user.vo.PublicUserVO;

import java.time.LocalDateTime;
import java.util.List;

public record CommentVO(
        long id,
        String content,
        List<ImageVO> images,
        long likeCount,
        boolean liked,
        String ipRegion,
        LocalDateTime createTime,
        PublicUserVO author,
        boolean postAuthor,
        long replyCount,
        Long replyToUserId,
        String replyToNickname) {}
