<template>
  <button
    class="naitang-voice"
    :class="{ inline, playing }"
    type="button"
    :aria-label="t(playing ? 'companion.stopVoice' : 'companion.playVoice')"
    :aria-pressed="playing"
    @click="$emit('toggle')"
  >
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      aria-hidden="true"
    >
      <circle
        cx="3.4"
        cy="8"
        r="1.3"
        fill="currentColor"
        stroke="none"
      />
      <path
        class="wave near"
        d="M6.6 5.2a4 4 0 0 1 0 5.6"
      />
      <path
        class="wave far"
        d="M9.6 2.8a7.4 7.4 0 0 1 0 10.4"
      />
    </svg>
    <span>{{ duration }}</span>
  </button>
</template>
<script src="./NaitangVoice.js"></script>
<style lang="scss" scoped src="./NaitangVoice.scss"></style>
