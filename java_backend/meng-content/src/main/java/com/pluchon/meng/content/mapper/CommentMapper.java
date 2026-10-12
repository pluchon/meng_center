package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.CommentEntity;
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
    // 一级评论倒序分页中位于目标之前的条数
    @Select(
        "SELECT COUNT(*) FROM comment WHERE post_id=#{post} AND root_id=0 AND delete_state=0 AND id>#{id}"
    )
    long countBeforeRoot(@Param("post") long post, @Param("id") long id);

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
