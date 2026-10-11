package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

// 评论持久化实体
@Data
@TableName("comment")
public class CommentEntity {

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属帖子编号
    private Long postId;

    // 作者编号
    private Long authorId;

    // 一级评论编号，零表示一级评论
    private Long rootId;

    // 回复目标评论编号
    private Long replyToCommentId;

    // 回复目标用户编号
    private Long replyToUserId;

    // 正文
    private String content;

    // 配图数量
    private Integer imageCount = 0;

    // 点赞次数
    private Long likeCount = 0L;

    // 回复数量
    private Long replyCount = 0L;

    // 请求地址属地
    private String ipRegion;

    // 创建时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    // 更新时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    // 逻辑删除标记
    @TableLogic private Integer deleteState;
}
