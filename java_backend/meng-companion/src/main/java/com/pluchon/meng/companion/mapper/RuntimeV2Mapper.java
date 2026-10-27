package com.pluchon.meng.companion.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

// 快照和消息来源与业务提交使用同一事务。
@Mapper
public interface RuntimeV2Mapper {
    @Update("UPDATE companion_session SET context_snapshot=#{snapshot},snapshot_time=#{time} WHERE id=#{id} AND user_id=#{user} AND delete_state=0")
    int snapshot(@Param("snapshot") String snapshot,@Param("time") LocalDateTime time,@Param("id") long id,@Param("user") long user);
    @Update("UPDATE companion_message SET sources=#{sources} WHERE id=#{id}")
    int metadata(@Param("sources") String sources,@Param("id") long id);
    @Update("UPDATE companion_session SET context_snapshot=NULL,snapshot_time=NULL WHERE user_id=#{user} AND character_code=#{code} AND delete_state=0")
    int invalidateSnapshots(@Param("user") long user,@Param("code") String code);
}
