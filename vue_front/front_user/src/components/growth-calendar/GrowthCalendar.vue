<template>
  <div class="growth-calendar">
    <div class="calendar-stage">
      <Transition
        name="calendar-shift"
        mode="out-in"
      >
        <div
          :key="data.month"
          class="calendar-frame"
          role="group"
          :aria-label="t('growth.calendarDescription')"
        >
          <div
            class="weekdays"
            aria-hidden="true"
          >
            <span
              v-for="(day,index) in weekdays"
              :key="day"
              :class="'weekday-'+index"
            >{{ day }}</span>
          </div>
          <div class="calendar-grid">
            <template
              v-for="cell in cells"
              :key="cell.key"
            >
              <span
                v-if="!cell.day"
                class="blank"
                aria-hidden="true"
              />
              <span
                v-else
                :class="['calendar-day','tone-'+cell.tone,{signed:cell.checked,now:cell.today&&!cell.checked,future:cell.future}]"
                :aria-label="cell.checked?t('growth.checkedDay',{date:cell.key}):cell.today?t('growth.todayDay',{date:cell.key}):cell.key"
                :aria-current="cell.today?'date':undefined"
              >{{ cell.day }}</span>
            </template>
          </div>
        </div>
      </Transition>
    </div>
    <footer class="calendar-controls">
      <div class="month-controls">
        <button
          type="button"
          :aria-label="t('growth.previousMonth')"
          @click="move(-1)"
        >
          <IconChevronLeft :size="19" />
        </button><strong>{{ monthLabel }}</strong><button
          type="button"
          :disabled="data.month>=data.today.slice(0,7)"
          :aria-label="t('growth.nextMonth')"
          @click="move(1)"
        >
          <IconChevronRight :size="19" />
        </button>
      </div>
      <button
        class="check-in"
        type="button"
        :disabled="checked||sending||reward==null"
        :aria-busy="sending"
        @click="$emit('check')"
      >
        <IconCheck
          v-if="checked"
          :size="22"
        /><IconCalendarCheck
          v-else
          :size="22"
        />{{ checked?t('growth.checkedIn'):t('growth.checkIn',{reward:reward??0}) }}
      </button>
    </footer>
  </div>
</template>
<script src="./GrowthCalendar.js"></script>
<style lang="scss" scoped src="./GrowthCalendar.scss"></style>
