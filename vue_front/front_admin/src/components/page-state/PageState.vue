<template>
  <div
    class="page-state"
    role="status"
  >
    <p>{{ t('common.' + state) }}</p>
    <el-button
      v-if="state === 'error'"
      @click="$emit('retry')"
    >
      {{ t('common.retry') }}
    </el-button>
    <router-link
      v-if="state === 'unauthenticated'"
      :to="{ name: RouteName.LOGIN }"
    >
      {{ t('common.login') }}
    </router-link>
  </div>
</template>
<script src="./PageState.js"></script>
<style lang="scss" scoped src="./PageState.scss"></style>
