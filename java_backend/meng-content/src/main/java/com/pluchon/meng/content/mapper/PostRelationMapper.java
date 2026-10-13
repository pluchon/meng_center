package com.pluchon.meng.content.mapper;

import com.pluchon.meng.content.entity.PostFavoriteCount;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 帖子点赞与收藏关系存取
@Mapper
public interface PostRelationMapper {
    // 只统计当前作者现存帖子的收藏关系
    @Select(
        "SELECT COUNT(*) FROM post_favorite f JOIN post p ON p.id=f.post_id " +
            "WHERE p.author_id=#{author} AND p.delete_state=0"
    )
    long selectCreatorFavoriteCount(long author);

    // 在帖子锁内读取取消关系的原始时间
    @Select(
        "SELECT create_time FROM post_like WHERE post_id=#{post} AND user_id=#{user}"
    )
    LocalDateTime selectLikeCreateTime(
        @Param("post") long post,
        @Param("user") long user
    );

    // 在帖子锁内读取收藏关系的原始时间
    @Select(
        "SELECT create_time FROM post_favorite WHERE post_id=#{post} AND user_id=#{user}"
    )
    LocalDateTime selectFavoriteCreateTime(
        @Param("post") long post,
        @Param("user") long user
    );

    @Select(
        "<script>SELECT post_id,COUNT(*) AS count FROM post_favorite WHERE post_id IN <foreach" +
            " collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " GROUP BY post_id</script>"
    )
    @ConstructorArgs({
        @Arg(column = "post_id", javaType = long.class),
        @Arg(column = "count", javaType = long.class),
    })
    List<PostFavoriteCount> selectFavoriteCounts(@Param("ids") List<Long> ids);

    @Insert(
        "INSERT IGNORE INTO post_like(post_id,user_id) VALUES(#{post},#{user})"
    )
    int insertLike(@Param("post") long post, @Param("user") long user);

    @Delete("DELETE FROM post_like WHERE post_id=#{post} AND user_id=#{user}")
    int deleteLike(@Param("post") long post, @Param("user") long user);

    @Insert(
        "INSERT IGNORE INTO post_favorite(post_id,user_id,folder_id) VALUES(#{post},#{user},#{folder})"
    )
    int insertFavorite(
        @Param("post") long post,
        @Param("user") long user,
        @Param("folder") long folder
    );

    @Delete(
        "DELETE FROM post_favorite WHERE post_id=#{post} AND user_id=#{user}"
    )
    int deleteFavorite(@Param("post") long post, @Param("user") long user);

    @Select(
        "<script>SELECT post_id FROM post_like WHERE user_id=#{user} AND post_id IN <foreach" +
            " collection='ids' item='id' open='(' separator=','" +
            " close=')'>#{id}</foreach></script>"
    )
    List<Long> selectLikedPostIds(
        @Param("user") long user,
        @Param("ids") List<Long> ids
    );

    @Select(
        "<script>SELECT post_id FROM post_favorite WHERE user_id=#{user} AND post_id IN" +
            " <foreach collection='ids' item='id' open='(' separator=','" +
            " close=')'>#{id}</foreach></script>"
    )
    List<Long> selectFavoritedPostIds(
        @Param("user") long user,
        @Param("ids") List<Long> ids
    );
}
