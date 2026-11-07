package com.pluchon.meng.user.mq;

// 免审核注册已写库的进程内事件，事务提交后发 user.approved
public record RegistrationActivated(long userId) {}
