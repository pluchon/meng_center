<template>
  <p class="comment-body">
    <span
      v-if="texts.length"
      class="body-text"
    ><template
      v-for="(part,index) in parts"
      :key="index"
    ><b
      v-if="part.mention"
      class="body-mention"
    >{{ part.text }}</b><template v-else>{{ part.text }}</template></template></span>
    <span
      v-if="images.length"
      class="body-images"
    >
      <img
        v-for="(segment,index) in images"
        :key="index"
        :src="segment.image"
        :alt="segment.text"
        loading="lazy"
        decoding="async"
      >
    </span>
  </p>
</template>
<script src="./CommentBody.js"></script>
<style lang="scss" scoped src="./CommentBody.scss"></style>
