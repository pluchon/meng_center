package com.pluchon.meng.content.client;

import com.pluchon.meng.api.user.contract.UserQueryContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "meng-user")
public interface UserClient extends UserQueryContract {}
