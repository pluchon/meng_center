<template>
  <div
    class="game-map"
    :class="{ open }"
  >
    <button
      type="button"
      class="map-frame"
      :aria-label="open ? t('game.map.close') : t('game.map.open')"
      @click="$emit('toggle')"
    >
      <canvas
        ref="canvas"
        :width="size"
        :height="size"
      />
    </button>
    <ul
      v-if="open"
      class="map-legend"
    >
      <li
        v-for="item in legend"
        :key="item.key"
      >
        <i :style="{ background: item.color }" />{{ t('game.map.' + item.key) }}
      </li>
    </ul>
  </div>
</template>
<script src="./GameMap.js"></script>
<style lang="scss" scoped src="./GameMap.scss"></style>
