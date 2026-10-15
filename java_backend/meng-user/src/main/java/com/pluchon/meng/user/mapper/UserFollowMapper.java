package com.pluchon.meng.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.user.entity.FollowEdge;
import com.pluchon.meng.user.entity.UserFollowEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

// 关注关系表访问。lockPair 连已取消的记录一起读并加行锁，只能在事务里调用
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowEntity> {
    @Select(
        "SELECT id,follower_id,followee_id,delete_state FROM user_follow"
            + " WHERE follower_id=#{follower} AND followee_id=#{followee} FOR UPDATE"
    )
    UserFollowEntity lockPair(
        @Param("follower") long follower,
        @Param("followee") long followee
    );

    @Insert(
        "INSERT INTO user_follow(follower_id,followee_id) VALUES(#{follower},#{followee})"
    )
    int insertPair(
        @Param("follower") long follower,
        @Param("followee") long followee
    );

    @Update("UPDATE user_follow SET delete_state=#{state} WHERE id=#{id}")
    int setState(@Param("id") long id, @Param("state") int state);

    // 某人关注的人，新关注的在前
    @Select(
        "SELECT followee_id user_id,create_time FROM user_follow"
            + " WHERE follower_id=#{user} AND delete_state=0"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
    )
    List<FollowEdge> following(
        @Param("user") long user,
        @Param("offset") long offset,
        @Param("size") long size
    );

    @Select(
        "SELECT COUNT(*) FROM user_follow WHERE follower_id=#{user} AND delete_state=0"
    )
    long countFollowing(@Param("user") long user);

    // 某人的粉丝，新关注的在前
    @Select(
        "SELECT follower_id user_id,create_time FROM user_follow"
            + " WHERE followee_id=#{user} AND delete_state=0"
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}"
    )
    List<FollowEdge> followers(
        @Param("user") long user,
        @Param("offset") long offset,
        @Param("size") long size
    );

    @Select(
        "SELECT COUNT(*) FROM user_follow WHERE followee_id=#{user} AND delete_state=0"
    )
    long countFollowers(@Param("user") long user);

    // 给定的人里，当前用户关注了哪些
    @Select(
        "<script>SELECT followee_id FROM user_follow WHERE follower_id=#{me} AND delete_state=0"
            + " AND followee_id IN <foreach collection='ids' item='id' open='(' separator=','"
            + " close=')'>#{id}</foreach></script>"
    )
    List<Long> followedAmong(
        @Param("me") long me,
        @Param("ids") List<Long> ids
    );

    // 给定的人里，哪些关注了当前用户
    @Select(
        "<script>SELECT follower_id FROM user_follow WHERE followee_id=#{me} AND delete_state=0"
            + " AND follower_id IN <foreach collection='ids' item='id' open='(' separator=','"
            + " close=')'>#{id}</foreach></script>"
    )
    List<Long> followersAmong(
        @Param("me") long me,
        @Param("ids") List<Long> ids
    );
}
