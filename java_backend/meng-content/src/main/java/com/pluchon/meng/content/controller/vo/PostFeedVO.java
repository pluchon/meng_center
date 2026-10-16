package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import java.util.List;

// 帖子流展示数据
public record PostFeedVO(
    // 记录主键
    long id,
    // 帖子分类
    PostCategory category,
    // 标题
    String title,
    // 正文摘要
    String summary,
    // 配图展示集合
    List<ImageVO> images,
    // 配图数量
    int imageCount,
    // 浏览次数
    long viewCount,
    // 点赞次数
    long likeCount,
    // 评论数量
    long commentCount,
    // 收藏次数
    long favoriteCount,
    // 当前用户是否点赞
    boolean liked,
    // 当前用户是否收藏
    boolean favorited,
    // 请求地址属地
    String ipRegion,
    // 创建时间
    LocalDateTime createTime,
    // 作者公开资料
    PublicUserVO author,
    RecommendationReason reason,
    String reviewState
) {
    public record RecommendationReason(String type, String author, String topic, String title) {}

    public PostFeedVO(
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
    ) {
        this(
            id,
            category,
            title,
            summary,
            images,
            imageCount,
            viewCount,
            likeCount,
            commentCount,
            favoriteCount,
            liked,
            favorited,
            ipRegion,
            createTime,
            author,
            null,
            "NORMAL"
        );
    }

    public PostFeedVO recommended(RecommendationReason value) {
        return new PostFeedVO(
            id,
            category,
            title,
            summary,
            images,
            imageCount,
            viewCount,
            likeCount,
            commentCount,
            favoriteCount,
            liked,
            favorited,
            ipRegion,
            createTime,
            author,
            value,
            reviewState
        );
    }
}
