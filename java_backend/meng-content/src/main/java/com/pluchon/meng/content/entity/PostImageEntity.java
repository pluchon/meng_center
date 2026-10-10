package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post_image")
public class PostImageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private Long postId;
    private String fileName;
    private String thumbName;
    private Integer width;
    private Integer height;
    private Integer sort;
    private String kind;

    @TableField(
        insertStrategy = FieldStrategy.NEVER,
        updateStrategy = FieldStrategy.NEVER
    )
    private LocalDateTime createTime;

    @TableField(
        insertStrategy = FieldStrategy.NEVER,
        updateStrategy = FieldStrategy.NEVER
    )
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleteState;
}
