package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.converter.CommentConverter;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.CommentImageEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.mapper.CommentImageMapper;
import com.pluchon.meng.content.mapper.PostImageMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final CommentImageMapper commentImages;
    private final CommentConverter commentConverter;
    private final CommentService comments;
    private final ImageStorage storage;
    private final PostImageMapper images;
    private final PostConverter converter;
    private final ContentLimitService limits;

    @Transactional
    public ImageVO upload(MultipartFile file) throws IOException {
        return upload(file, false);
    }

    @Transactional
    public ImageVO uploadComment(MultipartFile file) throws IOException {
        return upload(file, true);
    }

    private ImageVO upload(MultipartFile file, boolean comment) throws IOException {
        long user = UserContext.require().userId();
        if (file.isEmpty() || file.getSize() > ImageStorage.MAX_BYTES)
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (comment) limits.commentUpload(user);
        else limits.upload(user);
        var stored = storage.save(file.getBytes());
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED)
                            storage.delete(stored.fileName(), stored.thumbName());
                    }
                });
        if (comment) {
            var image = new CommentImageEntity();
            image.setUploaderId(user);
            image.setFileName(stored.fileName());
            image.setThumbName(stored.thumbName());
            image.setWidth(stored.width());
            image.setHeight(stored.height());
            commentImages.insert(image);
            return commentConverter.image(image);
        }
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
        var image =
                images.selectOne(
                        Wrappers.<PostImageEntity>lambdaQuery()
                                .and(
                                        q ->
                                                q.eq(PostImageEntity::getFileName, name)
                                                        .or()
                                                        .eq(PostImageEntity::getThumbName, name)));
        if (image == null) {
            var commentImage =
                    commentImages.selectOne(
                            Wrappers.<CommentImageEntity>lambdaQuery()
                                    .and(
                                            q ->
                                                    q.eq(CommentImageEntity::getFileName, name)
                                                            .or()
                                                            .eq(
                                                                    CommentImageEntity
                                                                            ::getThumbName,
                                                                    name)));
            if (commentImage == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            if (commentImage.getCommentId() != null) comments.require(commentImage.getCommentId());
        }
        if (!Files.isRegularFile(path)) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return new FileSystemResource(path);
    }
}
