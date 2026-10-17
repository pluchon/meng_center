package com.pluchon.meng.content.service;

import com.pluchon.meng.api.content.contract.CompanionPostContract;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.content.mapper.CompanionPostMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 萌萌工具请求不会触发作者查询、推荐或其他同步调用
@Service
public class CompanionPostService {
    @Autowired
    private CompanionPostMapper mapper;

    public List<CompanionPostContract.Post> search(String query, int size) {
        validate(size);
        if (query == null || query.isBlank() || query.length() > 100) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        return mapper.search(query.strip(), size);
    }
    public List<CompanionPostContract.Post> recent(long user, int size) {
        validate(size);
        if (user < 1) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        return mapper.recent(user, size);
    }
    private void validate(int size) { if (size < 1 || size > 5) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT); }
}
