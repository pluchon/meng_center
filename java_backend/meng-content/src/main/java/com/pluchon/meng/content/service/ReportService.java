package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.dto.CreateReportDTO;
import com.pluchon.meng.content.entity.*;
import com.pluchon.meng.content.entity.enums.ReportStatus;
import com.pluchon.meng.content.entity.enums.ReportTarget;
import com.pluchon.meng.content.mapper.*;
import com.pluchon.meng.content.validation.ContentErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportMapper reports;
    private final PostMapper posts;
    private final CommentService comments;
    private final ContentLimitService limits;

    @Transactional
    public void create(CreateReportDTO dto) {
        long user = UserContext.require().userId(), author;
        if (dto.targetType() == ReportTarget.POST) {
            var p = posts.lock(dto.targetId());
            if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            author = p.getAuthorId();
        } else {
            var c = comments.require(dto.targetId());
            posts.lock(c.getPostId());
            author = comments.require(dto.targetId()).getAuthorId();
        }
        if (author == user) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (reports.selectCount(
                        Wrappers.<ReportEntity>lambdaQuery()
                                .eq(ReportEntity::getReporterId, user)
                                .eq(ReportEntity::getTargetType, dto.targetType())
                                .eq(ReportEntity::getTargetId, dto.targetId()))
                > 0) throw new BusinessException(ContentErrorCode.REPORT_DUPLICATE);
        limits.report(user);
        var r = new ReportEntity();
        r.setReporterId(user);
        r.setTargetType(dto.targetType());
        r.setTargetId(dto.targetId());
        r.setReason(dto.reason());
        r.setNote(dto.note());
        r.setStatus(ReportStatus.PENDING);
        try {
            reports.insert(r);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ContentErrorCode.REPORT_DUPLICATE);
        }
    }
}
