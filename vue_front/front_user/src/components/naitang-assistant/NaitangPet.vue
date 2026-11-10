<template>
  <span
    class="naitang-pet"
    aria-hidden="true"
  >
    <img
      :key="sequence"
      :data-sequence="sequence"
      :src="clip.src"
      alt=""
      draggable="false"
      @load="schedule"
      @error="fallback"
    >
  </span>
</template>
<script src="./NaitangPet.js"></script>
<style lang="scss" scoped src="./NaitangPet.scss"></style>
