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
    <ImageViewer
      v-if="previewIndex!==null"
      :urls="viewerUrls"
      :thumbs="viewerThumbs"
      :initial-index="previewIndex"
      @close="previewIndex=null"
    />
  </div>
</template>
<script src="./PostImages.js"></script>
<style lang="scss" scoped src="./PostImages.scss"></style>
