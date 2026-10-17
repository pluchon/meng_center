<template>
  <div class="meng-home">
    <header class="house-banner">
      <span
        class="banner-cloud cloud-one"
        :style="{maskImage: 'url('+cloud+')'}"
        aria-hidden="true"
      />
      <span
        class="banner-cloud cloud-two"
        :style="{maskImage: 'url('+cloud+')'}"
        aria-hidden="true"
      />
      <img
        :src="waveBack"
        alt=""
        class="banner-wave back-wave"
        aria-hidden="true"
      >
      <img
        :src="waveFront"
        alt=""
        class="banner-wave"
        aria-hidden="true"
      >
      <div class="banner-title">
        <div
          class="banner-avatars"
          aria-hidden="true"
        >
          <img
            v-for="c in characters.data"
            :key="c.code"
            :src="avatar(c.code)"
            alt=""
            :class="c.code.toLowerCase()"
            width="60"
            height="60"
          >
        </div>
        <h1><span>{{ t('meng.meng') }}</span>{{ t('meng.house') }}<IconSparkles /></h1>
      </div>
      <div
        class="banner-greetings"
        aria-hidden="true"
      >
        <span>{{ t('meng.welcome') }}</span><span>{{ t('meng.meow') }}</span>
      </div>
      <div class="banner-actions">
        <span
          v-if="usage.usage"
          class="meng-button mint"
        ><IconMessageCircle />{{ t('meng.remaining',{count:usage.usage.messages}) }}</span>
        <button
          type="button"
          class="meng-button violet"
          @click="openDialog('all')"
        >
          <IconFiles />{{ t('meng.works') }}
        </button>
      </div>
    </header>
    <div class="character-grid">
      <article
        v-for="(c,index) in characters.data"
        :key="c.code"
        class="character-card"
        :class="c.code.toLowerCase()"
        :style="{'--card-index':index}"
      >
        <div class="character-portrait">
          <img
            :src="portrait(c.code,c.stage)"
            :alt="c.name"
            fetchpriority="high"
            decoding="async"
            width="768"
            height="1024"
          >
          <span class="personality">{{ t('meng.'+c.code+'.type') }}</span>
          <span class="greeting">{{ t('meng.'+c.code+'.greeting') }}</span>
        </div>
        <div class="character-name">
          <div><span class="character-symbol"><component :is="symbols[index]" /></span><h2>{{ c.name }}</h2><span class="personality-tag"><IconSparkles />{{ t('meng.'+c.code+'.tag') }}</span></div><p>{{ t('meng.'+c.code+'.intro') }}</p>
        </div>
        <div class="relationship-card">
          <div class="relationship-top">
            <span class="stage-stamp"><IconHeart />{{ c.stageName }}</span><small><IconCalendar />{{ c.acquaintanceDays?t('meng.days',{count:c.acquaintanceDays}):t('meng.never') }}</small>
          </div>
          <div
            class="relationship-road"
            role="img"
            :aria-label="c.stageName"
          >
            <span class="road-track" /><span
              class="road-progress"
              :style="{width:progress(c)+'%'}"
            />
            <i
              v-for="(stage,i) in stages"
              :key="stage"
              :class="{reached:i<c.stage}"
              :style="{left:i*25+'%'}"
            />
            <span
              class="road-heart"
              :style="{left:progress(c)+'%'}"
            ><IconHeartFilled /></span>
          </div>
          <div class="road-labels">
            <span>{{ c.stageName }}</span><span>{{ stages[Math.min(c.stage,4)] }}</span>
          </div>
          <div
            v-if="c.lastMessage"
            class="latest-message"
          >
            <img
              :src="avatar(c.code)"
              alt=""
              width="28"
              height="28"
            ><span>{{ c.lastMessage }}</span><small v-if="c.lastMessageTime">{{ relativeTime(c.lastMessageTime) }}</small>
          </div>
          <p
            v-else
            class="first-message"
          >
            {{ t('meng.first') }}
          </p>
        </div>
        <div class="character-actions">
          <button
            type="button"
            class="meng-button primary"
            @click="goChat(c.code)"
          >
            <IconMessageCircle />{{ t(c.lastMessage?'meng.chat':'meng.hello') }}
          </button><button
            type="button"
            class="meng-button secondary"
            @click="openBond(c.code)"
          >
            {{ t('meng.bond') }}
          </button>
        </div>
      </article>
    </div>
    <aside class="house-tips">
      <span class="tip-label">{{ t('meng.tip') }}</span><span class="tip-icon"><img
        :src="aiGuide"
        alt=""
        width="72"
        height="72"
        aria-hidden="true"
      ></span>
      <div>
        <h2>{{ t('meng.ai') }}</h2><div class="tip-tags">
          <span><IconMessageCircle />{{ t('meng.tipChat') }}</span><span class="violet"><IconPalette />{{ t('meng.tipWork') }}</span><span class="pink"><IconHeart />{{ t('meng.tipMemory') }}</span><p><IconPlant />{{ t('meng.rest') }}</p>
        </div>
      </div>
    </aside>
  </div>
</template>
<script src="./MengHome.js"></script>
<style lang="scss" scoped src="./MengHome.scss"></style>
