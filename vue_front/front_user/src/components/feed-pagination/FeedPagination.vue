<template>
  <div class="feed-pagination">
    <nav :aria-label="t('home.page',{page})">
      <template
        v-for="(value,index) in pages"
        :key="index"
      >
        <button
          v-if="value"
          type="button"
          :class="{selected:value===page}"
          :aria-current="value===page?'page':undefined"
          :aria-label="t('home.page',{page:value})"
          @click="$emit('page',value)"
        >
          {{ value }}
        </button><span
          v-else
          aria-hidden="true"
        >{{ t('home.ellipsis') }}</span>
      </template>
    </nav><button
      type="button"
      class="back-top"
      @click="top"
    >
      {{ t('home.top') }}
    </button>
  </div>
</template>
<script src="./FeedPagination.js"></script>
<style lang="scss" scoped src="./FeedPagination.scss"></style>
