package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.CommentEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 评论持久化、互动和计数更新
@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {
    // 一级评论分页（置顶的排最前，其余按编号倒序）中，位于没有置顶的目标之前的条数
    @Select(
        "SELECT COUNT(*) FROM comment WHERE post_id=#{post} AND root_id=0 AND delete_state=0"
            + " AND (id>#{id} OR pinned_time IS NOT NULL)"
    )
    long countBeforeRoot(@Param("post") long post, @Param("id") long id);

    // 取消这篇帖子除指定评论外所有一级评论的置顶
    @Update(
        "UPDATE comment SET pinned_time=NULL WHERE post_id=#{post} AND root_id=0"
            + " AND pinned_time IS NOT NULL AND id<>#{keep}"
    )
    int clearPinsExcept(@Param("post") long post, @Param("keep") long keep);

    // 设置或取消置顶，只有一级评论可以置顶
    @Update(
        "UPDATE comment SET pinned_time=#{time} WHERE id=#{id} AND root_id=0 AND delete_state=0"
    )
    int updatePinnedTime(@Param("id") long id, @Param("time") LocalDateTime time);

    // 评论过这篇帖子的人，最近评论的排前面
    @Select(
        "SELECT author_id FROM comment WHERE post_id=#{post} AND delete_state=0"
            + " GROUP BY author_id ORDER BY MAX(id) DESC LIMIT #{limit}"
    )
    List<Long> selectRecentCommenters(@Param("post") long post, @Param("limit") int limit);

    // 给定的用户里，哪些评论过这篇帖子
    @Select(
        "<script>SELECT DISTINCT author_id FROM comment WHERE post_id=#{post} AND delete_state=0"
            + " AND author_id IN <foreach collection='ids' item='id' open='(' separator=','"
            + " close=')'>#{id}</foreach></script>"
    )
    List<Long> selectCommenterAmong(@Param("post") long post, @Param("ids") List<Long> ids);

    // 回复正序分页中位于目标之前的条数
    @Select(
        "SELECT COUNT(*) FROM comment WHERE root_id=#{root} AND delete_state=0 AND id<#{id}"
    )
    long countBeforeReply(@Param("root") long root, @Param("id") long id);

    @Update(
        "UPDATE comment SET reply_count=reply_count+#{delta} WHERE id=#{id} AND delete_state=0"
    )
    int incrementReplyCount(@Param("id") long id, @Param("delta") long delta);

    @Update(
        "UPDATE comment SET like_count=like_count+#{delta} WHERE id=#{id} AND delete_state=0"
    )
    int incrementLikeCount(@Param("id") long id, @Param("delta") int delta);

    @Insert(
        "INSERT IGNORE INTO comment_like(comment_id,user_id) VALUES(#{id},#{user})"
    )
    int insertLike(@Param("id") long id, @Param("user") long user);

    @Delete(
        "DELETE FROM comment_like WHERE comment_id=#{id} AND user_id=#{user}"
    )
    int deleteLike(@Param("id") long id, @Param("user") long user);

    @Select(
        "<script>SELECT comment_id FROM comment_like WHERE user_id=#{user} AND comment_id IN" +
            " <foreach collection='ids' item='id' open='(' separator=','" +
            " close=')'>#{id}</foreach></script>"
    )
    List<Long> selectLikedCommentIds(
        @Param("user") long user,
        @Param("ids") List<Long> ids
    );
}
