<template>
  <div class="growth-tasks">
    <div
      v-for="task in tasks"
      :key="task.source"
      :class="['task','tone-'+SourceTones[task.source]]"
    >
      <div class="task-title">
        <component
          :is="icons[task.source]"
          :size="20"
          aria-hidden="true"
        /><strong>{{ t('growth.source.'+task.source) }}</strong><span>{{ t('growth.today') }} <b>{{ '+'+task.earned }}</b></span>
      </div>
      <div class="task-status">
        <span
          :class="{done:task.count>0}"
          :title="t('growth.rewardProgress',{count:task.count,max:task.dailyMax})"
        ><IconCheck
          v-if="task.count>0"
          :size="13"
        />{{ task.count>0?t('growth.done'):task.source===ExpSource.CHECK_IN?t('growth.notSigned'):t('growth.notDone') }}</span><span>{{ t('growth.cumulative') }} <b>{{ task.activeDays }}</b> {{ t('growth.daysUnit') }}</span><router-link
          v-if="task.source!==ExpSource.CHECK_IN"
          :to="{name:task.source===ExpSource.POST?RouteName.POST_NEW:RouteName.HOME}"
        >
          {{ task.source===ExpSource.POST?t('growth.newPost'):t('growth.visit') }}
        </router-link>
      </div>
    </div>
    <div class="weekly-heading">
      <strong>{{ t('growth.recent',{days:7}) }}</strong><span>{{ '+'+total }}</span>
    </div>
    <PageState
      v-if="stats.error||(stats.loading&&!stats.data)"
      :state="stats.error?'error':'loading'"
      @retry="$emit('retry')"
    />
    <div
      v-else
      class="weekly-bars"
      :aria-label="t('growth.chartDescription',{days:7})"
      :aria-busy="stats.loading"
    >
      <div
        v-for="row in weekly"
        :key="row.date"
        :class="['weekly-day','tone-'+weekdayTone(row.date)]"
      >
        <strong>{{ row.amount }}</strong><span :style="{height:(row.amount?Math.max(8,row.amount/maximum*76):3)+'px'}" /><small>{{ weekday(row.date) }}</small>
      </div>
    </div>
  </div>
</template>
<script src="./GrowthTasks.js"></script>
<style lang="scss" scoped src="./GrowthTasks.scss"></style>
