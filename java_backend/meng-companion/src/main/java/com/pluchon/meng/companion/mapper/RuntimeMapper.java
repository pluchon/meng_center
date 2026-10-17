package com.pluchon.meng.companion.mapper;

import com.pluchon.meng.companion.entity.Rows;
import java.util.List;
import org.apache.ibatis.annotations.*;

// 所有轮次归属于已经鉴权的会话；跨轮身份只来自本域持久化数据。
@Mapper
public interface RuntimeMapper {
    @Select(
        "SELECT content FROM companion_card WHERE character_code=#{code} AND active=1 AND delete_state=0 AND MOD(#{user},100)<rollout_percent ORDER BY id DESC LIMIT 1"
    )
    String card(@Param("code") String code, @Param("user") long user);

    @Select(
        "SELECT dialogue FROM companion_line WHERE character_code=#{code} AND level=#{level} AND stage=#{stage} AND version=#{version} AND enabled=1 AND delete_state=0 ORDER BY id LIMIT 30"
    )
    List<String> lines(
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
}
