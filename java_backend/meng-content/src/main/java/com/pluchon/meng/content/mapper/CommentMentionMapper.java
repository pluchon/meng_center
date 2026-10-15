package com.pluchon.meng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.content.entity.CommentMentionEntity;

import org.apache.ibatis.annotations.Mapper;

// 评论里被提到的用户的持久化
@Mapper
public interface CommentMentionMapper extends BaseMapper<CommentMentionEntity> {}
