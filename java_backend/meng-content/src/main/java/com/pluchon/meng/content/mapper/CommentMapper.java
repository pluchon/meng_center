package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.CommentEntity;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {
    @Update("UPDATE comment SET reply_count=reply_count+#{delta} WHERE id=#{id} AND delete_state=0")
    int replies(@Param("id") long id, @Param("delta") long delta);

    @Update("UPDATE comment SET like_count=like_count+#{delta} WHERE id=#{id} AND delete_state=0")
    int likes(@Param("id") long id, @Param("delta") int delta);

    @Insert("INSERT IGNORE INTO comment_like(comment_id,user_id) VALUES(#{id},#{user})")
    int like(@Param("id") long id, @Param("user") long user);

    @Delete("DELETE FROM comment_like WHERE comment_id=#{id} AND user_id=#{user}")
    int unlike(@Param("id") long id, @Param("user") long user);

    @Select(
            "<script>SELECT comment_id FROM comment_like WHERE user_id=#{user} AND comment_id IN"
                    + " <foreach collection='ids' item='id' open='(' separator=','"
                    + " close=')'>#{id}</foreach></script>")
    List<Long> liked(@Param("user") long user, @Param("ids") List<Long> ids);
}
