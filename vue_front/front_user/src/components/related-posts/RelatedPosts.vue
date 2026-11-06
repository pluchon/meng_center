<template>
  <section
    class="related-posts"
    :aria-label="t('ai.related')"
  >
    <header class="related-head">
      <h2>{{ t('ai.related') }}</h2>
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
    <div
      v-else-if="records.length"
      class="related-grid"
    >
      <router-link
        v-for="post in records"
        :key="post.id"
        :to="{name:RouteName.POST_DETAIL,params:{id:post.id}}"
      >
        <small>{{ t('home.categories.'+post.category) }}</small><strong>{{ post.title }}</strong><span>{{ post.author.nickname }}</span>
      </router-link>
    </div>
    <PageState
      v-else
      state="empty"
      size="small"
    />
  </section>
</template>
<script src="./RelatedPosts.js"></script>
<style lang="scss" scoped src="./RelatedPosts.scss"></style>
