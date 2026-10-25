<template>
  <div class="emotion-panel">
    <input
      v-model="query"
      type="search"
      :placeholder="t('meng.searchEmotion')"
      :aria-label="t('meng.searchEmotion')"
    >
    <button
      v-if="catalog.error"
      type="button"
      class="text-button"
      @click="catalog.load(true)"
    >
      {{ t('meng.retry') }}
    </button>
    <Transition
      name="emotion-swap"
      mode="out-in"
    >
      <div
        :key="active"
        class="emotion-grid"
        role="group"
        :aria-label="t('im.series.' + active)"
      >
        <button
          v-for="(item, index) in current.items"
          :key="item.name"
          type="button"
          :title="item.name"
          :style="{ '--i': index % 24 }"
          @click="$emit('select', item)"
        >
          <img
            :src="item.image"
            :alt="item.name"
          >
        </button>
      </div>
    </Transition>
    <footer
      class="emotion-series"
      role="tablist"
      :aria-label="t('im.emotionSeries')"
    >
      <button
        v-for="series in EmotionSeries"
        :key="series.key"
        type="button"
        role="tab"
        :aria-selected="series.key === active"
        :class="{ selected: series.key === active }"
        @click="active = series.key"
      >
        <img
          :src="series.items[0]?.image"
          alt=""
          aria-hidden="true"
        >{{
          t('im.series.' + series.key)
        }}
      </button>
      <span
        class="series-more"
        aria-hidden="true"
      >{{ t('im.moreSeries') }}</span>
    </footer>
  </div>
</template>
<script src="./EmotionPanel.js"></script>
<style lang="scss" scoped src="./EmotionPanel.scss"></style>
