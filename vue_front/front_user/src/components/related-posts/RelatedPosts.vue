<template>
  <section
    class="related-posts"
    :aria-label="t('ai.related')"
  >
    <h2>{{ t('ai.related') }}</h2>
    <PageState
      v-if="resource.loading||resource.error"
      :state="resource.error?'error':'loading'"
      @retry="load"
    />
    <template v-else-if="records.length">
      <div class="related-grid">
        <router-link
          v-for="post in records"
          :key="post.id"
          :to="{name:RouteName.POST_DETAIL,params:{id:post.id}}"
        >
          <small>{{ t('home.categories.'+post.category) }}</small><strong>{{ post.title }}</strong><span>{{ post.author.nickname }}</span>
        </router-link>
      </div>
      <button
        v-if="records.length<total"
        type="button"
        class="related-more"
        :disabled="loadingMore"
        :aria-busy="loadingMore"
        @click="more"
      >
        {{ t('ai.relatedMore') }}<small>{{ t('ai.relatedProgress',{shown:records.length,total}) }}</small>
      </button>
    </template>
    <PageState
      v-else
      state="empty"
      size="small"
    />
  </section>
</template>
<script src="./RelatedPosts.js"></script>
<style lang="scss" scoped src="./RelatedPosts.scss"></style>
