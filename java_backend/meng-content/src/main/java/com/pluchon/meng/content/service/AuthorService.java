package com.pluchon.meng.content.service;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.client.UserClient;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorService {

    private final UserClient users;

    public Map<Long, PublicUserVO> batch(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            var response = users.users(ids);
            if (
                !"OK".equals(response.code()) || response.data() == null
            ) return Map.of();
            var result = new HashMap<Long, PublicUserVO>();
            for (var user : response.data()) result.put(user.id(), user);
            return result;
        } catch (RuntimeException failure) {
            return Map.of();
        }
    }
}
