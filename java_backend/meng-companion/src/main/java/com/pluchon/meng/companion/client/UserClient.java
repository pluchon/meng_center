package com.pluchon.meng.companion.client;

import com.pluchon.meng.api.user.contract.UserQueryContract;
import org.springframework.cloud.openfeign.FeignClient;

// 昵称查询沿用公开资料契约，不读取联系方式
@FeignClient(name="meng-user", contextId="companionUser")
public interface UserClient extends UserQueryContract {}
