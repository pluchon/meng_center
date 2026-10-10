package com.pluchon.meng.content.service;

import com.pluchon.meng.content.mapper.TagMapper;
import com.pluchon.meng.content.validation.PostRules;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tags;

    public List<String> suggest(String keyword, int size) {
        return tags
            .suggest(
                PostRules.tagKey(keyword)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_"),
                size
            )
            .stream()
            .map(t -> t.getName())
            .toList();
    }

    public void attach(long post, List<String> input) {
        // Lock shared tag rows in a stable order to avoid opposite-order inserts deadlocking.
        for (String name : PostRules.tags(input)
            .stream()
            .sorted(java.util.Comparator.comparing(PostRules::tagKey))
            .toList()) {
            var key = PostRules.tagKey(name);
            tags.ensure(name, key);
            var tag = tags.find(key);
            tags.attach(post, tag.getId());
            tags.increment(tag.getId());
        }
    }

    public List<String> forPost(long post) {
        return tags.forPost(post);
    }
}
