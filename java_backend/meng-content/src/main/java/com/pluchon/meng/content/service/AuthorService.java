package com.pluchon.meng.content.service;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.client.UserClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 帖子和评论作者的批量公开资料查询
@Service
public class AuthorService {

    // 用户公开资料查询客户端
    @Autowired
    private UserClient users;

    // 按用户编号批量查询公开资料
    public Map<Long, PublicUserVO> batch(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            var response = users.users(ids);
            if (!"OK".equals(response.code()) || response.data() == null) return Map.of();
            var result = new HashMap<Long, PublicUserVO>();
            for (var user : response.data()) result.put(user.id(), user);
            return result;
        } catch (RuntimeException failure) {
            return Map.of();
        }
    }
}
