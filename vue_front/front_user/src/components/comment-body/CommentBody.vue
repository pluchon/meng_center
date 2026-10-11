<template>
  <p class="comment-body">
    <template
      v-for="(segment,index) in segments"
      :key="index"
    >
      <img
        v-if="segment.image"
        :src="segment.image"
        :alt="segment.text"
        loading="lazy"
        decoding="async"
      ><template v-else>
        {{ segment.text }}
      </template>
    </template>
  </p>
</template>
<script src="./CommentBody.js"></script>
<style lang="scss" scoped src="./CommentBody.scss"></style>
