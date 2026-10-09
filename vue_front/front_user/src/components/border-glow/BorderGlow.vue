<template>
  <span
    ref="glow"
    class="border-glow"
    aria-hidden="true"
  >
    <span class="glow-fog">
      <span class="glow-band glow-band-wide" />
    </span>
    <span class="glow-halo">
      <span class="glow-band" />
    </span>
    <span class="glow-rim" />
  </span>
</template>
<script src="./BorderGlow.js"></script>
<style lang="scss" scoped src="./BorderGlow.scss"></style>
