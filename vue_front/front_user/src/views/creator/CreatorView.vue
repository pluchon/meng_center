<template>
  <CommunityLayout class="creator-layout">
    <ShapeWaves />
    <section
      class="creator-page"
      :aria-label="t('creator.title')"
    >
      <PageState
        v-if="overview.loading||overview.error"
        :state="overview.error?'error':'loading'"
        @retry="retry('overview')"
      />
      <div
        v-else-if="overview.data"
        class="creator-metrics"
      >
        <div
          v-for="metric in metrics"
          :key="metric.field"
          class="metric"
          :class="metric.key"
        >
          <span><img
            :src="metric.icon"
            class="metric-icon"
            width="26"
            height="26"
            alt=""
            aria-hidden="true"
          >{{ t('creator.'+metric.key) }}</span><strong>{{ formatNumber(counts[metric.field]??0) }}</strong><i
            class="metric-sparkle"
            aria-hidden="true"
          />
        </div>
      </div>
      <div class="creator-charts">
        <section class="creator-panel trend-panel">
          <span
            class="creator-tape"
            aria-hidden="true"
          /><img
            :src="detailBubble"
            alt=""
            class="panel-bubble"
            aria-hidden="true"
          >
          <header class="panel-heading">
            <h2>
              <span>{{ t('creator.trend') }}</span><span
                :style="{ maskImage: 'url('+JSON.stringify(communitySparkle)+')' }"
                class="creator-star"
                aria-hidden="true"
              />
            </h2>
            <div
              class="trend-range"
              :class="{'long-range':days===Creator.DAYS[1]}"
              role="group"
              :aria-label="t('creator.range')"
            >
              <button
                v-for="value in Creator.DAYS"
                :key="value"
                type="button"
                :class="{selected:days===value}"
                :aria-pressed="days===value"
                @click="days=value"
              >
                {{ t('creator.days',{days:value}) }}
              </button>
            </div>
          </header>
          <PageState
            v-if="trend.error||(trend.loading&&!trend.data.length)"
            :state="trend.error?'error':'loading'"
            @retry="retry('trend')"
          />
          <CreatorChart
            v-else-if="hasTrend"
            :aria-busy="trend.loading"
            :class="{'trend-refreshing':trend.loading}"
            :data="trend.data"
            :days="days"
            :type="CreatorChartType.TREND"
          />
          <p
            v-else
            class="creator-empty"
          >
            {{ t('creator.emptyTrend') }}
          </p>
          <span
            class="trend-face"
            aria-hidden="true"
          >{{ t('creator.trendFace') }}</span><span
            :style="{ maskImage: 'url('+JSON.stringify(communitySparkle)+')' }"
            class="creator-star trend-sparkle"
            aria-hidden="true"
          />
        </section>
        <section class="creator-panel top-panel">
          <span
            class="creator-tape pink-tape"
            aria-hidden="true"
          /><img
            :src="detailFlower"
            alt=""
            class="panel-flower top-flower"
            aria-hidden="true"
          ><span
            class="creator-cheer"
            aria-hidden="true"
          >{{ t('creator.cheer') }}</span>
          <header class="panel-heading">
            <h2><span>{{ t('creator.performance') }}</span></h2><span
              :style="{ maskImage: 'url('+JSON.stringify(communitySparkle)+')' }"
              class="creator-star pink-sparkle"
              aria-hidden="true"
            />
          </header>
          <PageState
            v-if="top.loading||top.error"
            :state="top.error?'error':'loading'"
            @retry="retry('top')"
          />
          <CreatorChart
            v-else-if="top.data.length"
            :data="top.data"
            :type="CreatorChartType.TOP"
            @open="openPost"
          />
          <p
            v-else
            class="creator-empty"
          >
            {{ t('creator.emptyTop') }}
          </p>
        </section>
      </div>
      <section class="creator-panel content-panel">
        <span
          class="creator-tape"
          aria-hidden="true"
        /><img
          :src="detailFlower"
          alt=""
          class="panel-flower"
          aria-hidden="true"
        ><img
          :src="detailBubble"
          alt=""
          class="panel-bubble"
          aria-hidden="true"
        ><span
          :style="{ maskImage: 'url('+JSON.stringify(communitySparkle)+')' }"
          class="creator-star panel-sparkle"
          aria-hidden="true"
        />
        <header class="content-heading">
          <div
            class="content-tabs"
            role="tablist"
            :aria-label="t('creator.contents')"
          >
            <button
              type="button"
              role="tab"
              :aria-selected="tab===CreatorTab.PUBLISHED"
              :class="{selected:tab===CreatorTab.PUBLISHED}"
              @click="change({tab:CreatorTab.PUBLISHED})"
            >
              {{ t('creator.published') }}<span class="tab-count"><b>{{ overview.data?.postCount??published.data.total }}</b></span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="tab===CreatorTab.DRAFTS"
              :class="{selected:tab===CreatorTab.DRAFTS}"
              @click="change({tab:CreatorTab.DRAFTS})"
            >
              {{ t('creator.drafts') }}<span
                class="tab-count"
                :style="{'--ratio':draftRatio}"
              ><b>{{ drafts.data.length }}</b><i>{{ t('creator.draftMax',{max:Creator.DRAFT_MAX}) }}</i></span>
            </button>
          </div>
          <router-link
            class="creator-new"
            :to="{name:RouteName.POST_NEW}"
          >
            <IconPencil :size="18" />{{ t('creator.newPost') }}
          </router-link>
        </header>
        <div
          v-if="tab===CreatorTab.PUBLISHED"
          class="creator-list"
          role="tabpanel"
          :aria-busy="published.loading"
        >
          <PageState
            v-if="published.error||(published.loading&&!published.data.records.length)"
            :state="published.error?'error':'loading'"
            @retry="loadPublished"
          />
          <template v-if="published.data.records.length||(!published.loading&&!published.error)">
            <CreatorEntry
              v-for="entry in published.data.records"
              :key="entry.id"
              :entry="entry"
              :pending="deleting.has(CreatorTab.PUBLISHED+entry.id)"
              @open="openPost"
              @delete="remove($event,false)"
            />
            <p
              v-if="!published.data.records.length"
              class="creator-empty"
            >
              {{ t('creator.emptyPosts') }}
            </p>
            <FeedPagination
              v-if="totalPages>1"
              ref="pagination"
              :page="page"
              :total-pages="totalPages"
              @page="change({page:String($event)})"
              @top="backToTop"
            />
          </template>
        </div>
        <div
          v-else
          class="creator-list"
          role="tabpanel"
          :aria-busy="drafts.loading"
        >
          <PageState
            v-if="drafts.loading||drafts.error"
            :state="drafts.error?'error':'loading'"
            @retry="retry('drafts')"
          />
          <template v-else>
            <CreatorEntry
              v-for="entry in drafts.data"
              :key="entry.id"
              :entry="entry"
              draft
              :pending="deleting.has(CreatorTab.DRAFTS+entry.id)"
              @delete="remove($event,true)"
            /><p
              v-if="!drafts.data.length"
              class="creator-empty"
            >
              {{ t('creator.emptyDrafts') }}
            </p>
          </template>
        </div>
        <div
          class="creator-end"
          aria-hidden="true"
        >
          <span
            class="creator-star"
            :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
          /><span>{{ t('creator.face') }}</span><img
            :src="detailFlower"
            alt=""
          >
        </div>
      </section>
    </section>
  </CommunityLayout>
</template>
<script src="./CreatorView.js"></script>
<style lang="scss" scoped src="./CreatorView.scss"></style>
