<template>
  <span><template
    v-for="(segment,index) in parts"
    :key="index"
  ><mark v-if="segment.matched">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template></span>
</template>
<script src="./HighlightText.js"></script>
<style lang="scss" scoped src="./HighlightText.scss"></style>
