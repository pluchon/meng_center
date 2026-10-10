package com.pluchon.meng.content.converter;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.controller.vo.*;
import com.pluchon.meng.content.entity.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostConverter {

    public static final String FILE_ROOT = "/content/files/";

    public ImageVO image(PostImageEntity i) {
        return new ImageVO(
            i.getId(),
            FILE_ROOT + i.getFileName(),
            FILE_ROOT + i.getThumbName(),
            i.getWidth(),
            i.getHeight()
        );
    }

    public PostFeedVO feed(
        PostEntity p,
        List<PostImageEntity> images,
        PublicUserVO author,
        boolean liked,
        boolean favorite,
        long favoriteCount
    ) {
        return new PostFeedVO(
            p.getId(),
            p.getCategory(),
            p.getTitle(),
            p.getSummary(),
            images.stream().limit(5).map(this::image).toList(),
            p.getImageCount(),
            p.getViewCount(),
            p.getLikeCount(),
            p.getCommentCount(),
            favoriteCount,
            liked,
            favorite,
            p.getIpRegion(),
            p.getCreateTime(),
            author == null
                ? new PublicUserVO(p.getAuthorId(), null, null, 1)
                : author
        );
    }
}
