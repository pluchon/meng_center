<template>
  <div
    ref="stage"
    class="card-swap-stage"
    :class="{ swapping, 'auth-stage': sharedBackground }"
    :aria-busy="swapping"
  >
    <ParticleBackground v-if="sharedBackground" />
    <router-view v-slot="{ Component, route }">
      <Transition
        :css="false"
        @leave="leave"
        @enter="enter"
      >
        <component
          :is="Component"
          :key="pageKey(route)"
        />
      </Transition>
    </router-view>
  </div>
</template>
<script src="./AuthCardSwap.js"></script>
<style lang="scss" scoped src="./AuthCardSwap.scss"></style>
