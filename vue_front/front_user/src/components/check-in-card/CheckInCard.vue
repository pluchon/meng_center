<template>
  <section
    class="check-in-card"
    :aria-label="t('home.checkIn')"
  >
    <h2>{{ t('home.checkIn') }}</h2>
    <PageState
      v-if="growth.loading && !growth.data"
      state="loading"
    />
    <div
      v-else-if="growth.error"
      class="growth-error"
    >
      <p>{{ t('home.growthFailed') }}</p><el-button @click="growth.load()">
        {{ t('home.retry') }}
      </el-button>
    </div>
    <template v-else-if="growth.data">
      <div class="check-in-week">
        <div
          v-for="(day,index) in growth.data.week"
          :key="day.date"
          class="check-day"
        >
          <span
            class="day-circle"
            :class="{checked:day.checkedIn,today:day.date===growth.data.today&&!day.checkedIn}"
            :aria-label="day.date"
          ><el-icon v-if="day.checkedIn"><Check /></el-icon></span><small>{{ day.date===growth.data.today?t('home.today'):t('home.weekdays.'+index) }}</small>
        </div>
      </div>
      <el-button
        class="check-in-button"
        type="primary"
        :loading="growth.sending"
        :disabled="growth.data.checkedIn"
        @click="checkIn"
      >
        {{ t(growth.data.checkedIn?'home.checkedIn':'home.checkInButton') }}
      </el-button>
    </template>
  </section>
</template>
<script src="./CheckInCard.js"></script>
<style lang="scss" scoped src="./CheckInCard.scss"></style>
