package com.pluchon.meng.api.content.event;

public record PostPublishedEvent(
    long postId,
    long authorId,
    java.time.LocalDateTime version,
    String category,
    String title,
    String plainText,
    java.util.List<String> tags,
    java.util.List<ReviewImage> imageThumbs,
    boolean imagesChanged
) {
    public record ReviewImage(long id, String data) {}

    public PostPublishedEvent(long postId, long authorId) {
        this(
            postId,
            authorId,
            null,
            null,
            null,
            null,
            java.util.List.of(),
            java.util.List.of(),
            true
        );
    }

    public static final String EXCHANGE = "meng.content.topic";
    public static final String EVENT_TYPE = "post.published";
}
