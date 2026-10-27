package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// 私密对话图片独立鉴权，不进入公开内容索引
@Service
public class PrivateImages {
    @Autowired
    private PrivateImageStorage storage;
    @Autowired
    private CompanionMapper mapper;
    @Autowired
    private StringRedisTemplate redis;

    @Transactional(rollbackFor=Exception.class)
    public Map<String, Object> upload(long user, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 10_000_000) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        String key = "meng:companion:upload:" + user;
        Long count = redis.opsForValue().increment(key);
        if (Long.valueOf(1).equals(count)) redis.expire(key, java.time.Duration.ofMinutes(1));
        if (count == null || count > 10) throw new BusinessException(CompanionError.COMPANION_QUOTA_EXCEEDED);
        var saved = storage.save(file.getBytes());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) { if (status != STATUS_COMMITTED) storage.delete(saved.name()); }
        });
        mapper.insertImage(user, saved.name());
        long id = mapper.imageId(user, saved.name());
        return Map.of("id", id, "url", "/companion/images/" + id);
    }

    public Path read(long user, long id) {
        String name = mapper.image(id, user);
        if (name == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return storage.path(name);
    }
}
