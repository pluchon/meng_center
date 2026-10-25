package com.pluchon.meng.companion.mapper;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RuntimeV2Mapper {
    @Select("SELECT id FROM companion_memory WHERE user_id=#{user} AND character_code=#{code} AND kind='BIT' AND delete_state=0 AND invalid_at IS NULL ORDER BY last_used_time DESC,id DESC LIMIT 1000 OFFSET 5")
    List<Long> excessBits(@Param("user") long user,@Param("code") String code);
    @Update("UPDATE companion_session SET context_snapshot=#{snapshot},snapshot_time=#{time} WHERE id=#{id} AND user_id=#{user} AND delete_state=0")
    int snapshot(@Param("snapshot") String snapshot,@Param("time") LocalDateTime time,@Param("id") long id,@Param("user") long user);
    @Update("UPDATE companion_message SET sources=#{sources},retracted=#{retract} WHERE id=#{id}")
    int metadata(@Param("sources") String sources,@Param("retract") boolean retract,@Param("id") long id);
    @Update("UPDATE companion_session SET threads=#{threads} WHERE id=#{id} AND user_id=#{user}")
    int threads(@Param("threads") String threads,@Param("id") long id,@Param("user") long user);
    @Update("UPDATE companion_message SET reaction=#{emotion} WHERE id=#{id} AND session_id=#{session} AND role='USER'")
    int reaction(@Param("emotion") String emotion,@Param("id") long id,@Param("session") long session);
    @Select("SELECT status FROM companion_daily WHERE character_code=#{code} AND date=#{date} AND delete_state=0")
    List<String> status(@Param("code") String code,@Param("date") LocalDate date);
    @Select("SELECT outline FROM companion_arc WHERE character_code=#{code} AND week_start=#{week}")
    List<String> arcs(@Param("code") String code,@Param("week") LocalDate week);
    @Insert("INSERT IGNORE INTO companion_arc(character_code,week_start,outline) VALUES(#{code},#{week},#{outline})")
    int arc(@Param("code") String code,@Param("week") LocalDate week,@Param("outline") String outline);
    @Update("UPDATE companion_daily SET status=#{status} WHERE character_code=#{code} AND date=#{date}")
    int dailyStatus(@Param("status") String status,@Param("code") String code,@Param("date") LocalDate date);
    @Update("UPDATE companion_image SET source='WEB',source_url=#{source},page_url=#{page} WHERE id=#{id} AND user_id=#{user}")
    int webImage(@Param("source") String source,@Param("page") String page,@Param("id") long id,@Param("user") long user);
    @Select("SELECT id,summary FROM companion_episode WHERE delete_state=0 AND (embedding IS NULL OR embedding_version IS NULL OR embedding_version<>'doc-v1') ORDER BY id LIMIT 32")
    List<Map<String,Object>> missingEpisodes();
    @Update("UPDATE companion_episode SET embedding=#{vector},embedding_version='doc-v1' WHERE id=#{id} AND summary=#{summary}")
    int episodeVector(@Param("vector") String vector,@Param("id") long id,@Param("summary") String summary);
    @Update("UPDATE companion_session SET context_snapshot=NULL,snapshot_time=NULL WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0")
    int invalidateSnapshots(@Param("user") long user,@Param("code") String code);
}
