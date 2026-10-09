package com.pluchon.meng.api.user.vo;

// 可以给其他服务展示的公开资料，不含任何联系方式
public record PublicUserVO(long id, String nickname, String avatar, int level) {}
