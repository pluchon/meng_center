package com.pluchon.meng.companion.mapper;

import com.pluchon.meng.companion.entity.Rows;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

// 所有查询限定当前用户，行锁保护关系变更、重置与会话数量
@Mapper
public interface CompanionMapper {
    @Insert(
        "INSERT IGNORE INTO companion_bond(user_id,character_code) VALUES(#{user},#{code})"
    )
    void ensureBond(@Param("user") long user, @Param("code") String code);

    @Select(
        "SELECT * FROM companion_bond WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0 FOR UPDATE"
    )
    Rows.Bond lockBond(@Param("user") long user, @Param("code") String code);

    @Select(
        "SELECT * FROM companion_bond WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0"
    )
    Rows.Bond bond(@Param("user") long user, @Param("code") String code);

    @Select(
        "SELECT COUNT(*) FROM companion_session WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0"
    )
    long sessionCount(@Param("user") long user, @Param("code") String code);

    @Select(
        """
        SELECT s.*,(EXISTS(SELECT 1 FROM companion_message m WHERE m.session_id=s.id AND m.turn_state IN ('RECEIVED','CONTEXT_READY','GENERATING','CHECKED'))) generating FROM companion_session s WHERE user_id=#{user} AND character_code=#{code} AND s.delete_state=0
        ORDER BY COALESCE(last_message_time,create_time) DESC,id DESC LIMIT #{offset},#{size}
        """
    )
    List<Rows.Session> sessions(
        @Param("user") long user,
        @Param("code") String code,
        @Param("offset") long offset,
        @Param("size") int size
    );

