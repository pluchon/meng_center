package com.pluchon.meng.content.mapper;

import com.pluchon.meng.content.entity.PostTagName;
import com.pluchon.meng.content.entity.TagEntity;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

// 系统标签和帖子标签关系存取
@Mapper
public interface TagMapper {

    @Select(
            "<script>SELECT r.post_id,t.name FROM post_tag r JOIN tag t ON t.id=r.tag_id WHERE"
                    + " r.post_id IN <foreach collection='ids' item='id' open='(' separator=','"
                    + " close=')'>#{id}</foreach> ORDER BY r.post_id,t.id</script>")
    @ConstructorArgs({
        @Arg(column = "post_id", javaType = long.class),
        @Arg(column = "name", javaType = String.class)
    })
    List<PostTagName> selectNamesByPostIds(@Param("ids") List<Long> ids);

    @Select(
            "SELECT id,name,name_key,use_count FROM tag WHERE delete_state=0 AND name_key LIKE"
                    + " CONCAT(#{prefix},'%') ORDER BY use_count DESC,name_key LIMIT #{size}")
    List<TagEntity> selectByPrefix(@Param("prefix") String prefix, @Param("size") int size);

    @Insert("INSERT INTO tag(name,name_key) VALUES(#{name},#{key}) ON DUPLICATE KEY UPDATE id=id")
    int insertIfAbsent(@Param("name") String name, @Param("key") String key);

    @Select("SELECT id,name,name_key,use_count FROM tag WHERE name_key=#{key} FOR UPDATE")
    TagEntity selectByNameKeyForUpdate(String key);

    @Insert("INSERT INTO post_tag(post_id,tag_id) VALUES(#{post},#{tag})")
    int insertPostTag(@Param("post") long post, @Param("tag") long tag);

    @Update("UPDATE tag SET use_count=use_count+1 WHERE id=#{id}")
    int incrementUseCount(long id);

    @Update("UPDATE tag SET use_count=GREATEST(0,use_count-1) WHERE id=#{id}")
    int decrementUseCount(long id);

    @Delete("DELETE FROM post_tag WHERE post_id=#{post}")
    int deletePostTags(long post);

    @Select(
            "SELECT t.name FROM tag t JOIN post_tag r ON r.tag_id=t.id WHERE r.post_id=#{post}"
                    + " ORDER BY t.id")
    List<String> selectNamesByPostId(long post);
}
