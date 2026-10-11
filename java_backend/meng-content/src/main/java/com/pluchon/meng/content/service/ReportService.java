package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.dto.CreateReportDTO;
import com.pluchon.meng.content.converter.ReportConverter;
import com.pluchon.meng.content.entity.ReportEntity;
import com.pluchon.meng.content.entity.enums.ReportStatus;
import com.pluchon.meng.content.entity.enums.ReportTarget;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.mapper.ReportMapper;
import com.pluchon.meng.content.validation.ContentErrorCode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 举报创建、目标校验与重复提交控制
@Service
public class ReportService {

    // 展示数据和实体转换
    @Autowired
    private ReportConverter converter;

    // 举报数据存取
    @Autowired
    private ReportMapper reports;

    // 帖子数据存取
    @Autowired
    private PostMapper posts;

    // 评论数据存取
    @Autowired
    private CommentService comments;

    // 内容操作频率和配额校验
    @Autowired
    private ContentLimitService limits;

    // 校验目标与配额后创建去重举报
    @Transactional(rollbackFor = Exception.class)
    public void create(CreateReportDTO dto) {
        long user = UserContext.require().userId();
        long author;
        if (dto.targetType() == ReportTarget.POST) {
            var p = posts.selectByIdForUpdate(dto.targetId());
            if (p == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            author = p.getAuthorId();
        } else {
            var c = comments.require(dto.targetId());
            posts.selectByIdForUpdate(c.getPostId());
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
        var r = converter.create(dto);
        r.setReporterId(user);
        r.setStatus(ReportStatus.PENDING);
        try {
            reports.insert(r);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ContentErrorCode.REPORT_DUPLICATE);
        }
    }
}
