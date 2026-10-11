package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.pluchon.meng.content.entity.enums.PostCategory;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

// 帖子草稿持久化实体
@Data
@TableName(value = "post_draft", autoResultMap = true)
public class PostDraftEntity {

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 作者编号
    private Long authorId;

    // 帖子分类
    private PostCategory category;

    // 标题
    private String title;

    // 正文
    private String content;

    // 配图编号集合
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> imageIds;

    // 封面配图编号
    private Long coverImageId;

    // 草稿标签名称
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    // 创建时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    // 更新时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    // 逻辑删除标记
    @TableLogic private Integer deleteState;
}
