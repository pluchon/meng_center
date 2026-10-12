package com.pluchon.meng.im.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.im.controller.ImVO;
import com.pluchon.meng.im.entity.ImRows;
import com.pluchon.meng.im.mapper.ImMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// 私信图片只允许上传者或可见消息的会话成员读取
@Service
public class PrivateImageService {

    @Autowired
    private ImMapper mapper;

    @Autowired
    private ImageStorage storage;

    @Autowired
    private ImRateLimit limits;

    @Transactional(rollbackFor = Exception.class)
    public ImVO.Image upload(MultipartFile file) throws IOException {
        long user = ImService.user();
        if (
            file.isEmpty() || file.getSize() > ImageStorage.MAX_BYTES
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        limits.image(user);
        var saved = storage.save(file.getBytes());
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) storage.delete(
                        saved.fileName(),
                        saved.thumbName()
                    );
                }
            }
        );
        try {
            var row = new ImRows.Image();
            row.setUploaderId(user);
            row.setFileName(saved.fileName());
            row.setThumbName(saved.thumbName());
            row.setWidth(saved.width());
            row.setHeight(saved.height());
            row.setBytes(file.getSize());
            mapper.insertImage(row);
            return new ImVO.Image(row.getId(), saved.width(), saved.height());
        } catch (RuntimeException failure) {
            storage.delete(saved.fileName(), saved.thumbName());
            throw failure;
        }
    }

    public Path read(long id, boolean thumb) {
        long user = ImService.user();
        var row = mapper.image(id);
        if (row == null) missing();
        if (row.getMessageId() == null) {
            if (row.getUploaderId() != user) missing();
        } else {
            var message = mapper.message(row.getMessageId());
            var member =
                message == null
                    ? null
                    : mapper.member(message.getConversationId(), user);
            if (
                member == null ||
                member.getHiddenState() ||
                message.getId() <= member.getClearedMessageId()
            ) missing();
        }
        return storage.path(thumb ? row.getThumbName() : row.getFileName());
    }

    private static void missing() {
        throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }
}
