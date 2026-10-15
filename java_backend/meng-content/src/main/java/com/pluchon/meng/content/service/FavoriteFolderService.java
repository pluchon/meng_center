package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.entity.FavoriteFolder;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.CreatorMetric;
import com.pluchon.meng.content.entity.enums.FolderColor;
import com.pluchon.meng.content.entity.enums.FolderVisibility;
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

    // 一篇帖子最多放进的收藏夹个数
    private static final int MAX_FOLDERS_PER_POST = 10;

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
        FolderColor color,
        FolderVisibility visibility,
        long count,
        LocalDateTime latestAt
    ) {}

    // 别人主页上的数量：发布的帖子、公开的收藏夹、公开收藏夹里的帖子
    public record PublicCounts(long posts, long folders, long favorites) {}

    // 帖子对当前用户的收藏状态
    public record FavoriteState(boolean favorited, long favoriteCount) {}

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
        return count(user(), null);
    }

    // 某人的收藏夹个数，visibility 为空时不按可见性筛
    private long count(long owner, FolderVisibility visibility) {
        return folders.selectCount(
            Wrappers.<FavoriteFolder>lambdaQuery()
                .eq(FavoriteFolder::getUserId, owner)
                .eq(visibility != null, FavoriteFolder::getVisibility, visibility)
        );
    }

    @Transactional
    public PageResult<FolderVO> list(long page, long size) {
        ensure();
        return folderPage(user(), null, page, size);
    }

    // 别人的主页：只给他公开的收藏夹，不替他建默认收藏夹
    public PageResult<FolderVO> publicList(long owner, long page, long size) {
        return folderPage(owner, FolderVisibility.PUBLIC, page, size);
    }

    private PageResult<FolderVO> folderPage(
        long owner,
        FolderVisibility visibility,
        long page,
        long size
    ) {
        return new PageResult<>(
            folders
                .folders(owner, visibility, (page - 1) * size, size)
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
                        r.getColor(),
                        r.getVisibility(),
                        r.getCount(),
                        r.getLatestAt()
                    )
                )
                .toList(),
            count(owner, visibility),
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
    public long create(
        String name,
        String description,
        FolderColor color,
        FolderVisibility visibility,
        MultipartFile cover
    ) throws IOException {
        lockSelection(null);
        if (count() >= 20) throw new BusinessException(
            ContentErrorCode.FOLDER_LIMIT
        );
        validate(name, description, null);
        var folder = new FavoriteFolder();
        folder.setUserId(user());
        folder.setName(name.strip());
        folder.setDescription(description.strip());
        folder.setColor(color == null ? FolderColor.GOLD : color);
        folder.setVisibility(
            visibility == null ? FolderVisibility.PRIVATE : visibility
        );
        if (cover != null && !cover.isEmpty()) {
            folder.setCoverName(saveCover(cover));
        }
        folders.insert(folder);
        return folder.getId();
    }

    // 保存封面文件，只留缩略图；事务没有提交时把刚存的文件删掉，返回文件名
    private String saveCover(MultipartFile cover) throws IOException {
        if (cover.getSize() > ImageStorage.MAX_BYTES) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        limits.upload(user());
        var image = storage.save(cover.getBytes());
        storage.delete(image.fileName(), image.fileName());
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
        return image.thumbName();
    }

    // 改收藏夹对别人是否可见，默认收藏夹也能改；幂等
    @Transactional(rollbackFor = Exception.class)
    public void changeVisibility(long id, FolderVisibility visibility) {
        lockSelection(null);
        var folder = require(id);
        if (visibility == null) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        folder.setVisibility(visibility);
        folders.updateById(folder);
    }

    // 更换封面：默认收藏夹的封面固定，不能换；旧封面在事务提交后删除
    @Transactional(rollbackFor = Exception.class)
    public void changeCover(long id, MultipartFile cover) throws IOException {
        lockSelection(null);
        var folder = require(id);
        if (folder.getDefaultOwnerId() != null) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        if (cover == null || cover.isEmpty()) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        var previous = folder.getCoverName();
        folder.setCoverName(saveCover(cover));
        folders.updateById(folder);
        if (previous != null) TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(previous, previous);
                }
            }
        );
    }

    @Transactional
    public void update(
        long id,
        String name,
        String description,
        FolderColor color
    ) {
        lockSelection(null);
        var folder = require(id);
        if (folder.getDefaultOwnerId() != null) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
        validate(name, description, id);
        folder.setName(name.strip());
        folder.setDescription(description.strip());
        // 不传颜色表示不改
        if (color != null) folder.setColor(color);
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
                relations.deleteFavorite(relation.getPostId(), user(), id) ==
                    1 &&
                post != null &&
                relations.countFavoriteFolders(post.getId(), user()) == 0
            ) cancelFavorite(post, relation.getCreateTime());
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

    // 把帖子放进收藏夹：幂等，最多放进 10 个，第一次放进才算收藏
    @Transactional(rollbackFor = Exception.class)
    public FavoriteState add(long folderId, long postId) {
        lockSelection(folderId);
        long user = user();
        var post = requirePost(postId);
        long owned = relations.countFavoriteFolders(postId, user);
        if (relations.insertFavorite(postId, user, folderId) == 1) {
            if (owned >= MAX_FOLDERS_PER_POST) throw new BusinessException(
                ContentErrorCode.FAVORITE_FOLDER_LIMIT
            );
            if (owned == 0) recordFavorite(post, user);
        }
        return state(postId, user);
    }

    // 把帖子从收藏夹拿走：幂等，拿走最后一个才算取消收藏
    @Transactional(rollbackFor = Exception.class)
    public FavoriteState remove(long folderId, long postId) {
        lockSelection(folderId);
        long user = user();
        var post = requirePost(postId);
        var created = relations.selectFavoriteCreateTime(postId, user, folderId);
        if (
            relations.deleteFavorite(postId, user, folderId) == 1 &&
            relations.countFavoriteFolders(postId, user) == 0
        ) cancelFavorite(post, created);
        return state(postId, user);
    }

    // 这篇帖子放进了我的哪些收藏夹
    public List<Long> selected(long postId) {
        return relations.selectFavoriteFolderIds(postId, user());
    }

    // 一次性设定这篇帖子放进哪些收藏夹：补上新增的、去掉取消的，只在 0 到 n、n 到 0 时才算收藏和取消
    @Transactional(rollbackFor = Exception.class)
    public FavoriteState replace(long postId, List<Long> folderIds) {
        ensure();
        long user = user();
        folders.lockDefault(user);
        var target = new LinkedHashSet<>(folderIds);
        if (target.size() > MAX_FOLDERS_PER_POST) throw new BusinessException(
            ContentErrorCode.FAVORITE_FOLDER_LIMIT
        );
        if (!target.isEmpty()) {
            long owned = folders.selectCount(
                Wrappers.<FavoriteFolder>lambdaQuery()
                    .eq(FavoriteFolder::getUserId, user)
                    .in(FavoriteFolder::getId, target)
            );
            if (owned != target.size()) throw new BusinessException(
                CommonErrorCode.FORBIDDEN
            );
        }
        var post = requirePost(postId);
        var current = new LinkedHashSet<>(
            relations.selectFavoriteFolderIds(postId, user)
        );
        var created = current.isEmpty()
            ? null
            : relations.selectFavoriteCreateTime(
                  postId,
                  user,
                  current.iterator().next()
              );
        for (var id : current) if (!target.contains(id)) relations.deleteFavorite(
            postId,
            user,
            id
        );
        for (var id : target) if (!current.contains(id)) relations.insertFavorite(
            postId,
            user,
            id
        );
        if (current.isEmpty() && !target.isEmpty()) recordFavorite(post, user);
        else if (!current.isEmpty() && target.isEmpty()) cancelFavorite(
            post,
            created
        );
        return state(postId, user);
    }

    private PostEntity requirePost(long id) {
        var post = posts.selectByIdForUpdate(id);
        if (post == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        return post;
    }

    private FavoriteState state(long postId, long user) {
        var counts = relations.selectFavoriteCounts(List.of(postId));
        return new FavoriteState(
            relations.countFavoriteFolders(postId, user) > 0,
            counts.isEmpty() ? 0 : counts.get(0).count()
        );
    }

    // 某人对这篇帖子的第一行收藏记录：计入统计、加热度、通知作者
    private void recordFavorite(PostEntity post, long user) {
        statistics.record(post.getAuthorId(), CreatorMetric.FAVORITE);
        long delta = properties.favoriteWeight();
        posts.incrementHotScore(post.getId(), delta);
        events.publishEvent(
            new ContentInteractionEvent(
                "post.favorited",
                post.getId(),
                post.getTitle(),
                null,
                null,
                user,
                post.getAuthorId()
            )
        );
        publishScore(post.getId(), delta);
    }

    // 最后一行收藏记录被删掉：回减统计和热度
    private void cancelFavorite(PostEntity post, LocalDateTime created) {
        statistics.cancel(post.getAuthorId(), CreatorMetric.FAVORITE, created);
        long delta = -properties.favoriteWeight();
        posts.incrementHotScore(post.getId(), delta);
        publishScore(post.getId(), delta);
    }

    private void publishScore(long postId, long delta) {
        events.publishEvent(
            new PostReadModelEvent(
                postId,
                null,
                PostReadModelEvent.Kind.SCORE,
                delta
            )
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
        return savedOf(user(), folder, page, size);
    }

    // 别人公开收藏夹里的帖子：收藏夹不是他的、或者是私密的，都按不存在处理
    public PageResult<SavedPost> publicSaved(
        long owner,
        long folder,
        long page,
        long size
    ) {
        var row = folders.selectById(folder);
        if (
            row == null ||
            row.getUserId() != owner ||
            row.getVisibility() != FolderVisibility.PUBLIC
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return savedOf(owner, folder, page, size);
    }

    // 别人主页上的数量
    public PublicCounts publicCounts(long owner) {
        return new PublicCounts(
            posts.selectCount(
                Wrappers.<PostEntity>lambdaQuery().eq(
                    PostEntity::getAuthorId,
                    owner
                )
            ),
            count(owner, FolderVisibility.PUBLIC),
            folders.publicFavorites(owner)
        );
    }

    private PageResult<SavedPost> savedOf(
        long owner,
        Long folder,
        long page,
        long size
    ) {
        var rows =
            folder == null
                ? folders.likes(owner, (page - 1) * size, size)
                : folders.posts(owner, folder, (page - 1) * size, size);
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
                ? folders.liked(owner)
                : folders.favorites(owner, folder),
            page,
            size
        );
    }

    public Resource cover(String name) {
        var folder = folders.selectOne(
            Wrappers.<FavoriteFolder>lambdaQuery()
                .eq(FavoriteFolder::getCoverName, name)
                // 自己的封面能看；别人只能看公开收藏夹的封面
                .and(w ->
                    w
                        .eq(FavoriteFolder::getUserId, user())
                        .or()
                        .eq(FavoriteFolder::getVisibility, FolderVisibility.PUBLIC)
                )
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
