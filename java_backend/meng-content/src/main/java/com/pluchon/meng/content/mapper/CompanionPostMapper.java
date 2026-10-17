package com.pluchon.meng.content.mapper;

import com.pluchon.meng.api.content.contract.CompanionPostContract;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// 内部工具只读取本域公开帖子，作者名字使用本域公开资料快照
@Mapper
public interface CompanionPostMapper {
    String PUBLIC = "SELECT p.id,p.title,LEFT(p.summary,300) summary,COALESCE(a.nickname,'社区用户') author_nickname "
        + "FROM post p LEFT JOIN public_author_snapshot a ON a.user_id=p.author_id AND a.delete_state=0 "
        + "WHERE p.delete_state=0 AND p.review_state='NORMAL' ";

    @Select(PUBLIC + "AND (LOCATE(#{q},p.title)>0 OR LOCATE(#{q},p.summary)>0) ORDER BY p.hot_score DESC,p.id DESC LIMIT #{size}")
    List<CompanionPostContract.Post> search(@Param("q") String query, @Param("size") int size);

    @Select(PUBLIC + "AND p.author_id=#{user} ORDER BY p.id DESC LIMIT #{size}")
    List<CompanionPostContract.Post> recent(@Param("user") long user, @Param("size") int size);

    record AuthorSnapshot(long userId, String nickname) {}

    @Insert("""
        <script>INSERT INTO public_author_snapshot(user_id,nickname) VALUES
        <foreach collection="authors" item="author" separator=",">(#{author.userId},#{author.nickname})</foreach>
        ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),delete_state=0</script>
        """)
    void snapshots(@Param("authors") List<AuthorSnapshot> authors);
}
