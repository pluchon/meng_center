<template>
  <div
    class="creator-chart"
    :class="type"
  >
    <div
      v-if="type===CreatorChartType.TREND"
      class="chart-legend"
      role="group"
      :aria-label="t('creator.legend')"
    >
      <button
        v-for="item in legend"
        :key="item.field"
        type="button"
        :class="[item.key,{muted:!item.active}]"
        :aria-pressed="item.active"
        @click="toggle(item.field)"
      >
        <span aria-hidden="true" />{{ t('creator.'+item.key) }}
      </button>
    </div>
    <div
      ref="host"
      class="chart-host"
      role="img"
      :aria-label="type===CreatorChartType.TREND?t('creator.chartDescription',{days}):t('creator.performance')"
    />
    <div
      v-if="type===CreatorChartType.TOP"
      class="ranking-links"
    >
      <button
        v-for="(post,index) in data"
        :key="post.id"
        type="button"
        @click="$emit('open',post.id)"
      >
        <span class="rank">{{ index+1 }}</span><strong>{{ post.title }}</strong><span class="score">{{ formatNumber(post.hotScore) }}</span>
      </button>
    </div>
    <PageState
      v-if="failed"
      state="error"
      @retry="mount"
    />
  </div>
</template>
<script src="./CreatorChart.js"></script>
<style lang="scss" scoped src="./CreatorChart.scss"></style>
