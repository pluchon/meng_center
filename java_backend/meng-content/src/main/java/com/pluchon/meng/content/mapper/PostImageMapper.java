package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.PostImageEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

// 帖子配图持久化与归属关联
@Mapper
public interface PostImageMapper extends BaseMapper<PostImageEntity> {

    @Update(
            "UPDATE post_image SET post_id=#{post},sort=#{sort},kind=#{kind} WHERE id=#{id} AND"
                + " uploader_id=#{user} AND post_id IS NULL AND delete_state=0")
    int attachToPost(
            @Param("id") long id,
            @Param("post") long post,
            @Param("user") long user,
            @Param("sort") int sort,
            @Param("kind") String kind);

    // 帖子被退回草稿箱时，把它的配图从帖子上摘下来，草稿才能重新引用
    @Update("UPDATE post_image SET post_id=NULL WHERE post_id=#{post} AND delete_state=0")
    int detachFromPost(@Param("post") long post);
}
