package com.pluchon.meng.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;

// 作者每日互动统计，不随内容删除回减
@Data
@TableName("author_daily_stat")
public class AuthorDailyStatEntity {

    // 统计记录编号
    @TableId(type = IdType.AUTO)
    private Long id;

    // 作者编号
    private Long authorId;
    // 台北时区的统计日期
    private LocalDate statDate;
    // 当天新增浏览
    private Long viewCount = 0L;
    // 当天新增喜欢
    private Long likeCount = 0L;
    // 当天新增评论
    private Long commentCount = 0L;
    // 当天新增收藏
    private Long favoriteCount = 0L;
}
