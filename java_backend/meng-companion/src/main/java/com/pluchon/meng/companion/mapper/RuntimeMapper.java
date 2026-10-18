package com.pluchon.meng.companion.mapper;

import com.pluchon.meng.companion.entity.Rows;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 所有轮次归属于已经鉴权的会话；跨轮身份只来自本域持久化数据。
@Mapper
public interface RuntimeMapper {
    @Select(
        "SELECT content FROM companion_card WHERE character_code=#{code} AND active=1 AND delete_state=0 AND MOD(#{user},100)<rollout_percent ORDER BY id DESC LIMIT 1"
    )
    String card(@Param("code") String code, @Param("user") long user);

    @Select(
        "SELECT * FROM companion_line WHERE character_code=#{code} AND level=#{level} AND stage=#{stage} AND version=#{version} AND enabled=1 AND delete_state=0 ORDER BY id LIMIT 30"
    )
    List<Rows.Line> lines(
        @Param("code") String code,
        @Param("level") int level,
        @Param("stage") int stage,
        @Param("version") String version
    );

    @Select(
        "SELECT * FROM companion_message WHERE session_id=#{session} AND client_message_id=#{client}"
    )
    Rows.Message duplicate(
        @Param("session") long session,
        @Param("client") String client
    );

    @Select(
        "SELECT * FROM companion_message WHERE session_id=#{session} AND role='USER' AND turn_state='RECEIVED' AND delete_state=0 ORDER BY id LIMIT 10 FOR UPDATE"
    )
    List<Rows.Message> pending(long session);

    @Select(
        "SELECT * FROM companion_message WHERE session_id=#{session} AND turn_id=#{turn} AND role='CHARACTER' AND completed=1 AND delete_state=0 ORDER BY id"
    )
    List<Rows.Message> replies(
        @Param("session") long session,
        @Param("turn") long turn
    );

    @Select("SELECT * FROM companion_message WHERE id=#{id}")
    Rows.Message message(long id);

    @Update(
        "UPDATE companion_message SET turn_state=#{state},turn_id=#{turn} WHERE id=#{id} AND role='USER'"
    )
    void state(
        @Param("id") long id,
        @Param("turn") long turn,
        @Param("state") String state
    );

    @Update(
        "UPDATE companion_message SET turn_state=#{state} WHERE turn_id=#{turn} AND role='USER'"
    )
    void groupState(@Param("turn") long turn, @Param("state") String state);

    @Update(
        "UPDATE companion_session SET unread=#{unread} WHERE id=#{session} AND user_id=#{user} AND delete_state=0"
    )
    void unread(
        @Param("session") long session,
        @Param("user") long user,
        @Param("unread") boolean unread
    );

    @Update(
        "UPDATE companion_bond SET level=#{level},level_changed=1 WHERE user_id=#{user} AND character_code=#{code}"
    )
    void level(
        @Param("user") long user,
        @Param("code") String code,
        @Param("level") int level
    );

    @Update(
        "UPDATE companion_bond SET time_zone=#{zone} WHERE user_id=#{user} AND character_code=#{code}"
    )
    void zone(
        @Param("user") long user,
        @Param("code") String code,
        @Param("zone") String zone
    );

    @Update(
        "UPDATE companion_bond SET level_changed=0 WHERE user_id=#{user} AND character_code=#{code} AND level=#{level}"
    )
    void levelConsumed(
        @Param("user") long user,
        @Param("code") String code,
        @Param("level") int level
    );

    @Select(
        "SELECT content FROM companion_message WHERE session_id=#{session} AND role='CHARACTER' AND completed=1 AND delete_state=0 ORDER BY id DESC LIMIT 30"
    )
    List<String> recentReplies(long session);

    @Select(
        "SELECT feeling FROM companion_message WHERE session_id=#{session} AND role='CHARACTER' AND feeling IS NOT NULL AND create_time>DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 HOUR) AND delete_state=0 ORDER BY id DESC LIMIT 1"
    )
    String feeling(long session);

    @Select(
        "SELECT DISTINCT session_id FROM companion_message WHERE role='USER' AND turn_state='RECEIVED' AND delete_state=0 LIMIT 100"
    )
    List<Long> waiting();

    @Update(
        "UPDATE companion_message SET turn_state='FAILED' WHERE role='USER' AND turn_state IN ('CONTEXT_READY','GENERATING','CHECKED') AND update_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 MINUTE)"
    )
    void expireTurns();

