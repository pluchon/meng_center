package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.content.entity.enums.PostCategory;
import java.util.List;

public record DraftVO(
    long id,
    PostCategory category,
    String title,
    String content,
    List<ImageVO> images,
    ImageVO cover,
    List<String> tags
) {}
