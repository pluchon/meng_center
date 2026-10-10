package com.pluchon.meng.content.controller.vo;

import java.time.LocalDateTime;

public record DraftSummaryVO(
    long id,
    String title,
    String summary,
    LocalDateTime updateTime
) {}
