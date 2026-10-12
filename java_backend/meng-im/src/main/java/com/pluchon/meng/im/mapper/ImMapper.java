package com.pluchon.meng.im.mapper;

import com.pluchon.meng.im.entity.Channel;
import com.pluchon.meng.im.entity.ImRows;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 消息域独占库内的查询和原子写入
@Mapper
public interface ImMapper {
    // 会话展示的共同投影，一次取得双方游标和屏蔽状态
    String MEMBERS =
        "SELECT m.*,p.sent_count peer_sent_count,p.last_read_message_id peer_read_message_id," +
        "EXISTS(SELECT 1 FROM user_block b WHERE b.user_id=m.user_id AND b.blocked_user_id=m.peer_id AND b.delete_state=0) blocked " +
        "FROM conversation_member m JOIN conversation_member p ON p.conversation_id=m.conversation_id AND p.user_id=m.peer_id ";

    // 读取当前通知分组的未读数
    @Select(
        """
        <script>SELECT COUNT(*) FROM notification WHERE receiver_id=#{user} AND channel=#{channel} AND read_state=0 AND delete_state=0
        <if test="source != null">AND source=#{source}</if><if test="category != null">AND category=#{category}</if></script>
        """
    )
    long groupUnread(
        @Param("user") long user,
        @Param("channel") Channel channel,
        @Param("source") String source,
        @Param("category") String category
    );

    // member 对应的消息域查询
    @Select(
        MEMBERS +
            "WHERE m.conversation_id=#{id} AND m.user_id=#{user} AND m.delete_state=0"
    )
    ImRows.Member member(@Param("id") long id, @Param("user") long user);

    // peerMember 对应的消息域查询
    @Select(
        MEMBERS +
            "WHERE m.user_id=#{user} AND m.peer_id=#{peer} AND m.delete_state=0"
    )
    ImRows.Member peerMember(
        @Param("user") long user,
        @Param("peer") long peer
    );

    // lock 对应的消息域查询
    @Select(
        """
        SELECT id FROM conversation WHERE id=#{id} FOR UPDATE
        """
    )
    Long lock(long id);

    // createPair 对应的消息域查询
    @Insert(
        """
        INSERT INTO conversation(user_low_id,user_high_id) VALUES(#{low},#{high}) ON DUPLICATE KEY UPDATE id=id
        """
    )
    void createPair(@Param("low") long low, @Param("high") long high);

    // pair 对应的消息域查询
    @Select(
        """
        SELECT id FROM conversation WHERE user_low_id=#{low} AND user_high_id=#{high}
        """
    )
    long pair(@Param("low") long low, @Param("high") long high);

    // createMember 对应的消息域查询
    @Insert(
        """
        INSERT INTO conversation_member(conversation_id,user_id,peer_id) VALUES(#{id},#{user},#{peer}) ON DUPLICATE KEY UPDATE id=id
        """
    )
    void createMember(
        @Param("id") long id,
        @Param("user") long user,
        @Param("peer") long peer
    );

    // conversations 对应的消息域查询
    @Select(
        MEMBERS +
            "WHERE m.user_id=#{user} AND m.hidden_state=0 AND m.delete_state=0 ORDER BY m.last_message_time DESC,m.id DESC LIMIT #{size} OFFSET #{offset}"
    )
    List<ImRows.Member> conversations(
        @Param("user") long user,
        @Param("size") int size,
        @Param("offset") long offset
    );

    // conversationCount 对应的消息域查询
    @Select(
        """
        SELECT COUNT(*) FROM conversation_member WHERE user_id=#{user} AND hidden_state=0 AND delete_state=0
        """
    )
    long conversationCount(long user);

    // chatUnread 对应的消息域查询
    @Select(
        """
        SELECT COALESCE(SUM(unread_count),0) FROM conversation_member WHERE user_id=#{user} AND hidden_state=0 AND delete_state=0
        """
    )
    long chatUnread(long user);

    // duplicate 对应的消息域查询
    @Select(
        """
        SELECT * FROM private_message WHERE sender_id=#{user} AND client_msg_id=#{client}
        """
    )
    ImRows.Message duplicate(
        @Param("user") long user,
        @Param("client") String client
    );

    // message 对应的消息域查询
    @Select(
        """
        SELECT * FROM private_message WHERE id=#{id} AND delete_state=0
        """
    )
    ImRows.Message message(long id);

