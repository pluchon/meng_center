<template>
  <el-config-provider :locale="elementLocale">
    <AuthCardSwap />
    <GlowCursor v-if="showCursor" />
    <FavoritePicker v-if="favorites.open" />
  </el-config-provider>
</template>
<script src="./App.js"></script>
<style lang="scss" scoped src="./App.scss"></style>
