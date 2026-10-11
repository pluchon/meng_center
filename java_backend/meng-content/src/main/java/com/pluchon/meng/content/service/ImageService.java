package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.converter.CommentConverter;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.entity.CommentImageEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.mapper.CommentImageMapper;
import com.pluchon.meng.content.mapper.PostImageMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;

// 配图上传、归属记录和资源读取
@Service
public class ImageService {

    // 评论配图数据存取
    @Autowired
    private CommentImageMapper commentImages;

    // 评论配图展示转换
    @Autowired
    private CommentConverter commentConverter;

    // 评论数据存取
    @Autowired
    private CommentService comments;

    // 配图磁盘存储
    @Autowired
    private ImageStorage storage;

    // 帖子配图数据存取
    @Autowired
    private PostImageMapper images;

    // 展示数据和实体转换
    @Autowired
    private PostConverter converter;

    // 内容操作频率和配额校验
    @Autowired
    private ContentLimitService limits;

    // 校验上传配额并保存帖子配图
    @Transactional(rollbackFor = Exception.class)
    public ImageVO upload(MultipartFile file) throws IOException {
        return upload(file, false);
    }

    // 校验上传配额并保存评论配图
    @Transactional(rollbackFor = Exception.class)
    public ImageVO uploadComment(MultipartFile file) throws IOException {
        return upload(file, true);
    }

    // 校验上传配额并保存帖子配图
    private ImageVO upload(MultipartFile file, boolean comment) throws IOException {
        long user = UserContext.require().userId();
        if (file.isEmpty() || file.getSize() > ImageStorage.MAX_BYTES)
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (comment) limits.commentUpload(user);
        else limits.upload(user);
        var stored = storage.save(file.getBytes());
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    // 事务回滚后清除已写入的磁盘文件
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

    // 校验资源名并读取图片
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
