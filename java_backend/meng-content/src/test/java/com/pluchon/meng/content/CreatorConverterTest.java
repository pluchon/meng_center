package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.converter.CreatorConverter;
import com.pluchon.meng.content.entity.PostDraftEntity;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class CreatorConverterTest {

    @Test
    void publishedCopiesEveryFieldAndSuppliedCoverAndFavoriteCount() {
        var p = new PostEntity();
        p.setId(9L);
        p.setCategory(PostCategory.TECH);
        p.setTitle("标题");
        p.setSummary("摘要");
        p.setCreateTime(LocalDateTime.of(2026, 10, 11, 0, 0));
        p.setViewCount(21L);
        p.setLikeCount(4L);
        p.setCommentCount(6L);
        var cover = new ImageVO(8, "/image", "/thumb", 300, 200);
        var v = new CreatorConverter().post(p, cover, 7);
        assertEquals(p.getId(), v.id());
        assertEquals(p.getCategory(), v.category());
        assertEquals(p.getTitle(), v.title());
        assertEquals(p.getSummary(), v.summary());
        assertEquals(cover, v.cover());
        assertEquals(p.getCreateTime(), v.createTime());
        assertEquals(p.getViewCount(), v.viewCount());
        assertEquals(p.getLikeCount(), v.likeCount());
        assertEquals(p.getCommentCount(), v.commentCount());
        assertEquals(7, v.favoriteCount());
    }

    @Test
    void draftCopiesEveryFieldAndKeepsFiveImagesInInputOrder() {
        var d = new PostDraftEntity();
        d.setId(3L);
        d.setCategory(PostCategory.ANIME);
        d.setTitle("草稿");
        d.setUpdateTime(LocalDateTime.of(2026, 10, 11, 1, 0));
        d.setImageIds(List.of(7L, 6L, 5L, 4L, 3L, 2L, 1L));
        var images = LongStream.rangeClosed(1, 7)
            .mapToObj(i -> new ImageVO(i, "/" + i, "/thumb" + i, 100, 80))
            .toList();
        var v = new CreatorConverter().draft(d, "开头", images);
        assertEquals(d.getId(), v.id());
        assertEquals(d.getCategory(), v.category());
        assertEquals(d.getTitle(), v.title());
        assertEquals("开头", v.summary());
        assertEquals(images.subList(0, 5), v.images());
        assertEquals(7, v.imageCount());
        assertEquals(d.getUpdateTime(), v.updateTime());
    }
}
