package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.PostDraftEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PostDraftMapper extends BaseMapper<PostDraftEntity> {
    @Insert(
        "INSERT INTO post_draft_owner(author_id) VALUES(#{author}) ON DUPLICATE KEY UPDATE author_id=author_id"
    )
    int lockOwner(long author);

    @Update(
        "UPDATE post_draft SET category=#{category},title=#{title},content=#{content},image_ids=#{imageIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},cover_image_id=#{coverImageId},tags=#{tags,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} WHERE id=#{id} AND author_id=#{authorId} AND delete_state=0"
    )
    int save(PostDraftEntity draft);
}
