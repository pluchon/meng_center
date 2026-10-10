package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.dto.SaveDraftDTO;
import com.pluchon.meng.content.controller.vo.*;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.*;
import com.pluchon.meng.content.mapper.*;
import com.pluchon.meng.content.validation.PostRules;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DraftService {

    private final PostDraftMapper drafts;
    private final PostImageMapper images;
    private final PostConverter converter;
    private final PostSummary summary;
    private final ContentLimitService limits;

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

    public List<DraftSummaryVO> list() {
        return drafts
            .selectList(
                Wrappers.<PostDraftEntity>lambdaQuery()
                    .eq(
                        PostDraftEntity::getAuthorId,
                        UserContext.require().userId()
                    )
                    .orderByDesc(PostDraftEntity::getUpdateTime)
                    .orderByDesc(PostDraftEntity::getId)
            )
            .stream()
            .map(d ->
                new DraftSummaryVO(
                    d.getId(),
                    d.getTitle(),
                    summary.summarize(d.getContent()),
                    d.getUpdateTime()
                )
            )
            .toList();
    }

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

    private ImageVO image(long id) {
        var img = images.selectById(id);
        if (
            img == null || img.getUploaderId() != UserContext.require().userId()
        ) throw new BusinessException(CommonErrorCode.FORBIDDEN);
        return converter.image(img);
    }

    @Transactional
    public long save(Long id, SaveDraftDTO dto) {
        long author = UserContext.require().userId();
        limits.draft(author);
        drafts.lockOwner(author);
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
        draft.setCategory(dto.category());
        draft.setTitle(dto.title());
        draft.setContent(dto.content());
        draft.setImageIds(dto.imageIds());
        draft.setCoverImageId(dto.coverImageId());
        draft.setTags(normalized);
        if (id == null) drafts.insert(draft);
        else if (drafts.save(draft) != 1) throw new BusinessException(
            CommonErrorCode.CONFLICT
        );
        return draft.getId();
    }

    @Transactional
    public void delete(long id) {
        drafts.lockOwner(UserContext.require().userId());
        owned(id);
        drafts.deleteById(id);
    }

    public void lockForPublication(Long id) {
        if (id != null) {
            drafts.lockOwner(UserContext.require().userId());
            owned(id);
        }
    }

    public void published(Long id) {
        if (
            id != null && drafts.deleteById(id) != 1
        ) throw new BusinessException(CommonErrorCode.CONFLICT);
    }
}
