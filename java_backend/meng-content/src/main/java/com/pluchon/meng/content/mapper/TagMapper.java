package com.pluchon.meng.content.mapper;

import com.pluchon.meng.content.entity.TagEntity;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TagMapper {
    @Select(
            "SELECT id,name,name_key,use_count FROM tag WHERE delete_state=0 AND name_key LIKE"
                    + " CONCAT(#{prefix},'%') ORDER BY use_count DESC,name_key LIMIT #{size}")
    List<TagEntity> suggest(@Param("prefix") String prefix, @Param("size") int size);

    @Insert("INSERT INTO tag(name,name_key) VALUES(#{name},#{key}) ON DUPLICATE KEY UPDATE id=id")
    int ensure(@Param("name") String name, @Param("key") String key);

    @Select("SELECT id,name,name_key,use_count FROM tag WHERE name_key=#{key} FOR UPDATE")
    TagEntity find(String key);

    @Insert("INSERT INTO post_tag(post_id,tag_id) VALUES(#{post},#{tag})")
    int attach(@Param("post") long post, @Param("tag") long tag);

    @Update("UPDATE tag SET use_count=use_count+1 WHERE id=#{id}")
    int increment(long id);

    @Update("UPDATE tag SET use_count=GREATEST(0,use_count-1) WHERE id=#{id}")
    int decrement(long id);

    @Delete("DELETE FROM post_tag WHERE post_id=#{post}")
    int detach(long post);

    @Select(
            "SELECT t.name FROM tag t JOIN post_tag r ON r.tag_id=t.id WHERE r.post_id=#{post}"
                    + " ORDER BY t.id")
    List<String> forPost(long post);
}
