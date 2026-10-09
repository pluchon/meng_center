<template>
  <span
    ref="anchor"
    class="caps-lock-anchor"
  >
    <span
      v-if="active"
      class="caps-lock-hint"
      role="status"
    >{{ t('auth.capsLock') }}</span>
  </span>
</template>
<script src="./CapsLockHint.js"></script>
<style lang="scss" scoped src="./CapsLockHint.scss"></style>
