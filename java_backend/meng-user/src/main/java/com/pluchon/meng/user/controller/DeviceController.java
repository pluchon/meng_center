package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.user.service.DeviceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/me/sessions")
public class DeviceController {

    @Autowired
    private DeviceService devices;

    @GetMapping
    public ApiResponse<List<DeviceService.Device>> list() {
        return ApiResponse.success(devices.list());
    }

    @DeleteMapping("/others")
    public ApiResponse<Void> others() {
        devices.revokeOthers();
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(@PathVariable String id) {
        devices.revoke(id);
        return ApiResponse.success(null);
    }
}
