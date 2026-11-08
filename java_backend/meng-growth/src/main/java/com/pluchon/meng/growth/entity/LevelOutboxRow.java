package com.pluchon.meng.growth.entity;

import java.time.LocalDateTime;
import lombok.Data;

// 待发送的等级变化事件
@Data
public class LevelOutboxRow {

    // 主键
    private Long id;

    // 事件编号，重发时不变，消费方据此去重
    private String eventId;

    // 升级的用户
    private Long userId;

    // 新等级
    private Integer level;

    // 升级发生时间（UTC）
    private LocalDateTime occurredAt;
}
