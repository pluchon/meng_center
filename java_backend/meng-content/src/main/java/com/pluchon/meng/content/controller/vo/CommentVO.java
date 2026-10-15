package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.api.user.vo.PublicUserVO;

import java.time.LocalDateTime;
import java.util.List;

// 评论展示数据
public record CommentVO(
        // 记录主键
        long id,
        // 正文
        String content,
        // 配图展示集合
        List<ImageVO> images,
        // 点赞次数
        long likeCount,
        // 当前用户是否点赞
        boolean liked,
        // 请求地址属地
        String ipRegion,
        // 创建时间
        LocalDateTime createTime,
        // 作者公开资料
        PublicUserVO author,
        // 是否为帖子作者
        boolean postAuthor,
        // 回复数量
        long replyCount,
        // 回复目标用户编号
        Long replyToUserId,
        // 回复目标昵称
        String replyToNickname,
        // 是否被作者置顶
        boolean pinned,
        // 正文里 @ 到的用户
        List<MentionVO> mentions) {}
