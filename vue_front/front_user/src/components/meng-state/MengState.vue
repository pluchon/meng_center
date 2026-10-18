<template>
  <PageState
    class="meng-state"
    :class="{'page-state-full':page,compact}"
    :state="loading?'loading':error?.status===401?'unauthenticated':empty?'empty':'error'"
    :art="art"
    :text="label"
    :error="error"
    :size="page?'page':compact?'small':'large'"
    :loading-sprite-url="sprite"
    @retry="retry"
  />
</template>
<script src="./MengState.js"></script>
<style lang="scss" scoped src="./MengState.scss"></style>
