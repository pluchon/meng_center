package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.pluchon.meng.content.entity.enums.FolderColor;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("favorite_folder")
public class FavoriteFolder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String name;
    private String description;
    private String coverName;
    private FolderColor color;
    private Long defaultOwnerId;

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
