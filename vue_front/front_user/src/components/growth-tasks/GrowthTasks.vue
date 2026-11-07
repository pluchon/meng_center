<template>
  <div class="growth-tasks">
    <Transition
      name="task-page"
      mode="out-in"
    >
      <div
        :key="page"
        class="task-list"
      >
        <div
          v-for="(task,index) in visible"
          :key="task.source"
          :class="['task','tone-'+SourceTones[task.source]]"
          :style="{'--i':index}"
        >
          <div class="task-title">
            <span class="task-icon"><img
              :src="icons[task.source]"
              alt=""
              aria-hidden="true"
            ></span><strong>{{ t('growth.source.'+task.source) }}</strong><span>{{ t('growth.today') }} <b>{{ '+'+task.earned }}</b></span>
          </div>
          <div class="task-status">
            <span
              :class="{done:task.count>0}"
              :title="t('growth.rewardProgress',{count:task.count,max:task.dailyMax})"
            ><img
              v-if="task.count>0"
              :src="checkIcon"
              class="done-icon"
              alt=""
              aria-hidden="true"
            >{{ task.count>0?t('growth.done'):task.source===ExpSource.CHECK_IN?t('growth.notSigned'):t('growth.notDone') }}</span><span>{{ t('growth.cumulative') }} <b>{{ task.activeDays }}</b> {{ t('growth.daysUnit') }}</span><router-link
              v-if="task.source!==ExpSource.CHECK_IN"
              :to="{name:task.source===ExpSource.POST?RouteName.POST_NEW:RouteName.HOME}"
            >
              {{ task.source===ExpSource.POST?t('growth.newPost'):t('growth.visit') }}
            </router-link>
          </div>
        </div>
      </div>
    </Transition>
    <FeedPagination
      class="task-pager"
      :page="page"
      :total-pages="totalPages"
      compact
      @page="page=$event"
    />
  </div>
</template>
<script src="./GrowthTasks.js"></script>
<style lang="scss" scoped src="./GrowthTasks.scss"></style>
