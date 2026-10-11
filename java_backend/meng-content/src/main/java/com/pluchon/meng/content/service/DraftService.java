package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.dto.SaveDraftDTO;
import com.pluchon.meng.content.controller.vo.DraftSummaryVO;
import com.pluchon.meng.content.controller.vo.DraftVO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.converter.CreatorConverter;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.PostDraftEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.mapper.PostDraftMapper;
import com.pluchon.meng.content.mapper.PostImageMapper;
import com.pluchon.meng.content.validation.PostRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 草稿查询、保存与发布归属校验
@Service
public class DraftService {

    // 创作中心列表转换
    @Autowired
    private CreatorConverter creatorConverter;

    // 草稿数据存取
    @Autowired
    private PostDraftMapper drafts;

    // 帖子配图数据存取
    @Autowired
    private PostImageMapper images;

    // 展示数据和实体转换
    @Autowired
    private PostConverter converter;

    // 正文摘要
    @Autowired
    private PostSummary summary;

    // 内容操作频率和配额校验
    @Autowired
    private ContentLimitService limits;

    // 读取草稿并校验当前用户归属
    public PostDraftEntity owned(long id) {
        var draft = drafts.selectById(id);
        if (draft == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        if (
            draft.getAuthorId() != UserContext.require().userId()
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        return draft;
    }

    // 查询当前用户的草稿摘要
    public List<DraftSummaryVO> list() {
        var list = drafts.selectList(
            Wrappers.<PostDraftEntity>lambdaQuery()
                .eq(
                    PostDraftEntity::getAuthorId,
                    UserContext.require().userId()
                )
                .orderByDesc(PostDraftEntity::getUpdateTime)
                .orderByDesc(PostDraftEntity::getId)
        );
        var ids = list
            .stream()
            .flatMap(d -> d.getImageIds().stream().limit(5))
            .distinct()
            .toList();
        var imageMap = new HashMap<Long, ImageVO>();
        if (!ids.isEmpty()) images
            .selectList(
                Wrappers.<PostImageEntity>lambdaQuery()
                    .in(PostImageEntity::getId, ids)
                    .eq(
                        PostImageEntity::getUploaderId,
                        UserContext.require().userId()
                    )
            )
            .forEach(i -> imageMap.put(i.getId(), converter.image(i)));
        return list
            .stream()
            .map(d ->
                creatorConverter.draft(
                    d,
                    summary.summarize(d.getContent()),
                    d
                        .getImageIds()
                        .stream()
                        .limit(5)
                        .map(imageMap::get)
                        .filter(Objects::nonNull)
                        .toList()
                )
            )
            .toList();
    }

    // 组装可恢复编辑的草稿内容
    public DraftVO get(long id) {
        var d = owned(id);
        return new DraftVO(
            d.getId(),
            d.getCategory(),
            d.getTitle(),
            d.getContent(),
            d.getImageIds().stream().map(this::image).toList(),
            d.getCoverImageId() == null ? null : image(d.getCoverImageId()),
            d.getTags()
        );
    }

    // 校验草稿配图归属并转换展示信息
    private ImageVO image(long id) {
        var img = images.selectById(id);
        if (
            img == null || img.getUploaderId() != UserContext.require().userId()
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        return converter.image(img);
    }

    // 校验配额和配图后保存草稿
    @Transactional(rollbackFor = Exception.class)
    public long save(Long id, SaveDraftDTO dto) {
        long author = UserContext.require().userId();
        limits.draft(author);
        drafts.lockAuthorDrafts(author);
        var draft = id == null ? new PostDraftEntity() : owned(id);
        if (
            dto.title().isBlank() && dto.content().isBlank()
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        PostRules.images(dto.imageIds(), dto.coverImageId());
        var normalized = PostRules.tags(dto.tags());
        var ids = new ArrayList<>(dto.imageIds());
        if (dto.coverImageId() != null) ids.add(dto.coverImageId());
        for (long imageId : ids) {
            var img = images.selectById(imageId);
            if (
                img == null ||
                img.getUploaderId() != author ||
                img.getPostId() != null
            ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (
            id == null &&
            drafts.selectCount(
                Wrappers.<PostDraftEntity>lambdaQuery().eq(
                    PostDraftEntity::getAuthorId,
                    author
                )
            ) >= PostRules.DRAFTS_MAX
        ) throw new BusinessException(CommonErrorCode.CONFLICT);
        draft.setAuthorId(author);
        converter.applyDraft(dto, draft);
        draft.setTags(normalized);
        if (id == null) drafts.insert(draft);
        else if (
            drafts.updateDraftContent(draft) != 1
        ) throw new BusinessException(CommonErrorCode.CONFLICT);
        return draft.getId();
    }

    // 锁定作者草稿后删除指定草稿
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        drafts.lockAuthorDrafts(UserContext.require().userId());
        owned(id);
        drafts.deleteById(id);
    }

    // 发布前锁定并校验关联草稿
    public void lockForPublication(Long id) {
        if (id != null) {
            drafts.lockAuthorDrafts(UserContext.require().userId());
            owned(id);
        }
    }

    // 发布成功后删除关联草稿
    public void published(Long id) {
        if (
            id != null && drafts.deleteById(id) != 1
        ) throw new BusinessException(CommonErrorCode.CONFLICT);
    }
}