    @Insert(
        "INSERT INTO companion_session(user_id,character_code,summary) VALUES(#{userId},#{characterCode},'')"
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertSession(Rows.Session session);

    @Select(
        "SELECT * FROM companion_session WHERE id=#{id} AND user_id=#{user} AND delete_state=0"
    )
    Rows.Session session(@Param("id") long id, @Param("user") long user);

    @Select(
        "SELECT * FROM companion_session WHERE id=#{id} AND user_id=#{user} AND delete_state=0 FOR UPDATE"
    )
    Rows.Session lockSession(@Param("id") long id, @Param("user") long user);

    @Update(
        "UPDATE companion_session SET delete_state=1 WHERE id=#{id} AND user_id=#{user}"
    )
    void deleteSession(@Param("id") long id, @Param("user") long user);

    @Update(
        """
        UPDATE companion_session SET context_snapshot=NULL,snapshot_time=NULL,summary='',window_start_message_id=COALESCE((SELECT MAX(m.id) FROM companion_message m WHERE m.session_id=companion_session.id),0),summary_until_message_id=COALESCE(
        (SELECT MAX(id) FROM companion_message WHERE session_id=companion_session.id),0)
        WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0
        """
    )
    void resetSummaries(@Param("user") long user, @Param("code") String code);

    @Update(
        "UPDATE companion_session SET title=CASE WHEN title='' THEN #{title} ELSE title END,message_count=message_count+#{count},last_message_time=#{time} WHERE id=#{id} AND delete_state=0"
    )
    void advanceSession(
        @Param("id") long id,
        @Param("title") String title,
        @Param("count") int count,
        @Param("time") LocalDateTime time
    );

    @Select(
        """
        SELECT * FROM companion_message WHERE session_id=#{session} AND delete_state=0
        AND (#{before} IS NULL OR id < #{before}) ORDER BY id DESC LIMIT #{size}
        """
    )
    List<Rows.Message> messages(
        @Param("session") long session,
        @Param("before") Long before,
        @Param("size") int size
    );

    @Select(
        """
        SELECT * FROM companion_message WHERE session_id=#{session} AND id > #{after}
        AND delete_state=0 AND completed=1 AND (turn_id IS NULL OR role='USER' OR EXISTS(SELECT 1 FROM companion_message u WHERE u.id=companion_message.turn_id AND u.turn_state='COMMITTED')) ORDER BY id DESC
        """
    )
    List<Rows.Message> history(
        @Param("session") long session,
        @Param("after") long after
    );

    @Insert(
        """
        INSERT INTO companion_message(session_id,role,content,bubbles,image_ids,safety_label,completed,
        client_message_id,turn_id,turn_state,kind,sticker,prompt_version,model,memory_ids,metrics,request_revision,time_zone)
        VALUES(#{sessionId},#{role},#{content},#{bubbles},#{imageIds},#{safetyLabel},#{completed},
        #{clientMessageId},#{turnId},#{turnState},#{kind},#{sticker},#{promptVersion},#{model},#{memoryIds},#{metrics},#{requestRevision},#{timeZone})
        """
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMessage(Rows.Message message);

    @Update(
        "UPDATE companion_message SET completed=1,turn_state='COMMITTED',safety_label=#{label} WHERE id=#{id}"
    )
    void completeMessage(@Param("id") long id, @Param("label") String label);

    @Select(
        "SELECT * FROM companion_memory WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0 AND invalid_at IS NULL ORDER BY pinned DESC,importance DESC,last_used_time DESC,id DESC"
    )
    List<Rows.Memory> memories(
        @Param("user") long user,
        @Param("code") String code
    );

    @Insert(
        """
        INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id,source_session_id,
        kind,keywords,importance,embedding,embedding_version,pinned)
        VALUES(#{userId},#{characterCode},#{content},#{contentHash},#{sourceMessageId},#{sourceSessionId},
        #{kind},#{keywords},#{importance},#{embedding},#{embeddingVersion},#{pinned})
        ON DUPLICATE KEY UPDATE last_used_time=CURRENT_TIMESTAMP(6)
        """
    )
    @SelectKey(
        statement = "SELECT id FROM companion_memory WHERE user_id=#{userId} AND character_code=#{characterCode} AND active_content_hash=#{contentHash}",
        keyProperty = "id",
        before = false,
        resultType = Long.class
    )
    void insertMemory(Rows.Memory memory);

    @Update(
        "UPDATE companion_memory SET delete_state=1,embedding=NULL,keywords=NULL,invalid_at=UTC_TIMESTAMP(6) WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0"
    )
    void clearMemories(@Param("user") long user, @Param("code") String code);

    @Update(
        "UPDATE companion_bond SET revision=revision+1 WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0"
    )
    void invalidate(@Param("user") long user, @Param("code") String code);

    @Insert(
        "INSERT INTO companion_safety_event(user_id,character_code,message_id,kind) VALUES(#{user},#{code},#{message},#{kind})"
    )
    void safety(
        @Param("user") long user,
        @Param("code") String code,
        @Param("message") long message,
        @Param("kind") String kind
    );

    @Select(
        "SELECT COUNT(*) FROM companion_safety_event WHERE user_id=#{user} AND kind IN ('SEXUAL','JAILBREAK') AND create_time >= #{start} AND create_time < #{end}"
    )
    long blocks(
        @Param("user") long user,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Update(
        "UPDATE companion_session SET summary=#{summary},summary_until_message_id=#{until},window_start_message_id=#{until} WHERE id=#{id} AND summary_until_message_id=#{previous} AND delete_state=0"
    )
    int summary(
        @Param("id") long id,
        @Param("summary") String summary,
        @Param("until") long until,
        @Param("previous") long previous
    );

    @Insert(
        "INSERT INTO companion_image(user_id,file_path) VALUES(#{user},#{path})"
    )
    void insertImage(@Param("user") long user, @Param("path") String path);

    @Select(
        "SELECT id FROM companion_image WHERE file_path=#{path} AND user_id=#{user} AND delete_state=0"
    )
    Long imageId(@Param("user") long user, @Param("path") String path);

    @Select(
        "SELECT file_path FROM companion_image WHERE id=#{id} AND user_id=#{user} AND delete_state=0"
    )
    String image(@Param("id") long id, @Param("user") long user);

    @Select(
        """
        SELECT i.file_path FROM companion_image i WHERE i.user_id=#{user} AND i.delete_state=0
        AND i.create_time < #{cutoff} AND NOT EXISTS(
          SELECT 1 FROM companion_message m JOIN companion_session s ON s.id=m.session_id
          WHERE s.user_id=i.user_id AND JSON_CONTAINS(m.image_ids,JSON_ARRAY(i.id)))
        """
    )
    List<String> expiredPendingImages(
        @Param("user") long user,
        @Param("cutoff") LocalDateTime cutoff
    );

    @Update(
        "UPDATE companion_image SET delete_state=1 WHERE user_id=#{user} AND file_path=#{path} AND delete_state=0"
    )
    void expireImage(@Param("user") long user, @Param("path") String path);

    @Select(
        """
        SELECT COUNT(*) FROM companion_image i WHERE i.user_id=#{user} AND i.delete_state=0
        AND NOT EXISTS(SELECT 1 FROM companion_message m JOIN companion_session s ON s.id=m.session_id
          WHERE s.user_id=i.user_id AND JSON_CONTAINS(m.image_ids,JSON_ARRAY(i.id)))
        """
    )
    long pendingImageCount(@Param("user") long user);
}
