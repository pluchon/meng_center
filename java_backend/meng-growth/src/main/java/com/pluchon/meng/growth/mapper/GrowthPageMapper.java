package com.pluchon.meng.growth.mapper;

import com.pluchon.meng.growth.entity.DailyExperience;
import com.pluchon.meng.growth.entity.ExperienceRecordRow;
import com.pluchon.meng.growth.entity.SourceExperience;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 成长页只读查询，所有条件均包含当前用户和逻辑删除过滤
@Mapper
public interface GrowthPageMapper {
    // 按日期游标倒序分批获取签到，避免为连续天数加载整个历史
    @Select("SELECT check_date FROM check_in WHERE user_id=#{user} AND delete_state=0 "
            + "AND check_date<#{before} ORDER BY check_date DESC LIMIT #{size}")
    List<LocalDate> selectEarlierCheckIns(@Param("user") long user, @Param("before") LocalDate before, @Param("size") int size);

    // 汇总三种来源的今日奖励和累计日期数
    @Select("SELECT source,SUM(CASE WHEN record_date=#{today} THEN 1 ELSE 0 END) count,"
            + "SUM(CASE WHEN record_date=#{today} THEN amount ELSE 0 END) earned,"
            + "COUNT(DISTINCT record_date) active_days FROM exp_record "
            + "WHERE user_id=#{user} AND delete_state=0 GROUP BY source")
    List<SourceExperience> selectTaskExperience(@Param("user") long user, @Param("today") LocalDate today);

    // 按业务日期聚合选定区间的每日经验
    @Select("SELECT record_date date,SUM(amount) amount FROM exp_record WHERE user_id=#{user} "
            + "AND delete_state=0 AND record_date>=#{start} AND record_date<#{end} GROUP BY record_date ORDER BY record_date")
    List<DailyExperience> selectDailyExperience(@Param("user") long user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 与每日统计使用同一个区间汇总来源经验
    @Select("SELECT source,COUNT(*) count,SUM(amount) earned,COUNT(DISTINCT record_date) active_days "
            + "FROM exp_record WHERE user_id=#{user} AND delete_state=0 "
            + "AND record_date>=#{start} AND record_date<#{end} GROUP BY source")
    List<SourceExperience> selectSourceExperience(@Param("user") long user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 统计来源筛选后的用户明细数量
    @Select("<script>SELECT COUNT(*) FROM exp_record WHERE user_id=#{user} AND delete_state=0 "
            + "<if test='source != null'>AND source=#{source}</if></script>")
    long countExperienceRecords(@Param("user") long user, @Param("source") ExpSource source);

    // 相同创建时间以主键倒序稳定翻页，来源标识不投影
    @Select("<script>SELECT source,amount,create_time FROM exp_record WHERE user_id=#{user} AND delete_state=0 "
            + "<if test='source != null'>AND source=#{source}</if> "
            + "ORDER BY create_time DESC,id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ExperienceRecordRow> selectExperienceRecords(@Param("user") long user, @Param("source") ExpSource source,
            @Param("offset") long offset, @Param("size") long size);
}
