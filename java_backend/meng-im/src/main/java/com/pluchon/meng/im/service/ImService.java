package com.pluchon.meng.im.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.im.controller.ImDTO;
import com.pluchon.meng.im.controller.ImVO;
import com.pluchon.meng.im.entity.Channel;
import com.pluchon.meng.im.entity.ImRows;
import com.pluchon.meng.im.entity.MessageType;
import com.pluchon.meng.im.mapper.ImMapper;
import com.pluchon.meng.im.realtime.ImSocket;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

// 私信事务、权限与通知只读聚合
@Service
public class ImService {

    // 已提交的数据不因实时发送失败而返回失败
    private static final Logger LOG = LoggerFactory.getLogger(ImService.class);

    @Autowired
    private ImMapper mapper;

    @Autowired
    private PeerService peers;

    @Autowired
    private ImRateLimit limits;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ImSocket socket;

    public ImVO.Unread unread(long user) {
        return new ImVO.Unread(
            mapper.chatUnread(user),
            mapper.noticeUnread(user, Channel.COMMENT),
            mapper.noticeUnread(user, Channel.LIKE),
            mapper.noticeUnread(user, Channel.SYSTEM)
        );
    }

    public PageResult<ImVO.Conversation> conversations(long page, int size) {
        long user = user();
        long offset = offset(page, size);
        var rows = mapper.conversations(user, size, offset);
        var profiles = peers.batch(
            rows.stream().map(ImRows.Member::getPeerId).toList()
        );
        return new PageResult<>(
            rows
                .stream()
                .map(row -> conversation(row, profiles))
                .toList(),
            mapper.conversationCount(user),
            page,
            size
        );
    }

    public ImVO.Conversation peer(long id) {
        long user = user();
        if (user == id) throw new BusinessException(ImError.SELF);
        var profile = peers.require(id);
        var row = mapper.peerMember(user, id);
        if (row == null) return new ImVO.Conversation(
            null,
            profile,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            mapper.blocked(user, id) > 0,
            socket.online(id)
        );
        return conversation(row, Map.of(id, profile));
    }

    private ImVO.Conversation conversation(
        ImRows.Member row,
        Map<Long, com.pluchon.meng.api.user.vo.PublicUserVO> profiles
    ) {
        return new ImVO.Conversation(
            row.getConversationId(),
            peers.placeholder(row.getPeerId(), profiles),
            row.getUnreadCount(),
            row.getSentCount(),
            row.getPeerSentCount(),
            Math.max(row.getLastReadMessageId(), row.getClearedMessageId()),
            row.getPeerReadMessageId(),
            row.getLastMessagePreview(),
            time(row.getLastMessageTime()),
            Boolean.TRUE.equals(row.getBlocked()),
            socket.online(row.getPeerId())
        );
    }

    public ImVO.Messages messages(long id, Long before, int size) {
        if (size < 1 || size > 50 || (before != null && before < 1)) invalid();
        var member = require(id, user());
        var rows = new ArrayList<>(
            mapper.messages(id, member.getClearedMessageId(), before, size + 1)
        );
        boolean more = rows.size() > size;
        if (more) rows.remove(rows.size() - 1);
        Collections.reverse(rows);
        return new ImVO.Messages(messageVOs(rows), more);
    }

    public ImVO.Message send(ImDTO.Send dto) {
        MessageRules.validate(dto);
        long user = user();
        if (user == dto.peerId()) throw new BusinessException(ImError.SELF);
        var existing = mapper.duplicate(user, dto.clientMsgId());
        if (existing != null) return messageVOs(List.of(existing)).get(0);
        peers.require(dto.peerId());
        limits.message(user);
        try {
            return new TransactionTemplate(transactions).execute(status ->
                sendLocked(user, dto)
            );
        } catch (DuplicateKeyException duplicate) {
            var first = mapper.duplicate(user, dto.clientMsgId());
            if (first == null) throw duplicate;
            return messageVOs(List.of(first)).get(0);
        }
    }

