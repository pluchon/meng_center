<template>
  <CommunityLayout class="home-layout">
    <section class="home-hero">
      <img
        class="hero-art"
        :src="homeArtwork"
        :alt="t('home.brand')"
      ><div
        class="hero-veil"
        aria-hidden="true"
      /><div class="hero-welcome">
        <span class="hero-cheer">{{ t('home.cheer') }}</span><h1>{{ t('home.welcome') }} <span>{{ t('home.brand') }}</span></h1>
      </div><span class="hero-bubble">{{ t('home.wait') }}</span><span class="hero-face">{{ t('home.face') }}</span><span class="hero-meow">{{ t('home.meow') }}</span>
      <HeroWaves />
    </section>
    <div
      class="home-content"
    >
      <section
        class="home-feed"
        :aria-label="t('home.title')"
      >
        <FeedFilters
          :category="query.category"
          :sort="query.sort"
          @change="filter"
        />
        <PageState
          v-if="loading"
          state="loading"
        />
        <PageState
          v-else-if="error"
          state="error"
          @retry="load"
        />
        <div
          v-else-if="!records.length"
          class="feed-empty"
          role="status"
        >
          {{ t('home.empty') }}
        </div>
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
        <FeedPagination
          v-if="!loading&&!error&&total>0"
          :page="query.page"
          :total-pages="Math.ceil(total/query.size)"
          @top="scrollToTop"
          @page="page=>filter({page})"
        />
      </section>
    </div>
  </CommunityLayout>
</template>
<script src="./HomeView.js"></script>
<style lang="scss" scoped src="./HomeView.scss"></style>
