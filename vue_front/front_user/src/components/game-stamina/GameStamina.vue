<template>
  <div
    v-show="visible"
    class="game-stamina"
    :class="{ exhausted }"
    :style="{ left: left + '%', top: top + '%' }"
    role="meter"
    :aria-label="t('game.stamina')"
    :aria-valuenow="Math.round(value)"
    aria-valuemin="0"
    :aria-valuemax="max"
  >
    <svg
      viewBox="0 0 44 44"
      aria-hidden="true"
    >
      <circle
        class="track"
        cx="22"
        cy="22"
        r="17"
      />
      <circle
        class="fill"
        cx="22"
        cy="22"
        r="17"
        :stroke-dasharray="dash"
      />
    </svg>
  </div>
</template>
<script src="./GameStamina.js"></script>
<style lang="scss" scoped src="./GameStamina.scss"></style>
