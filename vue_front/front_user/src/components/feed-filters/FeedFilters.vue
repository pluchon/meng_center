<template>
  <div class="feed-filters">
    <div
      ref="categoryFilters"
      class="category-filters"
      @pointerover="categorySlider.onHover"
      @pointerleave="categorySlider.onLeave"
      @focusin="categorySlider.onHover"
      @focusout="categorySlider.onLeave"
    >
      <span
        class="slide-indicator"
        :class="categorySlider.sliderClass.value"
        :style="categorySlider.sliderStyle.value"
        aria-hidden="true"
      />
      <button
        type="button"
        :class="{selected:!category}"
        :aria-pressed="!category"
        @click="$emit('change',{category:undefined})"
        @pointerenter="hoverSelect($event,{category:undefined})"
        @pointerleave="cancelHover"
      >
        <img
          class="category-icon"
          :src="categoryAllIcon"
          alt=""
          aria-hidden="true"
        >{{ t('home.all') }}
      </button><span
        class="category-divider"
        aria-hidden="true"
      /><button
        v-for="item in Categories"
        :key="item"
        type="button"
        :class="['filter-'+item.toLowerCase(),{selected:item===category}]"
        :aria-pressed="item===category"
        @click="$emit('change',{category:item})"
        @pointerenter="hoverSelect($event,{category:item})"
        @pointerleave="cancelHover"
      >
        <img
          class="category-icon"
          :src="categoryIcons[item]"
          alt=""
          aria-hidden="true"
        >{{ t('home.categories.'+item) }}
      </button>
    </div><div
      ref="sortFilters"
      class="sort-filters"
      @pointerover="sortSlider.onHover"
      @pointerleave="sortSlider.onLeave"
      @focusin="sortSlider.onHover"
      @focusout="sortSlider.onLeave"
    >
      <span
        class="slide-indicator"
        :class="sortSlider.sliderClass.value"
        :style="sortSlider.sliderStyle.value"
        aria-hidden="true"
      />
      <button
        v-for="sort in PostSort"
        :key="sort"
        type="button"
        :class="['sort-'+sort.toLowerCase(),{selected:sortValue===sort}]"
        :aria-pressed="sortValue===sort"
        @click="$emit('change',{sort})"
        @pointerenter="hoverSelect($event,{sort})"
        @pointerleave="cancelHover"
      >
        {{ t('home.sort.'+sort) }}
      </button>
    </div>
  </div>
</template>
<script src="./FeedFilters.js"></script>
<style lang="scss" scoped src="./FeedFilters.scss"></style>
