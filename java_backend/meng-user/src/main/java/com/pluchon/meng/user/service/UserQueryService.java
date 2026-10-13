package com.pluchon.meng.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.converter.UserConverter;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.security.AuthorizationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 账号查询：当前用户、审核列表和给其他服务的批量公开资料
@Service
public final class UserQueryService {

    @Autowired
    private UserMapper mapper;

    @Autowired
    private UserConverter converter;

    @Autowired
    private AuthorizationService authorization;

    // 当前登录用户的资料
    public UserVO me() {
        return converter.toVO(authorization.current());
    }

    // 审核列表，只列普通用户，新申请在前；分页和排序都由数据库完成
    public PageResult<UserVO> registrations(
        long page,
        long size,
        UserStatus status
    ) {
        authorization.requireAdmin();
        Page<UserEntity> result = mapper.selectPage(
            new Page<>(page, size),
            Wrappers.<UserEntity>lambdaQuery()
                .eq(status != null, UserEntity::getStatus, status)
                .eq(UserEntity::getRole, UserRole.USER)
                .orderByDesc(UserEntity::getCreateTime)
                .orderByDesc(UserEntity::getId)
        );
        return new PageResult<>(
            result.getRecords().stream().map(converter::toVO).toList(),
            result.getTotal(),
            page,
            size
        );
    }

    // 内部接口：按编号批量取公开资料
    public List<PublicUserVO> users(List<Long> ids) {
        return mapper
            .selectByIds(ids)
            .stream()
            .filter(user -> user.getStatus() == UserStatus.NORMAL)
            .map(converter::toPublicVO)
            .toList();
    }
}
