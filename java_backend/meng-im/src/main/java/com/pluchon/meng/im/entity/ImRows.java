package com.pluchon.meng.im.entity;

import java.time.LocalDateTime;
import lombok.Data;

// 消息域持久化记录
public final class ImRows {

    private ImRows() {}

    // 双方会话及每位用户的独立游标
    @Data
    public static class Member {

        // 记录主键
        private Long id;
        // 所属会话
        private Long conversationId;
        // 当前成员
        private Long userId;
        // 对方用户
        private Long peerId;
        // 本人的未读数
        private Long unreadCount;
        // 本人累计发送条数
        private Long sentCount;
        // 本人已读游标
        private Long lastReadMessageId;
        // 删除时的历史边界
        private Long clearedMessageId;
        // 列表消息摘要
        private String lastMessagePreview;
        // 列表最近消息时间
        private LocalDateTime lastMessageTime;
        // 本人是否隐藏会话
        private Boolean hiddenState;
        // 联表取得对方计数与本人屏蔽状态，列表不逐条查询
        // 对方累计发送条数
        private Long peerSentCount;
        // 对方已读游标
        private Long peerReadMessageId;
        // 本人是否屏蔽对方
        private Boolean blocked;
    }

    // 私信记录
    @Data
    public static class Message {

        // 记录主键
        private Long id;
        // 所属会话
        private Long conversationId;
        // 发送人
        private Long senderId;
        // 业务类型
        private MessageType type;
        // 消息文字
        private String content;
        // 发送人的幂等标识
        private String clientMsgId;
        // 撤回时间，空表示没有撤回
        private LocalDateTime revokedTime;
        // 距离撤回截止还剩的毫秒数，由查询计算
        private Long revokeMillis;
        // 数据库 UTC 创建时间
        private LocalDateTime createTime;
    }

    // 鉴权配图记录
    @Data
    public static class Image {

        // 记录主键
        private Long id;
        // 图片上传人
        private Long uploaderId;
        // 已关联消息
        private Long messageId;
        // 消息内排序
        private Integer sort;
        // 原图存储文件名
        private String fileName;
        // 缩略图文件名
        private String thumbName;
        // 原图宽度
        private Integer width;
        // 原图高度
        private Integer height;
        // 存储字节数
        private Long bytes;
    }

    // 结构化通知快照
    @Data
    public static class Notice {

        // 记录主键
        private Long id;
        // 通知接收人
        private Long receiverId;
        // 通知频道
        private Channel channel;
        // 来源分组
        private String source;
        // 系统类型分组
        private String category;
        // 业务类型
        private NoticeType type;
        // 触发人
        private Long actorId;
        // 帖子目标
        private Long postId;
        // 评论目标
        private Long commentId;
        // 帖子标题快照
        private String postTitle;
        // 触发评论摘要
        private String excerpt;
        // 被回复的原评论摘要
        private String originalExcerpt;
        // 新的等级
        private Integer level;
        // 分组已读标记
        private Boolean readState;
        // 通知去重标识
        private String dedupeKey;
        // 萌萌通知的本人会话目标。
        // 数据库 UTC 创建时间
        private LocalDateTime createTime;
        // 聚合互动人数
        private Long actorCount;
    }
}
