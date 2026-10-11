package com.pluchon.meng.content.converter;

import com.pluchon.meng.content.controller.vo.CreatorPostVO;
import com.pluchon.meng.content.controller.vo.DraftSummaryVO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.entity.PostDraftEntity;
import com.pluchon.meng.content.entity.PostEntity;
import java.util.List;
import org.springframework.stereotype.Component;

// 创作中心列表的数据转换，不查询作者资料
@Component
public class CreatorConverter {

    // 已发布的条目只携带封面和互动计数
    public CreatorPostVO post(PostEntity p, ImageVO cover, long favorites) {
        return new CreatorPostVO(
            p.getId(),
            p.getCategory(),
            p.getTitle(),
            p.getSummary(),
            cover,
            p.getCreateTime(),
            p.getViewCount(),
            p.getLikeCount(),
            p.getCommentCount(),
            favorites
        );
    }

    // 草稿保留原始配图顺序，列表最多显示五张
    public DraftSummaryVO draft(
        PostDraftEntity d,
        String summary,
        List<ImageVO> images
    ) {
        return new DraftSummaryVO(
            d.getId(),
            d.getCategory(),
            d.getTitle(),
            summary,
            images.stream().limit(5).toList(),
            d.getImageIds().size(),
            d.getUpdateTime()
        );
    }
}
