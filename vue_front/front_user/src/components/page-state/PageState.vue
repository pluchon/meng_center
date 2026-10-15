<template>
  <div
    class="page-state"
    :class="'size-' + size"
    role="status"
  >
    <span
      v-if="state === 'loading'"
      class="state-art state-loading"
      :style="{ backgroundImage: `url(${loadingSprite})` }"
      aria-hidden="true"
    />
    <img
      v-else
      class="state-art"
      :src="image"
      alt=""
    >
    <el-button
      v-if="state === 'error'"
      class="state-action tone-pink"
      @click="$emit('retry')"
    >
      {{ text || t('common.error') }}
    </el-button>
    <router-link
      v-else-if="state === 'unauthenticated'"
      class="state-action tone-violet"
      :to="{ name: RouteName.LOGIN }"
    >
      {{ line }}
    </router-link>
    <p v-else>
      {{ line }}
    </p>
  </div>
</template>
<script src="./PageState.js"></script>
<style lang="scss" scoped src="./PageState.scss"></style>
