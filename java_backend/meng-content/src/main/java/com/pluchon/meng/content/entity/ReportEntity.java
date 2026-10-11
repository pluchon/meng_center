package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pluchon.meng.content.entity.enums.ReportReason;
import com.pluchon.meng.content.entity.enums.ReportStatus;
import com.pluchon.meng.content.entity.enums.ReportTarget;

import lombok.Data;

import java.time.LocalDateTime;

// 举报持久化实体
@Data
@TableName("report")
public class ReportEntity {

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 举报人编号
    private Long reporterId;

    // 举报目标类型
    private ReportTarget targetType;

    // 举报目标编号
    private Long targetId;

    // 举报原因
    private ReportReason reason;

    // 补充说明
    private String note;

    // 处理状态
    private ReportStatus status;

    // 处理人编号
    private Long handlerId;

    // 处理时间
    private LocalDateTime handleTime;

    // 创建时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    // 更新时间
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    // 逻辑删除标记
    @TableLogic private Integer deleteState;
}
