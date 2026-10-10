<template>
  <CommunityLayout>
    <section class="post-detail">
      <router-link
        class="detail-back"
        :to="{name:RouteName.HOME}"
      >
        {{ t('editor.home') }}
      </router-link><PageState
        v-if="loading"
        state="loading"
      /><PageState
        v-else-if="error"
        state="error"
        @retry="load"
      /><article v-else-if="post">
        <div class="detail-heading">
          <span class="detail-category">{{ t('home.categories.'+post.category) }}</span><h1>{{ post.title }}</h1><div class="detail-author">
            <el-avatar :src="post.author.avatar||userAvatar" /><strong>{{ post.author.nickname||t('home.authorFallback') }}</strong><small>{{ t('home.level',{level:post.author.level}) }}</small><time :datetime="post.createTime">{{ relativeTime(post.createTime) }}</time><small v-if="post.ipRegion">{{ post.ipRegion }}</small>
          </div><div
            v-if="post.tags.length"
            class="detail-tags"
          >
            <span
              v-for="tag in post.tags"
              :key="tag"
            >{{ tag }}</span>
          </div>
        </div><img
          v-if="post.cover"
          class="detail-cover"
          :src="contentImage(post.cover.url)"
          :alt="t('editor.coverAlt')"
        ><MarkdownBody :content="post.content" /><div
          v-if="post.images.length"
          class="detail-images"
        >
          <button
            v-for="(image,index) in post.images"
            :key="image.id"
            type="button"
            @click="previewIndex=index"
          >
            <img
              :src="contentImage(image.thumbUrl)"
              :alt="t('editor.imageAlt')"
              loading="lazy"
            >
          </button>
        </div><small class="detail-views">{{ t('editor.views',{count:post.viewCount}) }}</small><el-image-viewer
          v-if="previewIndex!==null"
          teleported
          :url-list="post.images.map(image=>contentImage(image.url))"
          :initial-index="previewIndex"
          @close="previewIndex=null"
        />
      </article>
    </section>
  </CommunityLayout>
</template>
<script src="./PostDetailView.js"></script>
<style lang="scss" scoped src="./PostDetailView.scss"></style>
