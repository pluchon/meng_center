package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.AuthorDailyStatEntity;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 作者每日统计的原子更新与日期查询
@Mapper
public interface AuthorDailyStatMapper
    extends BaseMapper<AuthorDailyStatEntity>
{
    @Insert(
        "INSERT INTO author_daily_stat(author_id,stat_date,${column}) " +
            "VALUES(#{author},#{date},GREATEST(0,#{delta})) ON DUPLICATE KEY UPDATE " +
            "${column}=GREATEST(0,${column}+#{delta})"
    )
    int incrementCount(
        @Param("author") long author,
        @Param("date") LocalDate date,
        @Param("column") String column,
        @Param("delta") int delta
    );
}
