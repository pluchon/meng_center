package com.pluchon.meng.content.client;

import com.pluchon.meng.api.user.contract.UserQueryContract;

import org.springframework.cloud.openfeign.FeignClient;

// 批量读取用户公开资料的内部客户端
@FeignClient(name = "meng-user")
public interface UserClient extends UserQueryContract {}
