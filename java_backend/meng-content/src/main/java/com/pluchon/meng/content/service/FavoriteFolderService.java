package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.entity.FavoriteFolder;
import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.*;
import com.pluchon.meng.content.validation.ContentErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FavoriteFolderService {

    @Autowired
    private FavoriteFolderMapper folders;

    @Autowired
    private PostRelationMapper relations;

    @Autowired
    private PostMapper posts;

    @Autowired
    private AuthorService authors;

    @Autowired
    private ImageStorage storage;

    @Autowired
    private ContentLimitService limits;

    @Autowired
    private AuthorStatisticsService statistics;

    @Autowired
    private ContentProperties properties;

    @Autowired
    private ApplicationEventPublisher events;

    public record FolderVO(
        long id,
        boolean isDefault,
        String name,
        String description,
        String cover,
        long count,
        LocalDateTime latestAt
    ) {}

    public record SavedPost(
        long id,
        String category,
        String title,
        String cover,
        PublicUserVO author,
        LocalDateTime savedAt
    ) {}

    public record Counts(long favorites, long likes) {}

    private long user() {
        return UserContext.require().userId();
    }

    private void ensure() {
        folders.ensure(user());
    }

    public FavoriteFolder require(long id) {
        var folder = folders.selectById(id);
        if (folder == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        if (folder.getUserId() != user()) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        return folder;
    }

    public FavoriteFolder lockSelection(Long id) {
        ensure();
        var base = folders.lockDefault(user());
        var folder = id == null ? base : folders.lock(id);
        if (folder == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        if (folder.getUserId() != user()) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        return folder;
    }

    private long count() {
        return folders.selectCount(
            Wrappers.<FavoriteFolder>lambdaQuery().eq(
                FavoriteFolder::getUserId,
                user()
            )
        );
    }

    @Transactional
    public PageResult<FolderVO> list(long page, long size) {
        ensure();
        return new PageResult<>(
            folders
                .folders(user(), (page - 1) * size, size)
                .stream()
                .map(r ->
                    new FolderVO(
                        r.getId(),
                        r.getDefaultOwnerId() != null,
                        r.getName(),
                        r.getDescription(),
                        r.getCoverName() != null
                            ? "/content/folder-covers/" + r.getCoverName()
                            : r.getPostCover() != null
                              ? "/content/files/" + r.getPostCover()
                              : null,
                        r.getCount(),
                        r.getLatestAt()
                    )
                )
                .toList(),
            count(),
            page,
            size
        );
    }

    private void validate(String name, String description, Long id) {
        if (name == null || description == null) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        String n = name.strip();
        if (
            n.isEmpty() ||
            n.codePointCount(0, n.length()) > 12 ||
            description.codePointCount(0, description.length()) > 50 ||
            n.equals("默认收藏夹")
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (
            folders.exists(
                Wrappers.<FavoriteFolder>lambdaQuery()
                    .eq(FavoriteFolder::getUserId, user())
                    .eq(FavoriteFolder::getName, n)
                    .ne(id != null, FavoriteFolder::getId, id)
            )
        ) throw new BusinessException(ContentErrorCode.FOLDER_NAME_TAKEN);
    }

    @Transactional(rollbackFor = Exception.class)
    public long create(String name, String description, MultipartFile cover)
        throws IOException {
        lockSelection(null);
        if (count() >= 20) throw new BusinessException(
            ContentErrorCode.FOLDER_LIMIT
        );
        validate(name, description, null);
        var folder = new FavoriteFolder();
        folder.setUserId(user());
        folder.setName(name.strip());
        folder.setDescription(description.strip());
        if (cover != null && !cover.isEmpty()) {
            if (
                cover.getSize() > ImageStorage.MAX_BYTES
            ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
            limits.upload(user());
            var image = storage.save(cover.getBytes());
            storage.delete(image.fileName(), image.fileName());
            folder.setCoverName(image.thumbName());
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.delete(
                            image.thumbName(),
                            image.thumbName()
                        );
                    }
                }
            );
        }
        folders.insert(folder);
        return folder.getId();
    }

    @Transactional
    public void update(long id, String name, String description) {
        lockSelection(null);
        var folder = require(id);
        if (folder.getDefaultOwnerId() != null) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        validate(name, description, id);
        folder.setName(name.strip());
        folder.setDescription(description.strip());
        folders.updateById(folder);
    }

    @Transactional
    public void delete(long id) {
        lockSelection(null);
        var folder = require(id);
        if (folder.getDefaultOwnerId() != null) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        for (var relation : folders.relations(id)) {
            var post = posts.selectByIdForUpdate(relation.getPostId());
            if (
                relations.deleteFavorite(relation.getPostId(), user()) == 1 &&
                post != null
            ) {
                statistics.cancel(
                    post.getAuthorId(),
                    CreatorMetric.FAVORITE,
                    relation.getCreateTime()
                );
                long delta = -properties.favoriteWeight();
                posts.incrementHotScore(post.getId(), delta);
                events.publishEvent(
                    new PostReadModelEvent(
                        post.getId(),
                        null,
                        PostReadModelEvent.Kind.SCORE,
                        delta
                    )
                );
            }
        }
        folders.deleteById(id);
        if (
            folder.getCoverName() != null
        ) TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(
                        folder.getCoverName(),
                        folder.getCoverName()
                    );
                }
            }
        );
    }

    public Counts counts() {
        return new Counts(
            folders.favorites(user(), null),
            folders.liked(user())
        );
    }

    public PageResult<SavedPost> saved(Long folder, long page, long size) {
        if (folder != null) require(folder);
        var rows =
            folder == null
                ? folders.likes(user(), (page - 1) * size, size)
                : folders.posts(user(), folder, (page - 1) * size, size);
        var people = authors.batch(
            rows
                .stream()
                .map(FavoriteFolderMapper.PostRow::getAuthorId)
                .distinct()
                .toList()
        );
        return new PageResult<>(
            rows
                .stream()
                .map(r ->
                    new SavedPost(
                        r.getId(),
                        r.getCategory(),
                        r.getTitle(),
                        r.getThumbName() == null
                            ? null
                            : "/content/files/" + r.getThumbName(),
                        people.get(r.getAuthorId()),
                        r.getSavedAt()
                    )
                )
                .toList(),
            folder == null
                ? folders.liked(user())
                : folders.favorites(user(), folder),
            page,
            size
        );
    }

    public Resource cover(String name) {
        var folder = folders.selectOne(
            Wrappers.<FavoriteFolder>lambdaQuery()
                .eq(FavoriteFolder::getUserId, user())
                .eq(FavoriteFolder::getCoverName, name)
        );
        if (folder == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        var path = storage.path(name);
        if (!Files.isRegularFile(path)) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        return new FileSystemResource(path);
    }
}
