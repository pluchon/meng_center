package com.pluchon.meng.im.controller;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.im.entity.ImRows;
import com.pluchon.meng.im.entity.MessageType;
import java.time.OffsetDateTime;
import java.util.List;

// 前端消息中心只读响应
public final class ImVO {

    private ImVO() {}

    public record Unread(long chat, long comment, long like, long system) {}

    public record Image(long id, int width, int height) {}

    public record Message(
        long id,
        long conversationId,
        long senderId,
        MessageType type,
        String content,
        String clientMsgId,
        OffsetDateTime createTime,
        List<Image> images
    ) {}

    public record Conversation(
        Long id,
        PublicUserVO peer,
        long unreadCount,
        long sentCount,
        long peerSentCount,
        long lastReadMessageId,
        long peerReadMessageId,
        String preview,
        OffsetDateTime lastMessageTime,
        boolean blocked,
        boolean online
    ) {}

    public record Messages(List<Message> records, boolean hasMore) {}

    public record Notification(
        ImRows.Notice notice,
        List<PublicUserVO> actors
    ) {}

    public record Group(
        String key,
        long total,
        long unreadCount,
        Notification latest
    ) {}

    public record Setting(boolean hidden) {}
}