    private ImVO.Message sendLocked(long user, ImDTO.Send dto) {
        long peer = dto.peerId();
        mapper.createPair(Math.min(user, peer), Math.max(user, peer));
        long id = mapper.pair(Math.min(user, peer), Math.max(user, peer));
        mapper.lock(id);
        mapper.createMember(id, user, peer);
        mapper.createMember(id, peer, user);
        var existing = mapper.duplicate(user, dto.clientMsgId());
        if (existing != null) return messageVOs(List.of(existing)).get(0);
        if (mapper.blocked(peer, user) > 0) throw new BusinessException(
            ImError.BLOCKED
        );
        var self = mapper.member(id, user);
        var other = mapper.member(id, peer);
        if (
            other.getSentCount() == 0 && self.getSentCount() >= 3
        ) throw new BusinessException(ImError.WAIT_REPLY);
        var message = new ImRows.Message();
        message.setConversationId(id);
        message.setSenderId(user);
        message.setType(
            dto.imageIds().isEmpty() ? MessageType.TEXT : MessageType.IMAGE
        );
        message.setContent(
            dto.imageIds().isEmpty() ? dto.content().strip() : null
        );
        message.setClientMsgId(dto.clientMsgId());
        mapper.insertMessage(message);
        for (int i = 0; i < dto.imageIds().size(); i++) {
            if (
                mapper.attach(
                    dto.imageIds().get(i),
                    user,
                    message.getId(),
                    i
                ) != 1
            ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        message = mapper.message(message.getId());
        String preview =
            message.getContent() == null ? "[图片]" : message.getContent();
        mapper.received(id, user, 0, 1, preview, message.getCreateTime());
        mapper.received(id, peer, 1, 0, preview, message.getCreateTime());
        var result = messageVOs(List.of(message)).get(0);
        afterCommit(() -> {
            socket.push(user, "dm.message", result);
            socket.push(peer, "dm.message", result);
            pushUnread(user);
            pushUnread(peer);
        });
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void read(long id, long messageId) {
        long user = user();
        mapper.lock(id);
        var member = require(id, user);
        var message = mapper.message(messageId);
        if (
            message == null ||
            message.getConversationId() != id ||
            messageId <= member.getClearedMessageId()
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        mapper.read(id, user, messageId);
        long read = mapper.member(id, user).getLastReadMessageId();
        afterCommit(() -> {
            socket.push(
                member.getPeerId(),
                "dm.read",
                Map.of("conversationId", id, "messageId", read, "userId", user)
            );
            socket.push(
                user,
                "dm.read",
                Map.of("conversationId", id, "messageId", read, "userId", user)
            );
            pushUnread(user);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        long user = user();
        mapper.lock(id);
        require(id, user);
        mapper.hide(id, user, mapper.lastMessage(id));
        afterCommit(() -> pushUnread(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public void block(long peer, boolean blocked) {
        long user = user();
        if (peer < 1 || peer == user) invalid();
        if (blocked) mapper.block(user, peer);
        else mapper.unblock(user, peer);
    }

    public ImVO.Setting setting(long user) {
        return new ImVO.Setting(Boolean.TRUE.equals(mapper.hidden(user)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ImVO.Setting setting(boolean hidden) {
        long user = user();
        mapper.setting(user, hidden);
        afterCommit(() -> socket.changed(user));
        return new ImVO.Setting(hidden);
    }

    public PageResult<ImVO.Notification> notifications(
        Channel channel,
        String source,
        String category,
        long page,
        int size
    ) {
        long user = user();
        validateGroup(channel, source, category, false);
        long offset = offset(page, size);
        var rows =
            channel == Channel.LIKE
                ? mapper.likes(user, channel, source, category, size, offset)
                : mapper.notices(user, channel, source, category, size, offset);
        long total =
            channel == Channel.LIKE
                ? mapper.likeCount(user, channel, source, category)
                : mapper.noticeCount(user, channel, source, category);
        return new PageResult<>(notificationVOs(rows), total, page, size);
    }

    public List<ImVO.Group> groups(Channel channel) {
        var keys =
            channel == Channel.SYSTEM
                ? List.of("GROWTH", "REPORT", "APPROVAL")
                : List.of("POST");
        long user = user();
        var groups = new ArrayList<ImVO.Group>();
        for (var key : keys) {
            String source = channel == Channel.SYSTEM ? null : key;
            String category = channel == Channel.SYSTEM ? key : null;
            var latest = mapper.notices(user, channel, source, category, 1, 0);
            var notices = notificationVOs(latest);
            long total =
                channel == Channel.LIKE
                    ? mapper.likeCount(user, channel, source, category)
                    : mapper.noticeCount(user, channel, source, category);
            // 分组未读数来自结构化列表查询，避免未读聚合干扰频道角标
            long unread = groupUnread(user, channel, source, category);
            groups.add(
                new ImVO.Group(
                    key,
                    total,
                    unread,
                    notices.isEmpty() ? null : notices.get(0)
                )
            );
        }
        return groups;
    }

    private long groupUnread(
        long user,
        Channel channel,
        String source,
        String category
    ) {
        return mapper.groupUnread(user, channel, source, category);
    }

    @Transactional(rollbackFor = Exception.class)
    public void readNotifications(ImDTO.NoticeRead dto) {
        validateGroup(dto.channel(), dto.source(), dto.category(), true);
        long user = user();
        mapper.readNotices(user, dto.channel(), dto.source(), dto.category());
        afterCommit(() -> pushUnread(user));
    }

    private List<ImVO.Notification> notificationVOs(List<ImRows.Notice> rows) {
        var actors = new HashMap<Long, List<Long>>();
        var all = new ArrayList<Long>();
        for (var row : rows) {
            var ids =
                row.getActorId() == null
                    ? List.<Long>of()
                    : row.getChannel() == Channel.LIKE
                      ? mapper
                            .likeActors(row)
                            .stream()
                            .map(ImRows.Notice::getActorId)
                            .toList()
                      : List.of(row.getActorId());
            actors.put(row.getId(), ids);
            all.addAll(ids);
        }
        var profiles = peers.batch(all);
        return rows
            .stream()
            .map(row ->
                new ImVO.Notification(
                    row,
                    actors
                        .get(row.getId())
                        .stream()
                        .map(id -> peers.placeholder(id, profiles))
                        .toList()
                )
            )
            .toList();
    }

    public List<ImVO.Message> messageVOs(List<ImRows.Message> rows) {
        var images = rows.isEmpty()
            ? List.<ImRows.Image>of()
            : mapper.images(rows.stream().map(ImRows.Message::getId).toList());
        var grouped = new HashMap<Long, List<ImVO.Image>>();
        for (var image : images)
            grouped
                .computeIfAbsent(image.getMessageId(), key -> new ArrayList<>())
                .add(
                    new ImVO.Image(
                        image.getId(),
                        image.getWidth(),
                        image.getHeight()
                    )
                );
        return rows
            .stream()
            .map(row ->
                new ImVO.Message(
                    row.getId(),
                    row.getConversationId(),
                    row.getSenderId(),
                    row.getType(),
                    row.getContent(),
                    row.getClientMsgId(),
                    time(row.getCreateTime()),
                    grouped.getOrDefault(row.getId(), List.of())
                )
            )
            .toList();
    }

    public ImRows.Member require(long id, long user) {
        var member = mapper.member(id, user);
        if (member == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        return member;
    }

    public void pushUnread(long user) {
        socket.push(user, "unread", unread(user));
    }

    public static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (RuntimeException unavailable) {
                        LOG.warn(
                            "IM realtime notification unavailable after commit"
                        );
                    }
                }
            }
        );
    }

    private static OffsetDateTime time(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static long user() {
        return UserContext.require().userId();
    }

    private static long offset(long page, int size) {
        if (
            page < 1 ||
            size < 1 ||
            size > 50 ||
            page - 1 > Long.MAX_VALUE / size
        ) invalid();
        return (page - 1) * size;
    }

    private static void validateGroup(
        Channel channel,
        String source,
        String category,
        boolean required
    ) {
        if (
            channel == null ||
            (source != null && !source.equals("POST")) ||
            (category != null &&
                !List.of("GROWTH", "REPORT", "APPROVAL").contains(category)) ||
            (channel == Channel.SYSTEM && source != null) ||
            (channel != Channel.SYSTEM && category != null) ||
            (required &&
                (channel == Channel.SYSTEM ? category == null : source == null))
        ) invalid();
    }

    private static void invalid() {
        throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }
}
