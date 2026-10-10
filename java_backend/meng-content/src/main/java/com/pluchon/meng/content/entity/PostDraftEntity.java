package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName(value = "post_draft", autoResultMap = true)
public class PostDraftEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;
    private PostCategory category;
    private String title;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> imageIds;

    private Long coverImageId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

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
