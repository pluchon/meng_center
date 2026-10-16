package com.pluchon.meng.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationSpreadTest {

    private final List<RecommendationService.Entry> ranked = new ArrayList<>();
    private final HashMap<Long, PostEntity> posts = new HashMap<>();
    private final HashMap<Long, Set<String>> routes = new HashMap<>();

    private void add(long id, long author, PostCategory category, String route) {
        var post = new PostEntity();
        post.setId(id);
        post.setAuthorId(author);
        post.setCategory(category);
        posts.put(id, post);
        routes.put(id, Set.of(route));
        ranked.add(new RecommendationService.Entry(id, route, null, List.of()));
    }

    private List<Long> spread(Set<Long> read) {
        return RecommendationService.spread(ranked, posts, routes, read)
            .stream()
            .map(RecommendationService.Entry::id)
            .toList();
    }

    @Test
    void keepsAuthorSpacingWhileOtherAuthorsRemain() {
        for (long id = 1; id <= 6; id++) add(id, 1, PostCategory.DAILY, "HOT");
        for (long id = 7; id <= 10; id++) add(id, id, PostCategory.DAILY, "HOT");
        var result = spread(Set.of());
        for (int i = 0; i < result.size(); i++) {
            var previous = authors(result.subList(Math.max(0, i - 4), i));
            boolean repeated = previous.contains(posts.get(result.get(i)).getAuthorId());
            boolean alternative = authors(result.subList(i, result.size()))
                .stream()
                .anyMatch(author -> !previous.contains(author));
            assertTrue(!repeated || !alternative, "author repeated at " + i);
        }
    }

    private Set<Long> authors(List<Long> ids) {
        return ids
            .stream()
            .map(id -> posts.get(id).getAuthorId())
            .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void doesNotTruncateWhenOnlyOneAuthorIsLeft() {
        for (long id = 1; id <= 5; id++) add(id, 1, PostCategory.TECH, "HOT");
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), spread(Set.of()));
    }

    @Test
    void doesNotTruncateWhenCategoryRunCannotBeBroken() {
        for (long id = 1; id <= 4; id++) add(id, id, PostCategory.TECH, "HOT");
        assertEquals(List.of(1L, 2L, 3L, 4L), spread(Set.of()));
    }

    @Test
    void spreadsCategoryRuns() {
        add(1, 1, PostCategory.TECH, "HOT");
        add(2, 2, PostCategory.TECH, "HOT");
        add(3, 3, PostCategory.TECH, "HOT");
        add(4, 4, PostCategory.DAILY, "HOT");
        var result = spread(Set.of());
        assertEquals(4, result.size());
        assertEquals(List.of(1L, 2L, 4L, 3L), result);
    }

    @Test
    void reservesTheTenthSlotForUnreadNewPosts() {
        for (long id = 1; id <= 9; id++) add(id, id, PostCategory.values()[(int) (id % 3)], "HOT");
        add(10, 10, PostCategory.DAILY, "HOT");
        add(11, 11, PostCategory.TECH, "NEW");
        var result = spread(Set.of());
        assertEquals(11L, result.get(9));
        var withRead = spread(Set.of(11L));
        assertTrue(withRead.get(9) != 11L);
    }
}
