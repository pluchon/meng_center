<template>
  <section
    :class="['growth-panel','tone-'+tone]"
    :style="{'--growth-sparkle':'url('+JSON.stringify(communitySparkle)+')'}"
  >
    <span
      class="panel-tape"
      :class="'tone-'+(tapeTone||tone)"
      aria-hidden="true"
    />
    <img
      class="panel-bubble"
      :src="detailBubble"
      alt=""
    >
    <img
      class="panel-flower"
      :src="detailFlower"
      alt=""
    >
    <span
      class="panel-star"
      aria-hidden="true"
    />
    <span
      v-if="sticker&&!inlineSticker"
      class="panel-sticker"
      :class="'tone-'+(stickerTone||tone)"
      aria-hidden="true"
    >{{ sticker }}</span>
    <header
      v-if="title"
      class="panel-heading"
    >
      <h2>
        <span
          class="heading-icon"
          aria-hidden="true"
        ><img
          v-if="icon"
          :src="icon"
          alt=""
        ></span><span class="heading-title">{{ title }}</span><span
          class="heading-star"
          aria-hidden="true"
        /><span
          v-if="inlineSticker&&sticker"
          class="heading-sticker"
          :class="'tone-'+(stickerTone||tone)"
          aria-hidden="true"
        >{{ sticker }}</span>
      </h2>
      <slot name="actions" />
    </header>
    <slot />
  </section>
</template>
<script src="./GrowthPanel.js"></script>
<style lang="scss" scoped src="./GrowthPanel.scss"></style>
