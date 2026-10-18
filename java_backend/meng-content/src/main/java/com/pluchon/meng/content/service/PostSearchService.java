package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.vo.HighlightSegmentVO;
import com.pluchon.meng.content.controller.vo.SearchPostVO;
import com.pluchon.meng.content.converter.SearchHighlight;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.mapper.PostMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 帖子搜索、降级查询与展示资料组装
@Service
public class PostSearchService {

    // 帖子搜索与索引维护
    @Autowired
    private PostSearchIndex index;

    // 帖子数据存取
    @Autowired
    private PostMapper posts;

    // 帖子流展示数据组装
    @Autowired
    private PostService feed;

    // 正文摘要
    @Autowired
    private PostSummary summary;

    // 内容操作频率和配额校验
    @Autowired
    private ContentLimitService limits;

    // 校验搜索边界并分页组装结果，不可用时降级
    public PageResult<SearchPostVO> search(
        String input,
        PostCategory category,
        PostSort sort,
        long page,
        long size
    ) {
        return search(input, category, sort, page, size, true);
    }

    // 语义搜索已完成准入检查，降级时不重复扣频率额度。
    public PageResult<SearchPostVO> search(
        String input,
        PostCategory category,
        PostSort sort,
        long page,
        long size,
        boolean checkLimit
    ) {
        String q = input.trim();
        int length = q.codePointCount(0, q.length());
        if (
            length < 1 || length > 50 || page < 1 || size < 1 || size > 50 || page > 10000 / size
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (checkLimit) limits.search(UserContext.require().userId());
        var matches = index.search(q, category, sort, page, size);
        List<PostEntity> rows;
        long total;
        var hitMap = new HashMap<Long, PostSearchIndex.Hit>();
        if (matches == null || matches.total() == 0) {
            var query = Wrappers.<PostEntity>lambdaQuery()
                .eq(PostEntity::getReviewState, "NORMAL")
                .eq(category != null, PostEntity::getCategory, category)
                .apply(
                    "title LIKE {0} ESCAPE '!'",
                    "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
                );
            if (sort == PostSort.HOT) query.orderByDesc(PostEntity::getHotScore);
            query.orderByDesc(PostEntity::getId);
            var result = posts.selectPage(new Page<>(page, size), query);
            rows = result.getRecords();
            total = result.getTotal();
        } else {
            if (matches.hits().isEmpty()) return new PageResult<>(
                List.of(),
                matches.total(),
                page,
                size
            );
            var ids = matches.hits().stream().map(PostSearchIndex.Hit::id).toList();
            matches.hits().forEach(hit -> hitMap.put(hit.id(), hit));
            var query = Wrappers.<PostEntity>lambdaQuery().in(PostEntity::getId, ids);
            if (sort == PostSort.HOT) {
                query.orderByDesc(PostEntity::getHotScore).orderByDesc(PostEntity::getId);
                var result = posts.selectPage(new Page<>(page, size), query);
                rows = result.getRecords();
                total = result.getTotal();
            } else {
                var byId = new HashMap<Long, PostEntity>();
                posts.selectList(query).forEach(p -> byId.put(p.getId(), p));
                rows = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
                total = matches.total();
            }
        }
        var entities = new HashMap<Long, PostEntity>();
        rows.forEach(row -> entities.put(row.getId(), row));
        var records = new ArrayList<SearchPostVO>();
        for (var item : feed.hydrate(rows)) {
            var hit = hitMap.get(item.id());
            var title =
                hit == null ||
                hit.title().isEmpty() ||
                !SearchHighlight.text(hit.title()).equals(item.title())
                    ? SearchHighlight.literal(item.title(), q)
                    : hit.title();
            var snippet =
                hit == null ||
                hit.content().isEmpty() ||
                !summary
                    .plain(entities.get(item.id()).getContent())
                    .contains(SearchHighlight.text(hit.content()))
                    ? List.of(new HighlightSegmentVO(item.summary(), false))
                    : hit.content();
            records.add(
                new SearchPostVO(
                    item.id(),
                    item.category(),
                    item.title(),
                    item.summary(),
                    item.images(),
                    item.imageCount(),
                    item.viewCount(),
                    item.likeCount(),
                    item.commentCount(),
                    item.favoriteCount(),
                    item.liked(),
                    item.favorited(),
                    item.ipRegion(),
                    item.createTime(),
                    item.author(),
                    title,
                    snippet
                )
            );
        }
        return new PageResult<>(records, total, page, size);
    }
}
