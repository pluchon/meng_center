package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post")
public class PostEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;
    private PostCategory category;
    private String title;
    private String content;
    private String summary;
    private Integer imageCount;
    private Long coverImageId;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long hotScore;
    private String ipRegion;

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
