package com.pluchon.meng.im.service;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.im.client.UserClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 批量公开资料查询，发送时必须确认收件人正常
@Service
public class PeerService {

    @Autowired
    private UserClient users;

    public Map<Long, PublicUserVO> batch(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            return fetch(ids);
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }

    private Map<Long, PublicUserVO> fetch(List<Long> ids) {
        var response = users.users(ids.stream().distinct().toList());
        if (
            !"OK".equals(response.code()) || response.data() == null
        ) throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        var result = new HashMap<Long, PublicUserVO>();
        response.data().forEach(user -> result.put(user.id(), user));
        return result;
    }

    public PublicUserVO require(long id) {
        var peer = fetch(List.of(id)).get(id);
        if (peer == null) throw new BusinessException(ImError.PEER_NOT_FOUND);
        return peer;
    }

    public PublicUserVO placeholder(long id, Map<Long, PublicUserVO> profiles) {
        return profiles.getOrDefault(id, new PublicUserVO(id, null, null, 0));
    }
}
