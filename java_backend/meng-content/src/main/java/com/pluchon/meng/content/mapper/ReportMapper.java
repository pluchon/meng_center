package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.ReportEntity;

import org.apache.ibatis.annotations.Mapper;

// 举报记录持久化
@Mapper
public interface ReportMapper extends BaseMapper<ReportEntity> {}
