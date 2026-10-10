package com.pluchon.meng.content.mapper;

import com.pluchon.meng.content.entity.PostFavoriteCount;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PostRelationMapper {
    @Select(
        "<script>SELECT post_id,COUNT(*) AS count FROM post_favorite WHERE post_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY post_id</script>"
    )
    @ConstructorArgs({
        @Arg(column = "post_id", javaType = long.class),
        @Arg(column = "count", javaType = long.class),
    })
    List<PostFavoriteCount> favoriteCounts(@Param("ids") List<Long> ids);

    @Insert(
        "INSERT IGNORE INTO post_like(post_id,user_id) VALUES(#{post},#{user})"
    )
    int like(@Param("post") long post, @Param("user") long user);

    @Delete("DELETE FROM post_like WHERE post_id=#{post} AND user_id=#{user}")
    int unlike(@Param("post") long post, @Param("user") long user);

    @Insert(
        "INSERT IGNORE INTO post_favorite(post_id,user_id) VALUES(#{post},#{user})"
    )
    int favorite(@Param("post") long post, @Param("user") long user);

    @Delete(
        "DELETE FROM post_favorite WHERE post_id=#{post} AND user_id=#{user}"
    )
    int unfavorite(@Param("post") long post, @Param("user") long user);

    @Select(
        "<script>SELECT post_id FROM post_like WHERE user_id=#{user} AND post_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"
    )
    List<Long> liked(@Param("user") long user, @Param("ids") List<Long> ids);

    @Select(
        "<script>SELECT post_id FROM post_favorite WHERE user_id=#{user} AND post_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"
    )
    List<Long> favorited(
        @Param("user") long user,
        @Param("ids") List<Long> ids
    );
}
