<template>
  <div
    v-if="images.length"
    :class="['post-image-grid',{compact}]"
  >
    <button
      v-for="(image,index) in images"
      :key="image.id"
      type="button"
      :aria-label="t('editor.imageAlt')"
      @click="previewIndex=index"
    >
      <el-image
        :src="contentImage(image.thumbUrl)"
        :alt="t('editor.imageAlt')"
        fit="cover"
        loading="lazy"
        decoding="async"
      >
        <template #error>
          <el-icon><PictureIcon /></el-icon>
        </template>
      </el-image>
    </button>
    <el-image-viewer
      v-if="previewIndex!==null"
      teleported
      :url-list="images.map(image=>contentImage(image.url))"
      :initial-index="previewIndex"
      @close="previewIndex=null"
    />
  </div>
</template>
<script src="./PostImages.js"></script>
<style lang="scss" scoped src="./PostImages.scss"></style>
