<template>
  <section
    class="related-posts"
    :aria-label="t('ai.related')"
  >
    <span
      class="related-tape"
      aria-hidden="true"
    /><span
      class="related-sticker"
      aria-hidden="true"
    >{{ t('ai.relatedFace') }}</span>
    <header class="related-head">
      <h2>
        <span
          class="related-icon"
          aria-hidden="true"
        ><el-icon><IconSparkles /></el-icon></span><span class="related-title">{{ t('ai.related') }}</span>
      </h2>
      <div
        v-if="records.length"
        class="related-tools"
      >
        <div
          v-if="pages.length>1"
          class="related-pager"
        >
          <button
            type="button"
            :aria-label="t('ai.relatedPrev')"
            :title="t('ai.relatedPrev')"
            :disabled="index===0"
            @click="index--"
          >
            <el-icon><IconChevronLeft /></el-icon>
          </button><span
            v-for="(page,dot) in pages"
            :key="dot"
            class="related-dot"
            :class="{active:dot===index}"
            aria-hidden="true"
          /><button
            type="button"
            :aria-label="t('ai.relatedNext')"
            :title="t('ai.relatedNext')"
            :disabled="index===pages.length-1"
            @click="index++"
          >
            <el-icon><IconChevronRight /></el-icon>
          </button>
        </div>
        <button
          type="button"
          class="related-more"
          :class="{spinning:loadingMore}"
          :aria-label="t('ai.relatedMore')"
          :title="t('ai.relatedMore')"
          :disabled="loadingMore||!hasMore"
          :aria-busy="loadingMore"
          @click="more"
        >
          <el-icon><IconRefresh /></el-icon>
        </button>
      </div>
    </header>
    <PageState
      v-if="resource.loading||resource.error"
      :state="resource.error?'error':'loading'"
      @retry="load"
    />
    <TransitionGroup
      v-else-if="records.length"
      tag="div"
      name="related-card"
      class="related-grid"
    >
      <router-link
        v-for="(post, order) in records"
        :key="post.id"
        class="related-card tag-sway-host"
        :class="'card-' + post.category.toLowerCase()"
        :style="{ '--i': order }"
        :to="{name:RouteName.POST_DETAIL,params:{id:post.id}}"
      >
        <span class="related-cover"><img
          v-if="post.images[0]"
          :src="contentImage(post.images[0].thumbUrl)"
          alt=""
          loading="lazy"
          decoding="async"
        ><svg
          v-else
          viewBox="0 0 96 72"
          aria-hidden="true"
          preserveAspectRatio="xMidYMid slice"
        ><use :href="accountScenes + '#post-' + post.category.toLowerCase()" /></svg></span>
        <CategoryTag
          class="related-tag"
          :category="post.category"
        />
        <strong>{{ post.title }}</strong>
        <span class="related-author"><img
          :src="avatarUrl(post.author.avatar) || userAvatar"
          alt=""
        >{{ post.author.nickname || t('home.authorFallback') }}</span>
      </router-link>
    </TransitionGroup>
    <PageState
      v-else
      state="empty"
      size="small"
    />
  </section>
</template>
<script src="./RelatedPosts.js"></script>
<style lang="scss" scoped src="./RelatedPosts.scss"></style>
