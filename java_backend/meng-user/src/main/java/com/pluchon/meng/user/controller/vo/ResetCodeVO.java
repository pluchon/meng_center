package com.pluchon.meng.user.controller.vo;

// 发码结果：短信通道没开通、走模拟发码时带上验证码给页面显示，真的发了短信就是空
public record ResetCodeVO(String mockCode) {}
