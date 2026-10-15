<template>
  <DialogShell
    :model-value="true"
    :title="t('account.pointsTitle')"
    :icon="IconCoin"
    :face="t('account.pointsFace')"
    width="min(1040px,calc(100vw - 32px))"
    @close="$emit('close')"
  >
    <PageState
      v-if="points.error"
      class="points-state-full"
      state="error"
      size="large"
      @retry="reload"
    />
    <PageState
      v-else-if="!points.data"
      class="points-state-full"
      state="loading"
      size="large"
    />
    <div
      v-else
      class="points-body"
    >
      <div class="points-left">
        <section class="points-balance">
          <div class="balance-main">
            <span
              class="balance-coin"
              aria-hidden="true"
            ><el-icon><IconCoin /></el-icon></span>
            <div class="balance-number">
              <span>{{ t('account.pointsBalance') }}</span>
              <b>{{ points.data.balance }}</b>
            </div>
            <div class="balance-recent">
              <div>
                <span>{{ t('account.pointsToday') }}</span><b>{{ signed(today) }}</b>
              </div>
              <i aria-hidden="true" />
              <div>
                <span>{{ t('account.pointsWeek') }}</span><b>{{ signed(week) }}</b>
              </div>
            </div>
          </div>
          <div class="balance-rules">
            <div
              v-for="rule in points.data.rules"
              :key="rule.source"
              class="balance-rule"
            >
              <div>
                <span class="rule-name"><i :class="'tone-' + sourceTone(rule.source)" />{{ t('account.sources.' + rule.source) }}</span>
                <small>{{ t(ruleKeys[rule.source], { count: rule.dailyMax }) }}</small>
              </div>
              <b>{{ signed(rule.amount) }}</b>
            </div>
          </div>
        </section>
        <section class="points-section">
          <header class="points-section-head">
            <h3>
              <i class="tone-mint" /><span>{{ t('account.pointsRange', { days }) }}</span>
            </h3>
            <div
              class="points-seg"
              role="group"
              :aria-label="t('account.pointsRange', { days })"
            >
              <button
                v-for="value in Points.DAYS"
                :key="value"
                type="button"
                :class="{ on: days === value }"
                :aria-pressed="days === value"
                @click="days = value"
              >
                <i :class="value === 7 ? 'tone-mint' : 'tone-blue'" />{{ t('account.rangeDays', { days: value }) }}
              </button>
            </div>
          </header>
          <PageState
            v-if="stats.error"
            class="points-state-chart"
            state="error"
            size="small"
            @retry="loadStats"
          />
          <PageState
            v-else-if="!stats.data"
            class="points-state-chart"
            state="loading"
            size="small"
          />
          <div
            v-else
            class="points-bars"
            :class="{ dense: days > 7, busy: stats.loading }"
            role="img"
            :aria-label="t('account.pointsRangeAria', { days })"
          >
            <div
              v-for="(row, index) in shownDays"
              :key="row.date"
              class="points-bar"
              :title="row.date.slice(5) + ' +' + row.amount"
            >
              <span class="bar-value">{{ row.amount }}</span>
              <span
                class="bar-fill"
                :class="['tone-' + barTone(index), { zero: !row.amount }]"
                :style="{ height: barHeight(row.amount) + '%' }"
              />
              <span class="bar-label">{{ barLabel(row, index) }}</span>
            </div>
          </div>
        </section>
        <section class="points-section">
          <header class="points-section-head">
            <h3>
              <i class="tone-violet" /><span>{{ t('account.pointsShare') }}</span>
            </h3>
          </header>
          <div
            class="share-stack"
            role="img"
            :aria-label="t('account.pointsShareAria', { days })"
          >
            <span
              v-for="item in shownSources"
              :key="item.source"
              :class="'tone-' + sourceTone(item.source)"
              :style="{ flex: item.amount + ' 1 0' }"
            />
          </div>
          <div class="share-legend">
            <span
              v-for="item in shownSources"
              :key="item.source"
              :class="'ink-' + sourceTone(item.source)"
            >
              <i :class="'tone-' + sourceTone(item.source)" />{{ t('account.sources.' + item.source) }}<b>{{ signed(item.amount) }}</b>
            </span>
          </div>
        </section>
      </div>
      <section class="points-ledger">
        <header class="points-section-head">
          <h3>
            <i class="tone-gold" /><span>{{ t('account.pointsLedger') }}</span>
          </h3>
          <div
            class="points-seg"
            role="group"
            :aria-label="t('account.pointsSource')"
          >
            <button
              v-for="item in sourceFilters"
              :key="item.key"
              type="button"
              :class="{ on: source === item.key }"
              :aria-pressed="source === item.key"
              @click="pickSource(item.key)"
            >
              <i :class="'tone-' + item.tone" />{{ t(item.label) }}
            </button>
          </div>
        </header>
        <PageState
          v-if="records.loading && !records.data.records.length"
          class="ledger-state"
          state="loading"
          size="large"
        />
        <PageState
          v-else-if="records.error"
          class="ledger-state"
          state="error"
          size="large"
          @retry="loadRecords"
        />
        <PageState
          v-else-if="!records.data.records.length"
          class="ledger-state"
          state="empty"
          art="growth"
          size="large"
          :text="t('account.pointsEmpty')"
        />
        <ul
          v-else
          class="ledger-list"
          :aria-label="t('account.pointsLedger')"
        >
          <li
            v-for="(row, index) in records.data.records"
            :key="page + '-' + index"
            class="ledger-row"
          >
            <span
              class="ledger-icon"
              :class="'tone-' + sourceTone(row.source)"
              aria-hidden="true"
            ><el-icon><component :is="sourceIcon(row.source)" /></el-icon></span>
            <div>
              <strong>{{ t('account.sources.' + row.source) }}</strong>
              <small>{{ date(row.time) }}</small>
            </div>
            <span class="ledger-amount"><b>{{ signed(row.amount) }}</b>{{ t('account.pointsPer') }}</span>
          </li>
        </ul>
        <div class="ledger-pager-slot">
          <FeedPagination
            v-if="totalPages > 1"
            :page="page"
            :total-pages="totalPages"
            :show-top="false"
            compact
            @page="page = $event"
          />
        </div>
      </section>
    </div>
  </DialogShell>
</template>
<script src="./PointsDialog.js"></script>
<style lang="scss" scoped src="./PointsDialog.scss"></style>
