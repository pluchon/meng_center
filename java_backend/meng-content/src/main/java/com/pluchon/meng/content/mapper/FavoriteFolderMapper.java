package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.FavoriteFolder;
import com.pluchon.meng.content.entity.enums.FolderColor;
import com.pluchon.meng.content.entity.enums.FolderVisibility;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FavoriteFolderMapper extends BaseMapper<FavoriteFolder> {
    @Insert(
        "INSERT IGNORE INTO favorite_folder(user_id,default_owner_id) VALUES(#{user},#{user})"
    )
    int ensure(long user);

    @Select(
        "SELECT * FROM favorite_folder WHERE default_owner_id=#{user} FOR UPDATE"
    )
    FavoriteFolder lockDefault(long user);

    @Select(
        "SELECT * FROM favorite_folder WHERE id=#{id} AND delete_state=0 FOR UPDATE"
    )
    FavoriteFolder lock(long id);

    @Data
    class Row {

        private Long id;
        private String name;
        private String description;
        private String coverName;
        private FolderColor color;
        private FolderVisibility visibility;
        private Long defaultOwnerId;
        private Long count;
        private LocalDateTime latestAt;
        private String postCover;
    }

    @Select(
        "SELECT d.id,d.name,d.description,d.cover_name,d.color,d.visibility,d.default_owner_id,COUNT(p.id) count,MAX(CASE WHEN p.id IS NOT NULL THEN f.create_time END) latest_at,(SELECT i.thumb_name FROM post_favorite z JOIN post q ON q.id=z.post_id AND q.delete_state=0 JOIN post_image i ON i.id=q.cover_image_id WHERE z.folder_id=d.id ORDER BY z.id DESC LIMIT 1) post_cover FROM favorite_folder d LEFT JOIN post_favorite f ON f.folder_id=d.id LEFT JOIN post p ON p.id=f.post_id AND p.delete_state=0 WHERE d.user_id=#{user} AND d.delete_state=0 AND (#{visibility} IS NULL OR d.visibility=#{visibility}) GROUP BY d.id ORDER BY (d.default_owner_id IS NOT NULL) DESC,COALESCE(MAX(CASE WHEN p.id IS NOT NULL THEN f.create_time END),d.create_time) DESC,d.id DESC LIMIT #{offset},#{size}"
    )
    List<Row> folders(
        @Param("user") long user,
        @Param("visibility") FolderVisibility visibility,
        @Param("offset") long offset,
        @Param("size") long size
    );

    @Data
    class PostRow {

        private Long id;
        private Long authorId;
        private String category;
        private String title;
        private String thumbName;
        private LocalDateTime savedAt;
    }

    String POST_COLUMNS =
        "SELECT p.id,p.author_id,p.category,p.title,i.thumb_name,f.create_time saved_at FROM ";

    @Select(
        POST_COLUMNS +
            "post_favorite f JOIN post p ON p.id=f.post_id AND p.delete_state=0 LEFT JOIN post_image i ON i.id=p.cover_image_id WHERE f.user_id=#{user} AND f.folder_id=#{folder} ORDER BY f.id DESC LIMIT #{offset},#{size}"
    )
    List<PostRow> posts(
        @Param("user") long user,
        @Param("folder") long folder,
        @Param("offset") long offset,
        @Param("size") long size
    );

    @Select(
        POST_COLUMNS +
            "post_like f JOIN post p ON p.id=f.post_id AND p.delete_state=0 LEFT JOIN post_image i ON i.id=p.cover_image_id WHERE f.user_id=#{user} ORDER BY f.id DESC LIMIT #{offset},#{size}"
    )
    List<PostRow> likes(
        @Param("user") long user,
        @Param("offset") long offset,
        @Param("size") long size
    );

    @Select(
        "SELECT COUNT(DISTINCT f.post_id) FROM post_favorite f JOIN post p ON p.id=f.post_id AND p.delete_state=0 WHERE f.user_id=#{user} AND (#{folder} IS NULL OR f.folder_id=#{folder})"
    )
    long favorites(@Param("user") long user, @Param("folder") Long folder);

    // 某人所有公开收藏夹里的帖子数，一篇帖子放进几个公开收藏夹只算一次
    @Select(
        "SELECT COUNT(DISTINCT f.post_id) FROM post_favorite f JOIN favorite_folder d ON d.id=f.folder_id AND d.delete_state=0 AND d.visibility='PUBLIC' JOIN post p ON p.id=f.post_id AND p.delete_state=0 WHERE f.user_id=#{user}"
    )
    long publicFavorites(long user);

    @Select(
        "SELECT COUNT(*) FROM post_like f JOIN post p ON p.id=f.post_id AND p.delete_state=0 WHERE f.user_id=#{user}"
    )
    long liked(long user);

    @Data
    class Relation {

        private Long postId;
        private LocalDateTime createTime;
    }

    @Select(
        "SELECT post_id,create_time FROM post_favorite WHERE folder_id=#{folder} ORDER BY post_id"
    )
    List<Relation> relations(long folder);
}
