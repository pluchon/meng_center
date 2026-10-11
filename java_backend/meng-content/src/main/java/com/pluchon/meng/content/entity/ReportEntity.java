package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.pluchon.meng.content.entity.enums.ReportReason;
import com.pluchon.meng.content.entity.enums.ReportStatus;
import com.pluchon.meng.content.entity.enums.ReportTarget;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class ReportEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;
    private ReportTarget targetType;
    private Long targetId;
    private ReportReason reason;
    private String note;
    private ReportStatus status;
    private Long handlerId;
    private LocalDateTime handleTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    @TableLogic private Integer deleteState;
}
