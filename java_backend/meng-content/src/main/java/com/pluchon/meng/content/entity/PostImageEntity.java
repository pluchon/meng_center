package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

// 帖子配图持久化实体
@Data
@TableName("post_image")
public class PostImageEntity {

    private Boolean aiGenerated = false;

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 上传者编号
    private Long uploaderId;

    // 所属帖子编号
    private Long postId;

    // 原图文件名
    private String fileName;

    // 缩略图文件名
    private String thumbName;

    // 原图宽度
    private Integer width;

    // 原图高度
    private Integer height;

    // 配图显示顺序
    private Integer sort;

    // 配图用途
    private String kind;

    // 创建时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    // 更新时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    // 逻辑删除标记
    @TableLogic
    private Integer deleteState;
}
