package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.PostEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {
    @Update(
        "UPDATE post SET view_count=view_count+1,hot_score=hot_score+#{weight} WHERE id=#{id} AND delete_state=0"
    )
    int view(@Param("id") long id, @Param("weight") int weight);

    @Select("SELECT * FROM post WHERE id=#{id} AND delete_state=0 FOR UPDATE")
    PostEntity lock(long id);

    @Update(
        "UPDATE post SET like_count=like_count+#{delta},hot_score=hot_score+#{score} WHERE id=#{id} AND delete_state=0"
    )
    int changeLikes(
        @Param("id") long id,
        @Param("delta") int delta,
        @Param("score") long score
    );
}
