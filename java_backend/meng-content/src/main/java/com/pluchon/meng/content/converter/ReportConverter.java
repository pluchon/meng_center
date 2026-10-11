package com.pluchon.meng.content.converter;

import cn.hutool.core.bean.BeanUtil;

import com.pluchon.meng.content.controller.dto.CreateReportDTO;
import com.pluchon.meng.content.entity.ReportEntity;

import org.springframework.stereotype.Component;

// 举报参数与持久化实体的转换
@Component
public class ReportConverter {

    // 拷贝举报目标、原因与说明，举报人和状态由业务层填写
    public ReportEntity create(CreateReportDTO dto) {
        var report = new ReportEntity();
        BeanUtil.copyProperties(dto, report);
        return report;
    }
}
