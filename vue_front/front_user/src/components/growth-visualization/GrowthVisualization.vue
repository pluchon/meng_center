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
        <span class="visual-emblem"><IconChartDonut :size="25" /></span>
        <div><h2>{{ t('growth.visualTitle') }}</h2><p>{{ t('growth.visualSubtitle') }}</p></div>
        <button
          class="visual-close"
          type="button"
          :aria-label="t('growth.closeVisualization')"
          @click="$emit('update:open',false)"
        >
          <IconX :size="20" />
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
      <div class="visual-toolbar">
        <div class="visual-total">
          <IconSparkles :size="18" /><span>{{ t('growth.recent',{days}) }}</span><strong>{{ '+'+total }}</strong>
        </div>
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
      </div>
      <PageState
        v-if="stats.error||!stats.data"
        :state="stats.error?'error':'loading'"
        @retry="$emit('retry')"
      />
      <template v-else>
        <div class="source-totals">
          <span
            v-for="kind in ExpSources"
            :key="kind"
            :class="'source-'+SourceTones[kind]"
          ><i aria-hidden="true" />{{ t('growth.source.'+kind) }}<b>{{ '+'+(stats.data.sources.find(row=>row.source===kind)?.amount??0) }}</b></span>
        </div>
        <div
          v-if="total>0"
          class="experience-charts"
          :aria-busy="stats.loading"
        >
          <section class="source-chart">
            <span
              class="visual-tape tape-left"
              aria-hidden="true"
            />
            <h3><IconChartDonut :size="19" />{{ t('growth.chartSources') }}<span>{{ t('growth.shareSticker') }}</span></h3><CreatorChart
              :type="CreatorChartType.EXP_SOURCES"
              :data="stats.data.sources"
              :days="days"
            />
          </section>
          <section class="trend-chart">
            <span
              class="visual-tape tape-right"
              aria-hidden="true"
            />
            <h3><IconChartLine :size="19" />{{ t('growth.chartTrend') }}<span>{{ t('growth.trendSticker') }}</span></h3><CreatorChart
              :type="CreatorChartType.EXP_TREND"
              :data="stats.data.days"
              :days="days"
            />
          </section>
        </div>
        <PageState
          v-else
          state="empty"
        />
        <footer class="visual-footer">
          <span>{{ period }}</span><span class="visual-face">{{ t('growth.visualFace') }}</span>
        </footer>
      </template>
    </div>
  </el-dialog>
</template>
<script src="./GrowthVisualization.js"></script>
<style lang="scss" src="./GrowthVisualization.scss"></style>
