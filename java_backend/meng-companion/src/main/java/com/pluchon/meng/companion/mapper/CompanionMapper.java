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
import org.apache.ibatis.annotations.Update;

// 所有查询限定当前用户，行锁保护关系变更、重置与会话数量
@Mapper
public interface CompanionMapper {
    @Select("SELECT * FROM companion_character WHERE enabled=1 AND delete_state=0 ORDER BY sort")
    List<Rows.Character> characters();

    @Insert("INSERT IGNORE INTO companion_bond(user_id,character_code) VALUES(#{user},#{code})")
    void ensureBond(@Param("user") long user, @Param("code") String code);

    @Select("SELECT * FROM companion_bond WHERE user_id=#{user} AND character_code=#{code} FOR UPDATE")
    Rows.Bond lockBond(@Param("user") long user, @Param("code") String code);

    @Select("SELECT * FROM companion_bond WHERE user_id=#{user} AND character_code=#{code}")
    Rows.Bond bond(@Param("user") long user, @Param("code") String code);

    @Update("""
        UPDATE companion_bond SET affection=#{affection},stage=#{stage},first_chat_time=#{firstChatTime},
        last_chat_time=#{lastChatTime},chat_days=#{chatDays},today_gain=#{todayGain},today_date=#{todayDate}
        WHERE id=#{id} AND revision=#{revision}
        """)
    int updateBond(Rows.Bond bond);

    @Update("""
        UPDATE companion_bond SET affection=0,stage=1,revision=revision+1,first_chat_time=NULL,
        last_chat_time=NULL,chat_days=0 WHERE id=#{id}
        """)
    void resetBond(long id);

    @Select("SELECT COUNT(*) FROM companion_session WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0")
    long sessionCount(@Param("user") long user, @Param("code") String code);

    @Select("""
        SELECT * FROM companion_session WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0
        ORDER BY COALESCE(last_message_time,create_time) DESC,id DESC LIMIT #{offset},#{size}
        """)
    List<Rows.Session> sessions(@Param("user") long user, @Param("code") String code,
                               @Param("offset") long offset, @Param("size") int size);

    @Insert("INSERT INTO companion_session(user_id,character_code,summary) VALUES(#{userId},#{characterCode},'')")
    @Options(useGeneratedKeys=true, keyProperty="id")
    void insertSession(Rows.Session session);

    @Select("SELECT * FROM companion_session WHERE id=#{id} AND user_id=#{user} AND delete_state=0")
    Rows.Session session(@Param("id") long id, @Param("user") long user);

    @Select("SELECT * FROM companion_session WHERE id=#{id} AND user_id=#{user} AND delete_state=0 FOR UPDATE")
    Rows.Session lockSession(@Param("id") long id, @Param("user") long user);

    @Update("UPDATE companion_session SET delete_state=1 WHERE id=#{id} AND user_id=#{user}")
    void deleteSession(@Param("id") long id, @Param("user") long user);

    @Update("""
        UPDATE companion_session SET summary='',summary_until_message_id=COALESCE(
        (SELECT MAX(id) FROM companion_message WHERE session_id=companion_session.id),0)
        WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0
        """)
    void resetSummaries(@Param("user") long user, @Param("code") String code);

    @Update("UPDATE companion_session SET title=IF(title='',#{title},title),message_count=message_count+#{count},last_message_time=#{time} WHERE id=#{id} AND delete_state=0")
    void advanceSession(@Param("id") long id, @Param("title") String title, @Param("count") int count,
                        @Param("time") LocalDateTime time);

    @Select("""
        SELECT * FROM companion_message WHERE session_id=#{session} AND delete_state=0
        AND (#{before} IS NULL OR id < #{before}) ORDER BY id DESC LIMIT #{size}
        """)
    List<Rows.Message> messages(@Param("session") long session, @Param("before") Long before, @Param("size") int size);

    @Select("""
        SELECT * FROM companion_message WHERE session_id=#{session} AND id > #{after}
        AND delete_state=0 AND completed=1 ORDER BY id DESC LIMIT 200
        """)
    List<Rows.Message> history(@Param("session") long session, @Param("after") long after);

    @Select("""
        SELECT id FROM companion_message WHERE session_id=#{session} AND id > #{after}
        AND role='USER' AND completed=1 AND delete_state=0 ORDER BY id DESC LIMIT 19,1
        """)
    Long recentBoundary(@Param("session") long session, @Param("after") long after);

    @Select("""
        SELECT * FROM companion_message WHERE session_id=#{session} AND id > #{after} AND id < #{before}
        AND completed=1 AND delete_state=0 ORDER BY id LIMIT 80
        """)
    List<Rows.Message> summaryBatch(@Param("session") long session, @Param("after") long after, @Param("before") long before);

