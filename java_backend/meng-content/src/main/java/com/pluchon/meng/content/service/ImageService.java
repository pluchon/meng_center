package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.mapper.PostImageMapper;
import java.io.IOException;
import java.nio.file.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageStorage storage;
    private final PostImageMapper images;
    private final PostConverter converter;
    private final ContentLimitService limits;

    @Transactional
    public ImageVO upload(MultipartFile file) throws IOException {
        long user = UserContext.require().userId();
        if (
            file.isEmpty() || file.getSize() > ImageStorage.MAX_BYTES
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        limits.upload(user);
        var stored = storage.save(file.getBytes());
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.delete(
                        stored.fileName(),
                        stored.thumbName()
                    );
                }
            }
        );
        var image = new PostImageEntity();
        image.setUploaderId(user);
        image.setFileName(stored.fileName());
        image.setThumbName(stored.thumbName());
        image.setWidth(stored.width());
        image.setHeight(stored.height());
        images.insert(image);
        return converter.image(image);
    }

    public Resource read(String name) {
        UserContext.require();
        var path = storage.path(name);
        var image = images.selectOne(
            Wrappers.<PostImageEntity>lambdaQuery().and(q ->
                q
                    .eq(PostImageEntity::getFileName, name)
                    .or()
                    .eq(PostImageEntity::getThumbName, name)
            )
        );
        if (
            image == null || !Files.isRegularFile(path)
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return new FileSystemResource(path);
    }
}
