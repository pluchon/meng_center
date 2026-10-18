package com.pluchon.meng.api.companion.event;

import java.time.LocalDate;

// 约定提醒只提供通知所需的本人数据快照。
public record PromiseDueEvent(
    long userId,
    String characterCode,
    String characterName,
    long memoryId,
    String content,
    LocalDate dueDate
) {}
