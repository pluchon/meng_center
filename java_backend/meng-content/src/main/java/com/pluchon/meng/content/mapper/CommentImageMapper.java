package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.CommentImageEntity;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentImageMapper extends BaseMapper<CommentImageEntity> {
    @Update(
            "UPDATE comment_image SET comment_id=#{comment},sort=#{sort} WHERE id=#{id} AND"
                    + " uploader_id=#{user} AND comment_id IS NULL AND delete_state=0")
    int attach(
            @Param("id") long id,
            @Param("comment") long comment,
            @Param("user") long user,
            @Param("sort") int sort);
}
