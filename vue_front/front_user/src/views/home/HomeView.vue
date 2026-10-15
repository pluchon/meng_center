<template>
  <CommunityLayout class="home-layout">
    <GeometryLines />
    <section
      class="home-hero"
      aria-roledescription="carousel"
      :aria-label="t('home.posterLabel')"
      @mouseenter="pause"
      @mouseleave="resume"
      @focusin="pause"
      @focusout="resume"
    >
      <div
        v-for="(slide,index) in slides"
        :key="slide.key"
        class="hero-slide"
        :class="{active:index===current,poster:!slide.cover}"
        :aria-hidden="index!==current"
      >
        <img
          v-if="!slide.cover"
          class="hero-backdrop"
          :src="slide.src"
          alt=""
          aria-hidden="true"
        ><img
          class="hero-art"
          :src="slide.src"
          :alt="t(slide.alt)"
          :loading="index?'lazy':'eager'"
          decoding="async"
        >
      </div>
      <Transition name="hero-overlay">
        <div
          v-if="current===0"
          class="hero-overlay"
        >
          <div
            class="hero-veil"
            aria-hidden="true"
          /><div class="hero-welcome">
            <span class="hero-cheer">{{ t('home.cheer') }}</span><h1>{{ t('home.welcome') }} <span>{{ t('home.brand') }}</span></h1>
          </div><span class="hero-bubble">{{ t('home.wait') }}</span><span class="hero-face">{{ t('home.face') }}</span><span class="hero-meow">{{ t('home.meow') }}</span><span
            v-for="spark in ['a','b','c','d']"
            :key="spark"
            class="hero-sparkle"
            :class="'sparkle-'+spark"
            aria-hidden="true"
            :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
          />
        </div>
      </Transition>
      <HeroWaves
        class="hero-waves-layer"
        :class="{soft:current>0}"
      />
      <div class="hero-pager">
        <button
          type="button"
          class="hero-arrow"
          :aria-label="t('home.posterPrev')"
          @click="go(current-1)"
        >
          <el-icon><IconChevronLeft /></el-icon>
        </button>
        <button
          v-for="(slide,index) in slides"
          :key="slide.key"
          type="button"
          class="hero-dot"
          :class="{active:index===current}"
          :aria-label="t('home.posterGo',{index:index+1})"
          :aria-current="index===current"
          @click="go(index)"
        />
        <button
          type="button"
          class="hero-arrow"
          :aria-label="t('home.posterNext')"
          @click="go(current+1)"
        >
          <el-icon><IconChevronRight /></el-icon>
        </button>
      </div>
    </section>
    <div
      class="home-content"
    >
      <section
        ref="feedRoot"
        class="home-feed"
        :aria-label="t('home.title')"
      >
        <FeedFilters
          :category="query.category"
          :sort="query.sort"
          @change="filter"
        />
        <div
          class="feed-results-shell"
          :aria-busy="loading"
        >
          <Transition
            name="feed-swap"
            mode="out-in"
          >
            <div
              :key="revision"
              class="feed-results"
            >
              <PageState
                v-if="loading&&!hasLoaded"
                state="loading"
              />
              <PageState
                v-else-if="error"
                state="error"
                @retry="load"
              />
              <PageState
                v-else-if="!records.length"
                class="feed-empty"
                state="empty"
                size="tall"
                :text="t('home.empty')"
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
          </Transition>
          <FeedPagination
            v-if="hasLoaded&&!error&&total>0"
            :page="renderedPage"
            :total-pages="Math.ceil(total/query.size)"
            @top="scrollToTop"
            @page="page=>filter({page})"
          />
        </div>
      </section>
    </div>
  </CommunityLayout>
</template>
<script src="./HomeView.js"></script>
<style lang="scss" scoped src="./HomeView.scss"></style>
