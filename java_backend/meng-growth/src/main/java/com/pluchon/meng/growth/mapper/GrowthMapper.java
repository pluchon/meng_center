package com.pluchon.meng.growth.mapper;

import com.pluchon.meng.growth.entity.GrowthAccountEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GrowthMapper {
    @Insert(
        "INSERT INTO growth_account(user_id,exp,level) VALUES(#{user},0,1) ON DUPLICATE KEY UPDATE user_id=#{user}"
    )
    int ensure(long user);

    @Select(
        "SELECT id,user_id,exp,level FROM growth_account WHERE user_id=#{user} AND delete_state=0 FOR UPDATE"
    )
    GrowthAccountEntity lock(long user);

    @Update(
        "UPDATE growth_account SET exp=#{exp},level=#{level} WHERE user_id=#{user} AND delete_state=0"
    )
    int update(
        @Param("user") long user,
        @Param("exp") long exp,
        @Param("level") int level
    );

    @Select(
        "SELECT COUNT(*) FROM exp_record WHERE user_id=#{user} AND source=#{source} AND source_id=#{id}"
    )
    int exists(
        @Param("user") long user,
        @Param("source") String source,
        @Param("id") String id
    );

    @Select(
        "SELECT COUNT(*) FROM exp_record WHERE user_id=#{user} AND source=#{source} AND record_date=#{date} AND delete_state=0"
    )
    int count(
        @Param("user") long user,
        @Param("source") String source,
        @Param("date") LocalDate date
    );

    @Insert(
        "INSERT INTO exp_record(user_id,source,source_id,amount,record_date) VALUES(#{user},#{source},#{id},#{amount},#{date})"
    )
    int record(
        @Param("user") long user,
        @Param("source") String source,
        @Param("id") String id,
        @Param("amount") int amount,
        @Param("date") LocalDate date
    );

    @Insert(
        "INSERT IGNORE INTO check_in(user_id,check_date) VALUES(#{user},#{date})"
    )
    int checkIn(@Param("user") long user, @Param("date") LocalDate date);

    @Select(
        "SELECT check_date FROM check_in WHERE user_id=#{user} AND check_date BETWEEN #{start} AND #{end} AND delete_state=0 ORDER BY check_date"
    )
    List<LocalDate> week(
        @Param("user") long user,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );
}
