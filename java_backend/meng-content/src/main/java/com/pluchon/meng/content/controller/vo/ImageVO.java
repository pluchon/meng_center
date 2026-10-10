package com.pluchon.meng.content.controller.vo;

public record ImageVO(
    long id,
    String url,
    String thumbUrl,
    int width,
    int height
) {}
