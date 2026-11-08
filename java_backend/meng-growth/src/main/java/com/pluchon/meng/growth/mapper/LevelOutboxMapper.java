package com.pluchon.meng.growth.mapper;

import com.pluchon.meng.growth.entity.LevelOutboxRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 等级事件发件箱：与升级同事务写入，提交后再投递
@Mapper
public interface LevelOutboxMapper {

    @Insert(
            "INSERT INTO level_event_outbox(event_id,user_id,level,occurred_at)"
                + " VALUES(#{eventId},#{user},#{level},#{occurredAt})")
    int insert(
            @Param("eventId") String eventId,
            @Param("user") long user,
            @Param("level") int level,
            @Param("occurredAt") LocalDateTime occurredAt);

    // 多实例同时投递时互不阻塞，各取各的
    @Select(
            "SELECT id,event_id,user_id,level,occurred_at FROM level_event_outbox WHERE sent=0 AND"
                + " delete_state=0 ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<LevelOutboxRow> claimPending(int limit);

    @Update("UPDATE level_event_outbox SET sent=1 WHERE id=#{id}")
    int markSent(long id);
}
