<template>
  <CommunityLayout class="search-layout">
    <Aurora
      class="search-aurora"
      :amplitude="1.15"
      :blend="0.65"
      :speed="0.95"
    />
    <div
      class="search-decor"
      aria-hidden="true"
    >
      <img
        :src="detailBubble"
        alt=""
        class="search-bubble bubble-left"
      ><img
        :src="detailBubble"
        alt=""
        class="search-bubble bubble-right"
      >
    </div>
    <main
      ref="pageRoot"
      class="search-page"
    >
      <header class="search-summary">
        <span
          class="search-icon"
          aria-hidden="true"
        ><img
          :src="navSearchIcon"
          alt=""
        ></span>
        <h1>{{ query.q || t('search.title') }}</h1><span
          class="search-sparkle"
          aria-hidden="true"
          :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
        /><span
          v-if="query.q"
          class="search-total"
          aria-live="polite"
        >{{ t('search.total',{count:total}) }}</span>
      </header>
      <FeedFilters
        :sorts="SearchSorts"
        :category="query.category"
        :sort="query.sort"
        @change="filter"
      />
      <div
        class="search-results"
        :class="{refreshing:loading}"
        :aria-busy="loading"
      >
        <PageState
          v-if="loading&&!records.length"
          state="loading"
        />
        <PageState
          v-else-if="error"
          state="error"
          @retry="load"
        />
        <PageState
          v-else-if="!records.length"
          class="search-empty"
          state="empty"
          art="search"
          size="tall"
          :text="t(query.q?'search.empty':'search.prompt')"
        />
        <div
          v-else
          class="post-list"
        >
          <PostCard
            v-for="post in records"
            :key="post.id"
            :post="post"
            @change="change"
          />
        </div>
      </div>
      <FeedPagination
        v-if="!error&&total>0"
        :page="query.page"
        :total-pages="totalPages"
        @top="scrollToTop"
        @page="page=>filter({page})"
      />
    </main>
  </CommunityLayout>
</template>
<script src="./SearchView.js"></script>
<style lang="scss" scoped src="./SearchView.scss"></style>
