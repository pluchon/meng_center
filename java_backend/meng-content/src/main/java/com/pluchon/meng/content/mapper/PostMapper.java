package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.content.entity.AuthorPublicationCount;
import com.pluchon.meng.content.entity.CreatorTotals;
import com.pluchon.meng.content.entity.PostEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 帖子查询、锁定与计数更新
@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {
    // 按作者汇总现存帖子的计数
    @Select(
        "SELECT COUNT(*) post_count,COALESCE(SUM(view_count),0) view_count," +
            "COALESCE(SUM(like_count),0) like_count,COALESCE(SUM(comment_count),0) comment_count " +
            "FROM post WHERE author_id=#{author} AND delete_state=0"
    )
    CreatorTotals selectCreatorTotals(long author);

    // 按业务时区日期汇总当前作者在区间内发布的帖子
    @Select(
        "SELECT DATE(DATE_ADD(create_time, INTERVAL #{offset} SECOND)) date,COUNT(*) count " +
            "FROM post WHERE author_id=#{author} AND delete_state=0 AND create_time>=#{start} " +
            "AND create_time<#{end} GROUP BY DATE(DATE_ADD(create_time, INTERVAL #{offset} SECOND))"
    )
    List<AuthorPublicationCount> selectPublicationCounts(
        @Param("author") long author,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("offset") int offset
    );

    // 按主键游标读取维护批次，关闭总数查询并覆盖普通接口的五十条限制
    default List<PostEntity> selectRebuildBatch(
        long cursor,
        LocalDateTime since
    ) {
        var page = new Page<PostEntity>(1, 500, false);
        page.setMaxLimit(500L);
        return selectPage(
            page,
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<PostEntity>lambdaQuery()
                .gt(PostEntity::getId, cursor)
                .ge(since != null, PostEntity::getCreateTime, since)
                .orderByAsc(PostEntity::getId)
        ).getRecords();
    }

    @Update(
        "UPDATE post SET hot_score=hot_score+#{delta} WHERE id=#{id} AND delete_state=0"
    )
    int incrementHotScore(@Param("id") long id, @Param("delta") long delta);

    @Update(
        "UPDATE post p LEFT JOIN (SELECT post_id,COUNT(*) n FROM post_favorite GROUP BY" +
            " post_id) f ON f.post_id=p.id SET" +
            " p.hot_score=p.view_count*#{view}+p.like_count*#{like}+p.comment_count*#{comment}+COALESCE(f.n,0)*#{favorite}" +
            " WHERE p.delete_state=0 AND p.create_time>=#{since}"
    )
    int recalculateRecentHotScores(
        @Param("since") LocalDateTime since,
        @Param("view") int view,
        @Param("like") int like,
        @Param("comment") int comment,
        @Param("favorite") int favorite
    );

    @Update(
        "UPDATE post SET view_count=view_count+1,hot_score=hot_score+#{weight} WHERE id=#{id}" +
            " AND delete_state=0"
    )
    int incrementViews(@Param("id") long id, @Param("weight") int weight);

    @Update(
        "UPDATE post SET comment_count=comment_count+#{delta},hot_score=hot_score+#{score}" +
            " WHERE id=#{id} AND delete_state=0"
    )
    int incrementCommentCount(
        @Param("id") long id,
        @Param("delta") long delta,
        @Param("score") long score
    );

    @Update(
        "UPDATE post SET cover_image_id=#{cover} WHERE id=#{id} AND delete_state=0"
    )
    int updateCoverImage(@Param("id") long id, @Param("cover") Long cover);

    @Select("SELECT * FROM post WHERE id=#{id} AND delete_state=0 FOR UPDATE")
    PostEntity selectByIdForUpdate(long id);

    @Update(
        "UPDATE post SET like_count=like_count+#{delta},hot_score=hot_score+#{score} WHERE" +
            " id=#{id} AND delete_state=0"
    )
    int incrementLikeCount(
        @Param("id") long id,
        @Param("delta") int delta,
        @Param("score") long score
    );
}