    @Insert("""
        INSERT INTO companion_message(session_id,role,content,bubbles,image_ids,work_id,affection_delta,safety_label,completed)
        VALUES(#{sessionId},#{role},#{content},#{bubbles},#{imageIds},#{workId},#{affectionDelta},#{safetyLabel},#{completed})
        """)
    @Options(useGeneratedKeys=true, keyProperty="id")
    void insertMessage(Rows.Message message);

    @Update("UPDATE companion_message SET completed=1,safety_label=#{label} WHERE id=#{id}")
    void completeMessage(@Param("id") long id, @Param("label") String label);

    @Select("SELECT * FROM companion_memory WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0 ORDER BY last_used_time DESC,id DESC")
    List<Rows.Memory> memories(@Param("user") long user, @Param("code") String code);

    @Insert("""
        INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id)
        VALUES(#{userId},#{characterCode},#{content},#{contentHash},#{sourceMessageId})
        ON DUPLICATE KEY UPDATE delete_state=0,last_used_time=CURRENT_TIMESTAMP(6)
        """)
    void insertMemory(Rows.Memory memory);

    @Update("UPDATE companion_memory SET last_used_time=CURRENT_TIMESTAMP(6) WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0")
    void touchMemories(@Param("user") long user, @Param("code") String code);

    @Update("UPDATE companion_memory SET delete_state=1 WHERE id=#{id} AND user_id=#{user}")
    int deleteMemory(@Param("id") long id, @Param("user") long user);

    @Select("SELECT character_code FROM companion_memory WHERE id=#{id} AND user_id=#{user} AND delete_state=0")
    String memoryCode(@Param("id") long id, @Param("user") long user);

    @Update("UPDATE companion_memory SET delete_state=1 WHERE user_id=#{user} AND character_code=#{code}")
    void clearMemories(@Param("user") long user, @Param("code") String code);

    @Update("UPDATE companion_bond SET revision=revision+1 WHERE user_id=#{user} AND character_code=#{code}")
    void invalidate(@Param("user") long user, @Param("code") String code);

    @Insert("INSERT INTO companion_work(user_id,character_code,session_id,kind,title,body,file_path) VALUES(#{userId},#{characterCode},#{sessionId},#{kind},#{title},#{body},#{filePath})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    void insertWork(Rows.Work work);

    @Select("""
        <script>SELECT * FROM companion_work WHERE user_id=#{user} AND delete_state=0
        <if test="code != null">AND character_code=#{code}</if> ORDER BY id DESC LIMIT #{offset},#{size}</script>
        """)
    List<Rows.Work> works(@Param("user") long user, @Param("code") String code, @Param("offset") long offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM companion_work WHERE user_id=#{user} AND delete_state=0 <if test='code != null'>AND character_code=#{code}</if></script>")
    long workCount(@Param("user") long user, @Param("code") String code);

    @Select("SELECT * FROM companion_work WHERE id=#{id} AND user_id=#{user} AND delete_state=0")
    Rows.Work work(@Param("id") long id, @Param("user") long user);

    @Update("UPDATE companion_work SET delete_state=1 WHERE id=#{id} AND user_id=#{user}")
    void deleteWork(@Param("id") long id, @Param("user") long user);

    @Insert("INSERT INTO companion_safety_event(user_id,character_code,message_id,kind) VALUES(#{user},#{code},#{message},#{kind})")
    void safety(@Param("user") long user, @Param("code") String code, @Param("message") long message, @Param("kind") String kind);

    @Select("SELECT COUNT(*) FROM companion_safety_event WHERE user_id=#{user} AND kind IN ('SEXUAL','JAILBREAK') AND create_time >= #{start} AND create_time < #{end}")
    long blocks(@Param("user") long user, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Update("UPDATE companion_session SET summary=#{summary},summary_until_message_id=#{until} WHERE id=#{id} AND summary_until_message_id=#{previous} AND delete_state=0")
    void summary(@Param("id") long id, @Param("summary") String summary, @Param("until") long until, @Param("previous") long previous);

    @Update("UPDATE companion_message SET work_id=#{work} WHERE id=#{id}")
    void attachWork(@Param("id") long id, @Param("work") long work);

    @Insert("INSERT INTO companion_image(user_id,file_path) VALUES(#{user},#{path})")
    void insertImage(@Param("user") long user, @Param("path") String path);

    @Select("SELECT id FROM companion_image WHERE file_path=#{path} AND user_id=#{user} AND delete_state=0")
    Long imageId(@Param("user") long user, @Param("path") String path);

    @Select("SELECT file_path FROM companion_image WHERE id=#{id} AND user_id=#{user} AND delete_state=0")
    String image(@Param("id") long id, @Param("user") long user);
}
