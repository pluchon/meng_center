package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment_image")
public class CommentImageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private Long commentId;
    private String fileName;
    private String thumbName;
    private Integer width;
    private Integer height;
    private Integer sort;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    @TableLogic private Integer deleteState;
}
