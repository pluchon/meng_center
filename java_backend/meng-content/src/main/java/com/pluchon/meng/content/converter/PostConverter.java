package com.pluchon.meng.content.converter;

import cn.hutool.core.bean.BeanUtil;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.controller.dto.CreatePostDTO;
import com.pluchon.meng.content.controller.dto.SaveDraftDTO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.entity.PostDraftEntity;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.PostImageEntity;

import org.springframework.stereotype.Component;

import java.util.List;

// 帖子、配图与草稿的字段转换
@Component
public class PostConverter {

    // 从发帖参数复制同名字段，标题在转换后去除首尾空白
    public PostEntity create(CreatePostDTO dto) {
        var post = new PostEntity();
        BeanUtil.copyProperties(dto, post);
        post.setTitle(dto.title().trim());
        return post;
    }

    // 更新草稿参数，保留实体已有的主键和作者
    public void applyDraft(SaveDraftDTO dto, PostDraftEntity draft) {
        BeanUtil.copyProperties(dto, draft);
    }

    // 配图资源访问前缀
    public static final String FILE_ROOT = "/content/files/";

    public ImageVO image(PostImageEntity i) {
        return new ImageVO(
                i.getId(),
                FILE_ROOT + i.getFileName(),
                FILE_ROOT + i.getThumbName(),
                i.getWidth(),
                i.getHeight());
    }

    public PostFeedVO feed(
            PostEntity p,
            List<PostImageEntity> images,
            PublicUserVO author,
            boolean liked,
            boolean favorite,
            long favoriteCount) {
        return new PostFeedVO(
                p.getId(),
                p.getCategory(),
                p.getTitle(),
                p.getSummary(),
                images.stream().map(this::image).toList(),
                p.getImageCount(),
                p.getViewCount(),
                p.getLikeCount(),
                p.getCommentCount(),
                favoriteCount,
                liked,
                favorite,
                p.getIpRegion(),
                p.getCreateTime(),
                author == null ? new PublicUserVO(p.getAuthorId(), null, null, 1) : author);
    }
}
