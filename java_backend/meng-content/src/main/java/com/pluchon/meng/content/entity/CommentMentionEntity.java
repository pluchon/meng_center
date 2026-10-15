package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

// 评论里被提到的用户
@Data
@TableName("comment_mention")
public class CommentMentionEntity {

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属评论编号
    private Long commentId;

    // 被提到的用户编号
    private Long userId;

    // 评论发出时的昵称，正文里的 @昵称 靠它对上
    private String nickname;

    // 创建时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    // 更新时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    // 逻辑删除标记
    @TableLogic private Integer deleteState;
}
