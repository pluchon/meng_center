package com.pluchon.meng.content.service;

import com.pluchon.meng.api.content.contract.CompanionPostContract;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.content.mapper.CompanionPostMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 萌萌工具请求不会触发作者查询、推荐或其他同步调用
@Service
public class CompanionPostService {

    @Autowired
    private CompanionPostMapper mapper;

    @Autowired
    private PostVectorIndex vectors;

    public List<CompanionPostContract.Post> vectorSearch(
        CompanionPostContract.VectorQuery query
    ) {
        if (
            query == null ||
            query.vector() == null ||
            query.vector().size() != 1024 ||
            query.size() < 1 ||
            query.size() > 20 ||
            query
                .vector()
                .stream()
                .anyMatch(value -> value == null || !Float.isFinite(value))
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        try {
            var ids = vectors
                .nearest(query.vector(), null, 0, query.size())
                .stream()
                .map(hit -> hit.document().id())
                .toList();
            if (ids.isEmpty()) return List.of();
            Map<Long, CompanionPostContract.Post> visible = mapper
                .byIds(ids)
                .stream()
                .collect(
                    Collectors.toMap(
                        CompanionPostContract.Post::id,
                        Function.identity()
                    )
                );
            return ids
                .stream()
                .map(visible::get)
                .filter(Objects::nonNull)
                .toList();
        } catch (java.io.IOException error) {
            throw new BusinessException(
                com.pluchon.meng.content.entity.enums.AiErrorCode.UNAVAILABLE
            );
        }
    }

    public List<CompanionPostContract.Post> recent(long user, int size) {
        validate(size);
        if (user < 1) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        return mapper.recent(user, size);
    }

    private void validate(int size) {
        if (size < 1 || size > 5) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
    }
}
