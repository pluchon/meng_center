package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import java.util.List;

public record PostFeedVO(
    long id,
    PostCategory category,
    String title,
    String summary,
    List<ImageVO> images,
    int imageCount,
    long viewCount,
    long likeCount,
    long commentCount,
    long favoriteCount,
    boolean liked,
    boolean favorited,
    String ipRegion,
    LocalDateTime createTime,
    PublicUserVO author
) {}
