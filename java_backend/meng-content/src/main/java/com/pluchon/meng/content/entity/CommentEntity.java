package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class CommentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long authorId;
    private Long rootId;
    private Long replyToCommentId;
    private Long replyToUserId;
    private String content;
    private Integer imageCount;
    private Long likeCount;
    private Long replyCount;
    private String ipRegion;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    @TableLogic private Integer deleteState;
}
