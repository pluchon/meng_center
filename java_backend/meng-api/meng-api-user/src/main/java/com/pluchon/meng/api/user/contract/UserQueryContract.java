package com.pluchon.meng.api.user.contract;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.response.ApiResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface UserQueryContract {
    String PATH = "/user/internal/users";

    @GetMapping(PATH)
    ApiResponse<List<PublicUserVO>> users(@RequestParam("ids") @NotEmpty @Size(max = 100) List<@Positive Long> ids);
}
