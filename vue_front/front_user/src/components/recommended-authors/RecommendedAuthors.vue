<template>
  <section
    v-if="loading||authors.length"
    class="recommended-authors"
    :aria-label="t('ai.authorsTitle')"
    :aria-busy="loading"
  >
    <span
      class="authors-tape"
      aria-hidden="true"
    />
    <header>
      <img
        :src="editorMagicIcon"
        alt=""
        aria-hidden="true"
      >
      <h2>{{ t('ai.authorsTitle') }}</h2>
      <button
        v-if="!loading&&(total>size||batch>1)"
        type="button"
        class="authors-refresh"
        :class="{spinning:refreshing}"
        :disabled="refreshing"
        :aria-busy="refreshing"
        @click="refresh"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path d="M20 12a8 8 0 0 1-13.7 5.6" />
          <path d="M4 12a8 8 0 0 1 13.7-5.6" />
          <path d="M17.5 3.5 18 7l-3.5.5" />
          <path d="M6.5 20.5 6 17l3.5-.5" />
        </svg>{{ t('ai.authorsRefresh') }}
      </button>
    </header>
    <div
      v-if="loading"
      class="authors-grid"
      role="status"
      :aria-label="t('ai.loading')"
    >
      <article
        v-for="index in 5"
        :key="index"
        class="author-card author-loading"
      >
        <GlowRing />
        <i class="author-avatar-skeleton" />
        <i class="author-bar author-bar-name" />
        <i class="author-bar" />
        <i class="author-bar author-bar-button" />
      </article>
    </div>
    <Transition
      v-else
      name="authors-batch"
      mode="out-in"
    >
      <div
        :key="batch"
        class="authors-grid"
      >
        <article
          v-for="item in authors"
          :key="item.author.id"
          class="author-card"
        >
          <router-link
            class="author-link"
            :to="{name:RouteName.USER,params:{id:item.author.id}}"
          >
            <el-avatar
              :size="56"
              :src="avatarUrl(item.author.avatar)||userAvatar"
              :alt="item.author.nickname||t('home.authorFallback')"
            />
            <strong>{{ item.author.nickname||t('home.authorFallback') }}</strong>
            <span class="author-level">{{ t('home.level',{level:Math.max(1,item.author.level)}) }}</span>
          </router-link>
          <small class="author-reason">{{ t('ai.authorReasons.'+item.reason,{count:item.interactions,topic:item.topic}) }}</small>
          <button
            type="button"
            class="author-follow"
            :class="{done:followed[item.author.id]}"
            :disabled="pending[item.author.id]||followed[item.author.id]"
            @click="follow(item)"
          >
            {{ t(followed[item.author.id]?'account.following':'account.follow') }}
          </button>
        </article>
      </div>
    </Transition>
  </section>
</template>
<script src="./RecommendedAuthors.js"></script>
<style lang="scss" scoped src="./RecommendedAuthors.scss"></style>
