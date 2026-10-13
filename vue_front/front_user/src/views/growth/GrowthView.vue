<template>
  <CommunityLayout class="growth-layout">
    <div
      class="growth-background"
      aria-hidden="true"
    >
      <GradientWaves />
    </div>
    <section
      class="growth-view"
      :aria-label="t('growth.title')"
    >
      <GrowthPanel
        class="level-panel"
        tape-tone="pink"
        sticker-tone="gold"
        :sticker="t('growth.cheer')"
      >
        <PageState
          v-if="!growth.data"
          :state="stateOf(growth)"
          @retry="growth.load"
        />
        <GrowthLevel
          v-else
          :account="growth.data"
          :levels="overview.data?.levels??[]"
          :name="name"
          :avatar="user.current?.avatar??''"
          :road-loading="overview.loading"
          :road-error="overview.error"
          @retry="retryOverview"
        />
      </GrowthPanel>
      <div class="growth-middle">
        <GrowthPanel
          class="calendar-panel"
          :title="t('growth.calendar')"
          :icon="IconCalendarCheck"
          :sticker="t('growth.calendarFace')"
        >
          <template #actions>
            <div class="calendar-stickers">
              <span class="tone-gold"><IconSparkles :size="18" />{{ t('growth.streak') }} <b>{{ calendar.data?.streak??0 }}</b> {{ t('growth.daysUnit') }}</span><span class="tone-pink"><IconCalendarMonth :size="18" />{{ t('growth.monthCount') }} <b>{{ calendar.data?.count??0 }}</b> {{ t('growth.daysUnit') }}</span>
            </div>
          </template>
          <PageState
            v-if="calendar.error||!calendar.data"
            :state="stateOf(calendar)"
            @retry="loadCalendar"
          />
          <GrowthCalendar
            v-else
            :data="calendar.data"
            :checked="checked"
            :sending="growth.sending||signing"
            :reward="calendar.data.reward??overview.data?.tasks.find(task=>task.source==='CHECK_IN')?.reward??null"
            @month="moveMonth"
            @check="checkIn"
          />
        </GrowthPanel>
        <GrowthPanel
          class="tasks-panel"
          tone="gold"
          :title="t('growth.tasks')"
          :icon="IconFlag"
        >
          <template #actions>
            <span class="task-completed"><IconCheck :size="16" />{{ t('growth.completed',{count:completed,total:ExpSources.length}) }}</span>
          </template>
          <PageState
            v-if="overview.error||!overview.data"
            :state="stateOf(overview)"
            @retry="retryOverview"
          />
          <GrowthTasks
            v-else
            :tasks="overview.data.tasks"
            :stats="seven"
            @retry="retrySeven"
          />
        </GrowthPanel>
      </div>
      <GrowthPanel
        class="records-panel"
        tone="mint"
        tape-tone="pink"
        sticker-tone="gold"
        inline-sticker
        :title="t('growth.records')"
        :icon="IconReceipt"
        :sticker="t('growth.recordsFace')"
      >
        <template #actions>
          <div
            class="source-filter"
            :style="{'--filter-index':Math.max(0,ExpSources.indexOf(source)+1)}"
            role="group"
            :aria-label="t('growth.sourceFilter')"
          >
            <button
              type="button"
              :class="{selected:!source}"
              :aria-pressed="!source"
              @click="changeRecords({source:undefined,page:'1'})"
            >
              <span class="filter-dot" />{{ t('growth.all') }}
            </button><button
              v-for="kind in ExpSources"
              :key="kind"
              type="button"
              :class="['tone-'+SourceTones[kind],{selected:source===kind}]"
              :aria-pressed="source===kind"
              @click="changeRecords({source:kind,page:'1'})"
            >
              <span class="filter-dot" />{{ t('growth.source.'+kind) }}
            </button>
          </div>
        </template>
        <PageState
          v-if="records.error||(records.loading&&!records.data.records.length)"
          :state="stateOf(records)"
          @retry="loadRecords"
        />
        <template v-if="records.data.records.length||(!records.loading&&!records.error)">
          <ul
            class="experience-records"
            :aria-busy="records.loading"
          >
            <li
              v-for="(record,index) in records.data.records"
              :key="source+'-'+page+'-'+index"
              :class="'tone-'+SourceTones[record.source]"
              :style="{'--record-index':Math.min(index,5)}"
            >
              <span
                class="record-icon"
                aria-hidden="true"
              ><component
                :is="sourceIcons[record.source]"
                :size="20"
              /></span><span><strong>{{ t('growth.source.'+record.source) }}</strong><time
                :datetime="record.time"
                :title="recordTime(record.time)"
              ><IconClock :size="12" /><span>{{ recordTime(record.time) }}</span></time></span><b>{{ '+'+record.amount }}</b>
            </li>
          </ul>
          <PageState
            v-if="!records.data.records.length"
            state="empty"
          />
        </template>
        <footer
          ref="footer"
          class="records-controls"
        >
          <FeedPagination
            v-if="totalPages>1"
            :page="page"
            :total-pages="totalPages"
            :show-top="false"
            @page="nextPage"
          />
          <div class="record-actions">
            <button
              type="button"
              class="tone-gold"
              @click="unavailable"
            >
              <IconSparkles :size="18" />{{ t('growth.ai') }}
            </button><button
              type="button"
              class="tone-violet"
              aria-haspopup="dialog"
              :aria-expanded="visualOpen"
              @click="toggleVisual"
            >
              <IconChartPie :size="18" />{{ t('growth.visualize') }}
            </button>
          </div>
        </footer>
      </GrowthPanel>
    </section>
    <GrowthVisualization
      v-model:open="visualOpen"
      v-model:days="days"
      :stats="visibleStats"
      @retry="retryStats"
    />
  </CommunityLayout>
</template>
<script src="./GrowthView.js"></script>
<style lang="scss" scoped src="./GrowthView.scss"></style>
