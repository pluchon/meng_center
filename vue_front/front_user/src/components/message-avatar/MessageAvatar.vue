<template>
  <el-avatar
    class="message-avatar"
    :class="tone"
    :size="size"
    :src="user.avatar||undefined"
    :alt="name"
  >
    {{ name.slice(0,1) }}
  </el-avatar>
</template>
<script src="./MessageAvatar.js"></script>
<style lang="scss" scoped src="./MessageAvatar.scss"></style>
