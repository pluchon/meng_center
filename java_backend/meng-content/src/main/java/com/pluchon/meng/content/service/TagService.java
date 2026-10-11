package com.pluchon.meng.content.service;

import com.pluchon.meng.content.mapper.TagMapper;
import com.pluchon.meng.content.validation.PostRules;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// 系统标签匹配和帖子标签关联
@Service
public class TagService {

    // 系统标签数据存取
    @Autowired
    private TagMapper tags;

    // 优先匹配系统已有标签并转义通配符
    public List<String> suggest(String keyword, int size) {
        return tags
                .selectByPrefix(
                        PostRules.tagKey(keyword)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_"),
                        size)
                .stream()
                .map(t -> t.getName())
                .toList();
    }

    // 按稳定顺序锁定标签并关联帖子
    public void attach(long post, List<String> input) {
        // 按稳定顺序锁定标签，避免并发操作以相反顺序插入造成死锁。
        for (String name :
                PostRules.tags(input).stream()
                        .sorted(java.util.Comparator.comparing(PostRules::tagKey))
                        .toList()) {
            var key = PostRules.tagKey(name);
            tags.insertIfAbsent(name, key);
            var tag = tags.selectByNameKeyForUpdate(key);
            tags.insertPostTag(post, tag.getId());
            tags.incrementUseCount(tag.getId());
        }
    }

    // 锁定新旧标签后替换帖子标签关系
    public void replace(long post, List<String> names) {
        var all = new java.util.TreeMap<String, String>();
        for (String name : tags.selectNamesByPostId(post)) all.put(PostRules.tagKey(name), name);
        for (String name : names) all.putIfAbsent(PostRules.tagKey(name), name);
        for (var entry : all.entrySet()) {
            tags.insertIfAbsent(entry.getValue(), entry.getKey());
            tags.selectByNameKeyForUpdate(entry.getKey());
        }
        detach(post);
        attach(post, names);
    }

    // 删除帖子标签关系并递减引用次数
    public void detach(long post) {
        for (String name :
                tags.selectNamesByPostId(post).stream()
                        .sorted(java.util.Comparator.comparing(PostRules::tagKey))
                        .toList()) {
            var tag = tags.selectByNameKeyForUpdate(PostRules.tagKey(name));
            tags.decrementUseCount(tag.getId());
        }
        tags.deletePostTags(post);
    }

    // 查询帖子关联的标签名称
    public List<String> forPost(long post) {
        return tags.selectNamesByPostId(post);
    }
}
