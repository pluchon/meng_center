<template>
  <div class="feed-filters">
    <div class="category-filters">
      <button
        type="button"
        :class="{selected:!category}"
        @click="$emit('change',{category:undefined})"
      >
        {{ t('home.all') }}
      </button><button
        v-for="item in Categories"
        :key="item"
        type="button"
        :class="['filter-'+item.toLowerCase(),{selected:item===category}]"
        @click="$emit('change',{category:item})"
      >
        <span class="category-dot" />{{ t('home.categories.'+item) }}
      </button>
    </div><div
      class="sort-filters"
      :class="{hot:sortValue===PostSort.HOT}"
    >
      <button
        v-for="sort in PostSort"
        :key="sort"
        type="button"
        :class="{selected:sortValue===sort}"
        :aria-pressed="sortValue===sort"
        @click="$emit('change',{sort})"
      >
        {{ t('home.sort.'+sort) }}
      </button>
    </div>
  </div>
</template>
<script src="./FeedFilters.js"></script>
<style lang="scss" scoped src="./FeedFilters.scss"></style>
