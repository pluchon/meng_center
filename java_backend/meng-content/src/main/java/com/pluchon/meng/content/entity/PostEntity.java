package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pluchon.meng.content.entity.enums.PostCategory;

import lombok.Data;

import java.time.LocalDateTime;

// 帖子持久化实体
@Data
@TableName("post")
public class PostEntity {

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

    // 正文摘要
    private String summary;

    // 配图数量
    private Integer imageCount = 0;

    // 封面配图编号
    private Long coverImageId;

    // 浏览次数
    private Long viewCount = 0L;

    // 点赞次数
    private Long likeCount = 0L;

    // 评论数量
    private Long commentCount = 0L;

    // 帖子热度分值
    private Long hotScore = 0L;

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