    @Select(
        "SELECT * FROM companion_message WHERE session_id=#{session} AND id>#{after} AND completed=1 AND delete_state=0 ORDER BY id LIMIT 100"
    )
    List<Rows.Message> window(
        @Param("session") long session,
        @Param("after") long after
    );

    @Update(
        "UPDATE companion_message SET turn_state='RECEIVED',turn_id=id,request_revision=#{revision},time_zone=#{zone} WHERE id=#{id} AND turn_state='FAILED'"
    )
    void retry(
        @Param("id") long id,
        @Param("revision") long revision,
        @Param("zone") String zone
    );

    @Select("SELECT id FROM companion_outbox WHERE event_id=#{event}")
    List<Map<String, Object>> outboxRows(@Param("event") String event);

    @Select(
        "SELECT event_id,payload FROM companion_outbox WHERE id>#{after} AND id<#{before} AND event_type='turn.committed' ORDER BY id"
    )
    @Results(
        @Result(
            column = "payload",
            property = "payload",
            javaType = String.class
        )
    )
    List<Map<String, Object>> followingTurnEvents(
        @Param("after") long after,
        @Param("before") long before
    );

    @Select(
        "SELECT * FROM companion_message WHERE turn_id=#{turn} AND session_id=#{session} AND completed=1 AND delete_state=0 AND work_id IS NULL ORDER BY id"
    )
    List<Rows.Message> turnMessages(
        @Param("turn") long turn,
        @Param("session") long session
    );

    @Update(
        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{user} AND character_code=#{code} AND invalid_at IS NULL"
    )
    int invalidateMemory(
        @Param("id") long id,
        @Param("user") long user,
        @Param("code") String code
    );

