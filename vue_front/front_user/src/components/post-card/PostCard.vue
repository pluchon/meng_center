<template>
  <article
    class="post-card"
    :class="'card-'+post.category.toLowerCase()"
    @click="openCard"
    @pointerenter="preloadDetail"
    @focusin="preloadDetail"
  >
    <div class="post-author">
      <el-avatar
        :src="avatarUrl(post.author.avatar) || userAvatar"
        :alt="post.author.nickname || t('home.authorFallback')"
      /><strong>{{ post.author.nickname || t('home.authorFallback') }}</strong><span class="author-level">{{ t('home.level',{level:Math.max(1,post.author.level)}) }}</span><div class="post-meta">
        <time :datetime="post.createTime"><el-icon aria-hidden="true"><Clock /></el-icon>{{ relativeTime(post.createTime) }}</time><span
          v-if="region.label"
          class="post-region"
        ><svg
          v-if="region.shape"
          class="region-silhouette"
          aria-hidden="true"
          viewBox="0 0 48 48"
        ><use :href="regionSilhouettes+'#'+region.shape" /></svg>{{ region.label }}</span>
      </div>
    </div>
    <div class="post-title">
      <router-link
        :to="detailLink"
      >
        <HighlightText
          :text="post.title"
          :segments="post.titleSegments"
        />
      </router-link>
    </div>
    <p class="post-summary">
      <HighlightText
        :text="post.summary"
        :segments="post.summarySegments"
      />
    </p>
    <div
      v-if="post.images.length"
      class="post-images"
    >
      <button
        v-for="(image,index) in post.images"
        :key="image.id"
        type="button"
        @click="preview(index)"
      >
        <el-image
          :src="contentImage(image.thumbUrl)"
          :alt="t('home.image')"
          fit="cover"
          lazy
        >
          <template #error>
            <el-icon class="image-fallback">
              <PictureIcon />
            </el-icon>
          </template>
        </el-image><span v-if="index===4&&post.imageCount>5">{{ t('home.extraImages',{count:post.imageCount-5}) }}</span>
      </button>
    </div>
    <el-image-viewer
      v-if="previewIndex!==null"
      teleported
      :url-list="post.images.map(image=>contentImage(image.url))"
      :initial-index="previewIndex"
      @close="previewIndex=null"
    />
    <div class="post-actions">
      <span
        class="views-action"
        :aria-label="t('home.views')"
      ><el-icon><ViewIcon /></el-icon>{{ post.viewCount }}</span><button
        type="button"
        class="like-action"
        :class="{active:post.liked}"
        :aria-label="t(post.liked?'home.unlike':'home.like')"
        :aria-pressed="post.liked"
        :disabled="pending.like"
        @click="toggle('like')"
      >
        <el-icon><Star /></el-icon>{{ post.likeCount }}
      </button><button
        type="button"
        class="comment-action"
        :aria-label="t('home.comment')"
        @click="openDetail"
      >
        <el-icon><ChatDotRound /></el-icon>{{ post.commentCount }}
      </button><button
        type="button"
        class="favorite-action"
        :aria-label="t(post.favorited?'home.unfavorite':'home.favorite')"
        :class="{active:post.favorited}"
        :aria-pressed="post.favorited"
        :disabled="pending.favorite"
        @click="toggle('favorite')"
      >
        <el-icon><CollectionTag /></el-icon>{{ post.favoriteCount }}
      </button><span
        class="post-type category-badge"
        :class="'category-'+post.category.toLowerCase()"
      >{{ t('home.categories.'+post.category) }}</span>
    </div>
  </article>
</template>
<script src="./PostCard.js"></script>
<style lang="scss" scoped src="./PostCard.scss"></style>
