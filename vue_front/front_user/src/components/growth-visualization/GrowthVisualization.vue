<template>
  <el-dialog
    :model-value="open"
    :title="t('growth.visualTitle')"
    class="growth-visual-dialog"
    width="min(1040px,calc(100vw - 32px))"
    align-center
    append-to-body
    destroy-on-close
    :show-close="false"
    @update:model-value="$emit('update:open',$event)"
  >
    <template #header>
      <div class="visual-heading">
        <span class="visual-emblem"><img
          :src="icons.chart"
          alt=""
          aria-hidden="true"
        ></span>
        <h2>{{ t('growth.visualTitle') }}</h2>
        <div
          class="visual-range"
          :style="{'--range-index':GrowthCenter.DAYS.indexOf(days)}"
          role="group"
          :aria-label="t('growth.chartDescription',{days})"
        >
          <button
            v-for="value in GrowthCenter.DAYS"
            :key="value"
            type="button"
            :class="{selected:days===value}"
            :aria-pressed="days===value"
            @click="$emit('update:days',value)"
          >
            {{ t('growth.recent',{days:value}) }}
          </button>
        </div>
        <button
          class="visual-close"
          type="button"
          :aria-label="t('growth.closeVisualization')"
          @click="$emit('update:open',false)"
        >
          <img
            :src="icons.close"
            alt=""
            aria-hidden="true"
          >
        </button>
      </div>
    </template>
    <div
      class="growth-visualization"
      :style="{'--visual-sparkle':'url('+JSON.stringify(communitySparkle)+')'}"
    >
      <img
        class="visual-bubble"
        :src="detailBubble"
        alt=""
      ><img
        class="visual-flower"
        :src="detailFlower"
        alt=""
      ><span
        class="visual-star"
        aria-hidden="true"
      />
      <PageState
        v-if="stats.error||!shown"
        :state="stats.error?'error':'loading'"
        @retry="$emit('retry')"
      />
      <template v-else>
        <div
          v-if="total>0"
          class="experience-charts"
          :class="{switching}"
          :aria-busy="switching"
        >
          <span
            v-if="switching"
            class="visual-spinner"
            aria-hidden="true"
          ><i /><i /><i /></span>
          <section class="source-chart">
            <span
              class="visual-tape tape-left"
              aria-hidden="true"
            />
            <h3>
              <img
                :src="icons.chart"
                class="chart-icon"
                alt=""
                aria-hidden="true"
              >{{ t('growth.chartSources') }}<span>{{ t('growth.shareSticker') }}</span>
            </h3><CreatorChart
              :type="CreatorChartType.EXP_SOURCES"
              :data="shown.sources"
              :days="days"
            />
          </section>
          <section class="trend-chart">
            <span
              class="visual-tape tape-right"
              aria-hidden="true"
            />
            <h3>
              <img
                :src="icons.trend"
                class="chart-icon"
                alt=""
                aria-hidden="true"
              >{{ t('growth.chartTrend') }}<span>{{ t('growth.trendSticker') }}</span>
            </h3><CreatorChart
              :type="CreatorChartType.EXP_TREND"
              :data="shown.days"
              :days="days"
            />
          </section>
        </div>
        <PageState
          v-else
          state="empty"
        />
        <footer class="visual-footer">
          <span class="visual-period"><img
            :src="icons.calendar"
            class="chart-icon"
            alt=""
            aria-hidden="true"
          >{{ period }}</span><span class="visual-face">{{ t('growth.visualFace') }}</span>
        </footer>
      </template>
    </div>
  </el-dialog>
</template>
<script src="./GrowthVisualization.js"></script>
<style lang="scss" src="./GrowthVisualization.scss"></style>
