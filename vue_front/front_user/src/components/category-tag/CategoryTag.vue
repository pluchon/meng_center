<template>
  <span
    class="category-tag"
    :class="'tag-' + category.toLowerCase()"
  >{{ t('home.categories.' + category) }}</span>
</template>
<script src="./CategoryTag.js"></script>
<style lang="scss" scoped src="./CategoryTag.scss"></style>
