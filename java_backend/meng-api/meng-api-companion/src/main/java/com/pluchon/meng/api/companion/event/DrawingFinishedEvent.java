package com.pluchon.meng.api.companion.event;

// 画作仍需通过 companion 本人鉴权读取，不携带私人磁盘路径。
public record DrawingFinishedEvent(
    long userId,
    String characterCode,
    String characterName,
    long sessionId,
    long workId,
    String title
) {}