    // insertMessage 对应的消息域查询
    @Insert(
        """
        INSERT INTO private_message(conversation_id,sender_id,type,content,client_msg_id) VALUES(#{conversationId},#{senderId},#{type},#{content},#{clientMsgId})
        """
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMessage(ImRows.Message message);

    // messages 对应的消息域查询
    @Select(
        """
               <script>SELECT * FROM private_message WHERE conversation_id=#{id} AND id &gt; #{cleared} AND delete_state=0
        <if test="before != null">AND id &lt; #{before}</if> ORDER BY id DESC LIMIT #{size}</script>
               """
    )
    List<ImRows.Message> messages(
        @Param("id") long id,
        @Param("cleared") long cleared,
        @Param("before") Long before,
        @Param("size") int size
    );

    // received 对应的消息域查询
    @Update(
        """
               UPDATE conversation_member SET hidden_state=0,unread_count=unread_count+#{unread},sent_count=sent_count+#{sent},
        last_message_preview=#{preview},last_message_time=#{time} WHERE conversation_id=#{id} AND user_id=#{user}
               """
    )
    void received(
        @Param("id") long id,
        @Param("user") long user,
        @Param("unread") int unread,
        @Param("sent") int sent,
        @Param("preview") String preview,
        @Param("time") java.time.LocalDateTime time
    );

    // read 对应的消息域查询
    @Update(
        """
               UPDATE conversation_member SET last_read_message_id=GREATEST(last_read_message_id,#{message}),
        unread_count=(SELECT COUNT(*) FROM private_message WHERE conversation_id=#{id} AND sender_id <> #{user}
        AND id > GREATEST(#{message},last_read_message_id,cleared_message_id) AND delete_state=0)
        WHERE conversation_id=#{id} AND user_id=#{user}
               """
    )
    void read(
        @Param("id") long id,
        @Param("user") long user,
        @Param("message") long message
    );

    // lastMessage 对应的消息域查询
    @Select(
        """
        SELECT COALESCE(MAX(id),0) FROM private_message WHERE conversation_id=#{id}
        """
    )
    long lastMessage(long id);

    // hide 对应的消息域查询
    @Update(
        """
        UPDATE conversation_member SET hidden_state=1,unread_count=0,cleared_message_id=#{last} WHERE conversation_id=#{id} AND user_id=#{user}
        """
    )
    void hide(
        @Param("id") long id,
        @Param("user") long user,
        @Param("last") long last
    );

    // blocked 对应的消息域查询
    @Select(
        """
        SELECT COUNT(*) FROM user_block WHERE user_id=#{user} AND blocked_user_id=#{peer} AND delete_state=0
        """
    )
    int blocked(@Param("user") long user, @Param("peer") long peer);

    // block 对应的消息域查询
    @Insert(
        """
        INSERT INTO user_block(user_id,blocked_user_id) VALUES(#{user},#{peer}) ON DUPLICATE KEY UPDATE delete_state=0
        """
    )
    void block(@Param("user") long user, @Param("peer") long peer);

    // unblock 对应的消息域查询
    @Update(
        """
        UPDATE user_block SET delete_state=1 WHERE user_id=#{user} AND blocked_user_id=#{peer}
        """
    )
    void unblock(@Param("user") long user, @Param("peer") long peer);

    // hidden 对应的消息域查询
    @Select(
        """
        SELECT hidden FROM im_user_setting WHERE user_id=#{user} AND delete_state=0
        """
    )
    Boolean hidden(long user);

    // setting 对应的消息域查询
    @Insert(
        """
        INSERT INTO im_user_setting(user_id,hidden) VALUES(#{user},#{hidden}) ON DUPLICATE KEY UPDATE hidden=#{hidden}
        """
    )
    void setting(@Param("user") long user, @Param("hidden") boolean hidden);

    // insertImage 对应的消息域查询
    @Insert(
        """
        INSERT INTO message_image(uploader_id,file_name,thumb_name,width,height,bytes) VALUES(#{uploaderId},#{fileName},#{thumbName},#{width},#{height},#{bytes})
        """
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertImage(ImRows.Image image);

    // attach 对应的消息域查询
    @Update(
        """
        UPDATE message_image SET message_id=#{message},sort=#{sort} WHERE id=#{id} AND uploader_id=#{user} AND message_id IS NULL AND delete_state=0
        """
    )
    int attach(
        @Param("id") long id,
        @Param("user") long user,
        @Param("message") long message,
        @Param("sort") int sort
    );

    // image 对应的消息域查询
    @Select(
        """
        SELECT * FROM message_image WHERE id=#{id} AND delete_state=0
        """
    )
    ImRows.Image image(long id);

    // images 对应的消息域查询
    @Select(
        """
        <script>SELECT * FROM message_image WHERE delete_state=0 AND message_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach> ORDER BY sort,id</script>
        """
    )
    List<ImRows.Image> images(@Param("ids") List<Long> ids);

    // insertNotice 对应的消息域查询
    @Insert(
        """
               INSERT IGNORE INTO notification(receiver_id,channel,source,category,type,actor_id,post_id,comment_id,post_title,excerpt,original_excerpt,level,dedupe_key)
        VALUES(#{receiverId},#{channel},#{source},#{category},#{type},#{actorId},#{postId},#{commentId},#{postTitle},#{excerpt},#{originalExcerpt},#{level},#{dedupeKey})
               """
    )
    int insertNotice(ImRows.Notice notice);

    // noticeUnread 对应的消息域查询
    @Select(
        """
        SELECT COUNT(*) FROM notification WHERE receiver_id=#{user} AND channel=#{channel} AND read_state=0 AND delete_state=0
        """
    )
    long noticeUnread(
        @Param("user") long user,
        @Param("channel") Channel channel
    );

    // noticeCount 对应的消息域查询
    @Select(
        """
        <script>SELECT COUNT(*) FROM notification WHERE receiver_id=#{user} AND channel=#{channel} AND delete_state=0 <if test="source != null">AND source=#{source}</if> <if test="category != null">AND category=#{category}</if></script>
        """
    )
    long noticeCount(
        @Param("user") long user,
        @Param("channel") Channel channel,
        @Param("source") String source,
        @Param("category") String category
    );

    // notices 对应的消息域查询
    @Select(
        """
        <script>SELECT * FROM notification WHERE receiver_id=#{user} AND channel=#{channel} AND delete_state=0 <if test="source != null">AND source=#{source}</if> <if test="category != null">AND category=#{category}</if> ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>
        """
    )
    List<ImRows.Notice> notices(
        @Param("user") long user,
        @Param("channel") Channel channel,
        @Param("source") String source,
        @Param("category") String category,
        @Param("size") int size,
        @Param("offset") long offset
    );

    // likeCount 对应的消息域查询
    @Select(
        """
        <script>SELECT COUNT(*) FROM (SELECT 1 FROM notification WHERE receiver_id=#{user} AND channel=#{channel} AND delete_state=0 <if test="source != null">AND source=#{source}</if> <if test="category != null">AND category=#{category}</if> GROUP BY type,post_id,COALESCE(comment_id,0)) grouped</script>
        """
    )
    long likeCount(
        @Param("user") long user,
        @Param("channel") Channel channel,
        @Param("source") String source,
        @Param("category") String category
    );

    // likes 对应的消息域查询
    @Select(
        """
               <script>SELECT n.id,n.receiver_id,n.channel,n.source,n.category,n.type,n.actor_id,n.post_id,n.comment_id,n.post_title,n.excerpt,n.original_excerpt,n.level,n.dedupe_key,n.create_time,g.actor_count,g.all_read AS read_state FROM notification n JOIN
        (SELECT MAX(id) id,COUNT(*) actor_count,MIN(read_state) all_read FROM notification WHERE receiver_id=#{user} AND channel=#{channel} AND delete_state=0 <if test="source != null">AND source=#{source}</if> <if test="category != null">AND category=#{category}</if> GROUP BY type,post_id,COALESCE(comment_id,0)) g ON n.id=g.id ORDER BY n.id DESC LIMIT #{size} OFFSET #{offset}</script>
               """
    )
    List<ImRows.Notice> likes(
        @Param("user") long user,
        @Param("channel") Channel channel,
        @Param("source") String source,
        @Param("category") String category,
        @Param("size") int size,
        @Param("offset") long offset
    );

    // likeActors 对应的消息域查询
    @Select(
        """
               SELECT * FROM notification WHERE receiver_id=#{receiverId} AND type=#{type} AND post_id=#{postId}
        AND COALESCE(comment_id,0)=COALESCE(#{commentId},0) AND delete_state=0 ORDER BY id DESC LIMIT 2
               """
    )
    List<ImRows.Notice> likeActors(ImRows.Notice notice);

    // readNotices 对应的消息域查询
    @Update(
        """
        <script>UPDATE notification SET read_state=1 WHERE receiver_id=#{user} AND channel=#{channel} AND delete_state=0 <if test="source != null">AND source=#{source}</if> <if test="category != null">AND category=#{category}</if></script>
        """
    )
    void readNotices(
        @Param("user") long user,
        @Param("channel") Channel channel,
        @Param("source") String source,
        @Param("category") String category
    );
}
