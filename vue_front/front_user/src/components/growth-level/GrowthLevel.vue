<template>
  <div
    class="growth-level"
    :style="{'--growth-sparkle':'url('+JSON.stringify(communitySparkle)+')'}"
  >
    <div class="level-summary">
      <img
        class="level-avatar"
        :src="avatar||userAvatar"
        :alt="t('growth.avatar',{name})"
      >
      <div class="level-info">
        <div class="level-name-row">
          <h1>{{ name }}</h1><span class="level-name">{{ levelName(account.level) }}</span><span
            class="level-star"
            aria-hidden="true"
          /><strong class="experience-total">{{ shown.exp }}<small v-if="account.nextLevelExp!=null">{{ ' / '+account.nextLevelExp }}</small><small v-else>{{ t('growth.maxLevel') }}</small></strong>
        </div>
        <div class="experience-row">
          <span class="level-badge">{{ t('growth.level',{level:account.level}) }}</span><div
            class="experience-track"
            role="progressbar"
            :aria-valuenow="progress"
            :aria-valuemin="0"
            :aria-valuemax="100"
            :aria-label="t('growth.road')"
          >
            <span :style="{width:shownProgress+'%'}"><i
              class="progress-star"
              aria-hidden="true"
            ><span class="level-star" /></i></span>
          </div><span
            v-if="account.nextLevelExp!=null"
            class="next-level"
          >{{ t('growth.level',{level:account.level+1}) }}</span>
        </div>
      </div>
    </div>
    <PageState
      v-if="roadError||(roadLoading&&!levels.length)"
      :state="roadError?'error':'loading'"
      @retry="$emit('retry')"
    />
    <div
      v-else-if="levels.length"
      ref="road"
      class="road-scroll"
      tabindex="0"
      role="group"
      :aria-label="t('growth.road')"
    >
      <div
        class="road"
        :style="{'--level-count':levels.length}"
      >
        <svg
          class="road-line"
          :viewBox="'0 0 '+width+' 54'"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <defs><linearGradient id="growth-road-gradient"><stop
            v-for="(level,index) in levels"
            :key="level.level"
            :offset="index/Math.max(1,levels.length-1)"
            :class="'tone-'+GrowthTones[index%GrowthTones.length]"
          /></linearGradient><clipPath id="growth-road-progress"><rect
            class="road-clip"
            :style="{width:(revealed?(width-100)*roadProgress/100+50:0)+'px'}"
            height="54"
          /></clipPath></defs>
          <path
            class="road-unreached"
            :d="path"
          />
          <path
            class="road-reached"
            :d="path"
            clip-path="url(#growth-road-progress)"
          />
        </svg>
        <ol>
          <li
            v-for="(level,index) in levels"
            :key="level.level"
            :class="['tone-'+GrowthTones[index%GrowthTones.length],{passed:level.level<=account.level,current:level.level===account.level}]"
            :style="{'--i':index}"
          >
            <span
              class="road-number"
              :aria-current="level.level===account.level?'step':undefined"
            >{{ level.level }}<span
              v-if="level.level===account.level"
              class="current-label"
            >{{ t('growth.currentFace') }}</span></span>
            <strong>{{ levelName(level.level) }}</strong><small>{{ level.requiredExp }}</small>
          </li>
        </ol>
      </div>
    </div>
  </div>
</template>
<script src="./GrowthLevel.js"></script>
<style lang="scss" scoped src="./GrowthLevel.scss"></style>