    @Update(
        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),superseded_by=#{replacement} WHERE id=#{id} AND user_id=#{user} AND character_code=#{code} AND invalid_at IS NULL"
    )
    int supersedeMemory(
        @Param("replacement") long replacement,
        @Param("id") long id,
        @Param("user") long user,
        @Param("code") String code
    );

    @Update("UPDATE companion_bond SET profile='' WHERE id=#{bond}")
    int clearProfileById(@Param("bond") long bond);

    @Update("UPDATE companion_bond SET profile=#{profile} WHERE id=#{bond}")
    int writeProfile(
        @Param("profile") String profile,
        @Param("bond") long bond
    );

    @Update(
        "UPDATE companion_bond SET profile_revision=profile_revision+#{delta} WHERE id=#{bond}"
    )
    int advanceProfileRevision(
        @Param("delta") int delta,
        @Param("bond") long bond
    );

    @Update(
        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),embedding=NULL WHERE id=#{id}"
    )
    int evictMemory(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM companion_processed WHERE event_id=#{event}")
    Integer processedEventCount(@Param("event") String event);

    @Insert("INSERT IGNORE INTO companion_processed(event_id) VALUES(#{event})")
    int markProcessed(@Param("event") String event);

    @Select("SELECT * FROM companion_drawing WHERE message_id=#{message}")
    @Results(
        id = "drawingPayload",
        value = @Result(
            column = "reservation",
            property = "reservation",
            javaType = String.class
        )
    )
    List<Map<String, Object>> drawingJobs(@Param("message") long message);

    @Update(
        "UPDATE companion_drawing SET status='RUNNING' WHERE message_id=#{message} AND status='PENDING'"
    )
    int startDrawing(@Param("message") long message);

    @Update(
        "UPDATE companion_drawing SET status='COMPLETED',work_id=#{work} WHERE message_id=#{message}"
    )
    int completeDrawing(
        @Param("work") long work,
        @Param("message") long message
    );

    @Update(
        "UPDATE companion_drawing SET status='FAILED' WHERE message_id=#{message}"
    )
    int failDrawing(@Param("message") long message);

    @Select(
        "SELECT * FROM companion_memory WHERE embedding IS NULL AND invalid_at IS NULL AND delete_state=0 ORDER BY id LIMIT 32"
    )
    List<Rows.Memory> missingMemoryVectors();

    @Update(
        "UPDATE companion_memory SET embedding=#{vector} WHERE id=#{id} AND content_hash=#{hash} AND invalid_at IS NULL AND delete_state=0"
    )
    int writeMemoryVector(
        @Param("vector") String vector,
        @Param("id") long id,
        @Param("hash") String hash
    );

    @Select(
        "SELECT s.* FROM companion_session s WHERE s.delete_state=0 AND s.last_message_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE) AND EXISTS(SELECT 1 FROM companion_message m WHERE m.session_id=s.id AND m.id>s.episode_until_message_id AND m.role='USER' AND m.completed=1 AND m.delete_state=0) LIMIT 20"
    )
    List<Map<String, Object>> idleSessions();

    @Insert(
        "INSERT IGNORE INTO companion_episode(user_id,character_code,session_id,start_time,end_time,summary,emotion,open_threads,until_message_id) VALUES(#{user},#{code},#{session},#{start},#{end},#{summary},#{emotion},#{openThreads},#{until})"
    )
    int insertEpisode(
        @Param("user") long user,
        @Param("code") String code,
        @Param("session") long session,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("summary") String summary,
        @Param("emotion") String emotion,
        @Param("openThreads") String openThreads,
        @Param("until") long until
    );

    @Update(
        "UPDATE companion_session SET episode_until_message_id=#{until} WHERE id=#{session} AND episode_until_message_id=#{previous}"
    )
    int advanceEpisodeWindow(
        @Param("until") long until,
        @Param("session") long session,
        @Param("previous") long previous
    );

    @Update(
        "UPDATE companion_episode SET heat=heat*0.98,open_threads=IF(heat<0.1,'',open_threads) WHERE delete_state=0"
    )
    int decayEpisodes();

    @Update(
        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),embedding=NULL WHERE kind='PROMISE' AND done=0 AND due_date<DATE_SUB(UTC_DATE(),INTERVAL 7 DAY) AND invalid_at IS NULL"
    )
    int expirePromises();

    @Update(
        "UPDATE companion_message SET content='',image_ids=NULL,bubbles=NULL,delete_state=1 WHERE create_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 YEAR) AND work_id IS NULL"
    )
    int purgeExpiredMessages();

    @Select(
        "SELECT message_id,reservation FROM companion_drawing WHERE status='RUNNING' AND update_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 5 MINUTE)"
    )
    @ResultMap("drawingPayload")
    List<Map<String, Object>> staleDrawings();

    @Update(
        "UPDATE companion_drawing SET status='FAILED' WHERE message_id=#{message} AND status='RUNNING'"
    )
    int failStaleDrawing(@Param("message") long message);

    @Select(
        "SELECT DISTINCT s.id,s.user_id,s.character_code FROM companion_session s JOIN companion_message m ON m.session_id=s.id WHERE s.delete_state=0 AND m.delete_state=0 AND m.completed=1 AND m.id>s.summary_until_message_id AND m.create_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 YEAR) LIMIT 20"
    )
    List<Map<String, Object>> expiredSessions();

    @Select(
        "SELECT * FROM companion_message WHERE session_id=#{session} AND id>#{after} AND completed=1 AND delete_state=0 AND create_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 YEAR) ORDER BY id LIMIT 80"
    )
    List<Rows.Message> expiredHistory(
        @Param("session") long session,
        @Param("after") long after
    );

    @Select(
        "SELECT m.* FROM companion_memory m WHERE m.kind='PROMISE' AND m.notified=0 AND m.done=0 AND m.invalid_at IS NULL AND m.delete_state=0"
    )
    List<Rows.Memory> pendingPromises();

    @Update(
        "UPDATE companion_memory SET notified=1 WHERE id=#{id} AND notified=0 AND invalid_at IS NULL AND delete_state=0"
    )
    int notifyPromise(@Param("id") long id);

    @Select(
        "SELECT id,summary,heat FROM companion_episode WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0 ORDER BY heat DESC,end_time DESC LIMIT 3"
    )
    List<Map<String, Object>> recallEpisodes(
        @Param("user") long user,
        @Param("code") String code
    );

    @Update(
        "UPDATE companion_episode SET heat=heat+1 WHERE id=#{id} AND user_id=#{user} AND character_code=#{code}"
    )
    int touchEpisode(
        @Param("id") long id,
        @Param("user") long user,
        @Param("code") String code
    );

    @Select(
        "SELECT m.id,m.content FROM companion_message m JOIN companion_session s ON s.id=m.session_id WHERE s.user_id=#{user} AND s.character_code=#{code} AND s.delete_state=0 AND m.delete_state=0 AND m.completed=1 AND m.work_id IS NULL AND m.kind='TEXT' ORDER BY m.id DESC LIMIT 100"
    )
    List<Map<String, Object>> recallMessages(
        @Param("user") long user,
        @Param("code") String code
    );

    @Insert(
        "INSERT IGNORE INTO companion_daily(character_code,date,content) VALUES(#{code},#{date},#{content})"
    )
    int insertDaily(
        @Param("code") String code,
        @Param("date") LocalDate date,
        @Param("content") String content
    );

    @Select(
        "SELECT content FROM companion_daily WHERE character_code=#{code} AND date=#{date}"
    )
    String daily(@Param("code") String code, @Param("date") LocalDate date);

    @Select(
        "SELECT content FROM companion_daily WHERE character_code=#{code} AND date<#{date} AND delete_state=0 ORDER BY date DESC LIMIT 7"
    )
    List<String> recentDailies(
        @Param("code") String code,
        @Param("date") LocalDate date
    );

    @Select(
        "SELECT user_id FROM companion_session WHERE id=#{session} AND delete_state=0"
    )
    List<Map<String, Object>> sessionOwnerRows(@Param("session") long session);

    @Update(
        "UPDATE companion_message SET kind='STICKER',sticker=#{sticker} WHERE id=#{id}"
    )
    int sticker(@Param("sticker") String sticker, @Param("id") long id);

    @Insert(
        "INSERT IGNORE INTO companion_outbox(event_id,event_type,payload) VALUES(#{event},#{type},#{payload})"
    )
    int insertOutbox(
        @Param("event") String event,
        @Param("type") String type,
        @Param("payload") String payload
    );

    @Select(
        "SELECT * FROM companion_outbox WHERE status='PENDING' AND delete_state=0 ORDER BY id LIMIT 100"
    )
    @Results(
        @Result(
            column = "payload",
            property = "payload",
            javaType = String.class
        )
    )
    List<Map<String, Object>> pendingOutbox();

    @Update("UPDATE companion_outbox SET status='SENT' WHERE id=#{id}")
    int sentOutbox(@Param("id") long id);

    @Update(
        "UPDATE companion_outbox SET retry_count=retry_count+1 WHERE id=#{id}"
    )
    int retryOutbox(@Param("id") long id);

    @Insert(
        "INSERT INTO companion_drawing(user_id,session_id,message_id,instruction,reservation,revision) VALUES(#{user},#{session},#{message},#{instruction},#{reservation},#{revision})"
    )
    int insertDrawing(
        @Param("user") long user,
        @Param("session") long session,
        @Param("message") long message,
        @Param("instruction") String instruction,
        @Param("reservation") String reservation,
        @Param("revision") long revision
    );

    @Update(
        "UPDATE companion_memory SET last_used_time=UTC_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{user} AND character_code=#{code} AND invalid_at IS NULL AND delete_state=0"
    )
    int touchMemory(
        @Param("id") long id,
        @Param("user") long user,
        @Param("code") String code
    );

    @Insert(
        "INSERT IGNORE INTO companion_feedback(user_id,message_id,kind,rejected_content) VALUES(#{user},#{message},#{kind},#{rejected})"
    )
    int insertFeedback(
        @Param("user") long user,
        @Param("message") long message,
        @Param("kind") String kind,
        @Param("rejected") String rejected
    );

    @Select(
        "SELECT COUNT(*) FROM companion_feedback f JOIN companion_message m ON m.id=f.message_id WHERE f.user_id=#{user} AND f.kind='REGENERATE' AND m.turn_id=#{turn}"
    )
    Integer regenerationCount(
        @Param("user") long user,
        @Param("turn") long turn
    );

    @Select("SELECT turn_id FROM companion_message WHERE id=#{message}")
    Long rejectedTurn(@Param("message") long message);

    @Update(
        "UPDATE companion_message SET delete_state=1 WHERE turn_id=#{turn} AND role='CHARACTER' AND work_id IS NULL"
    )
    int hideCharacterTurn(@Param("turn") long turn);

    @Update(
        "UPDATE companion_message SET delete_state=1 WHERE turn_id=#{turn} AND role='USER'"
    )
    int hideUserTurn(@Param("turn") long turn);

    @Update(
        "UPDATE companion_message SET regenerates_message_id=#{rejected} WHERE turn_id=#{turn} AND role='CHARACTER'"
    )
    int markRegeneration(
        @Param("rejected") long rejected,
        @Param("turn") long turn
    );

    @Update(
        "UPDATE companion_feedback SET replacement_message_id=#{replacement} WHERE user_id=#{user} AND message_id=#{rejected} AND kind='REGENERATE'"
    )
    int feedbackReplacement(
        @Param("replacement") long replacement,
        @Param("user") long user,
        @Param("rejected") long rejected
    );

    @Update(
        "UPDATE companion_message SET memory_ids=#{ids} WHERE turn_id=#{turn} AND role='CHARACTER'"
    )
    int messageMemories(@Param("ids") String ids, @Param("turn") long turn);

    @Update(
        "UPDATE companion_memory SET mentioned_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{user} AND character_code=#{code} AND invalid_at IS NULL AND delete_state=0"
    )
    int mentionMemory(
        @Param("id") long id,
        @Param("user") long user,
        @Param("code") String code
    );

    @Update(
        "UPDATE companion_message SET turn_state='FAILED' WHERE session_id=#{session} AND turn_state IN ('RECEIVED','CONTEXT_READY','GENERATING','CHECKED')"
    )
    int failSessionTurns(@Param("session") long session);

    @Update(
        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),embedding=NULL WHERE user_id=#{user} AND source_session_id=#{session} AND invalid_at IS NULL"
    )
    int forgetSessionMemories(
        @Param("user") long user,
        @Param("session") long session
    );

    @Update(
        "UPDATE companion_bond SET profile='',profile_revision=profile_revision+1 WHERE user_id=#{user} AND character_code=#{code}"
    )
    int clearProfile(@Param("user") long user, @Param("code") String code);

    @Update(
        "UPDATE companion_episode SET delete_state=1,embedding=NULL WHERE user_id=#{user} AND character_code=#{code}"
    )
    int clearEpisodes(@Param("user") long user, @Param("code") String code);

    @Update(
        "UPDATE companion_memory SET content=#{content},content_hash=#{hash},embedding=NULL,keywords=NULL WHERE id=#{id} AND user_id=#{user} AND invalid_at IS NULL"
    )
    int editMemory(
        @Param("content") String content,
        @Param("hash") String hash,
        @Param("id") long id,
        @Param("user") long user
    );

    @Update(
        "UPDATE companion_memory SET pinned=#{pinned} WHERE id=#{id} AND user_id=#{user} AND invalid_at IS NULL"
    )
    int pinMemory(
        @Param("pinned") boolean pinned,
        @Param("id") long id,
        @Param("user") long user
    );

    @Select("SELECT COUNT(*) FROM companion_outbox WHERE status='PENDING'")
    double pendingOutboxCount();

    @Select(
        "SELECT COUNT(*) FROM companion_feedback WHERE create_time>=UTC_DATE()"
    )
    double todayFeedbackCount();

    @Select(
        "SELECT code FROM companion_character WHERE code=#{code} AND delete_state=0 FOR UPDATE"
    )
    String lockCharacter(String code);

    @Select(
        "SELECT content FROM companion_card WHERE character_code=#{code} AND version=#{version}"
    )
    String catalogCard(
        @Param("code") String code,
        @Param("version") String version
    );

    @Select(
        "SELECT * FROM companion_line WHERE character_code=#{code} AND version=#{version} ORDER BY id"
    )
    List<Rows.Line> catalogLines(
        @Param("code") String code,
        @Param("version") String version
    );

    @Insert(
        "INSERT INTO companion_card(character_code,version,content,active,rollout_percent) VALUES(#{code},#{version},#{content},FALSE,#{rollout})"
    )
    int insertCatalogCard(
        @Param("code") String code,
        @Param("version") String version,
        @Param("content") String content,
        @Param("rollout") int rollout
    );

    @Insert(
        "INSERT INTO companion_line(character_code,level,stage,scene,dialogue,version) VALUES(#{code},#{level},#{stage},#{scene},#{dialogue},#{version})"
    )
    int insertCatalogLine(
        @Param("code") String code,
        @Param("level") int level,
        @Param("stage") int stage,
        @Param("scene") String scene,
        @Param("dialogue") String dialogue,
        @Param("version") String version
    );

    @Update(
        "UPDATE companion_card SET active=TRUE WHERE character_code=#{code} AND version=#{version}"
    )
    int activateCatalog(
        @Param("code") String code,
        @Param("version") String version
    );

    @Update(
        "UPDATE companion_card SET active=FALSE WHERE character_code=#{code} AND id < (SELECT current_id FROM (SELECT MAX(id) AS current_id FROM companion_card WHERE character_code=#{code} AND version=#{version} AND active=TRUE AND rollout_percent=100 AND delete_state=0) current_card)"
    )
    int retirePreviousCatalog(
        @Param("code") String code,
        @Param("version") String version
    );
}
