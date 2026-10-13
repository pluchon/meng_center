<template>
  <div
    ref="host"
    class="shape-waves"
    :class="{ready}"
    aria-hidden="true"
  >
    <canvas ref="canvas" />
  </div>
</template>
<script src="./ShapeWaves.js"></script>
<style lang="scss" scoped src="./ShapeWaves.scss"></style>
