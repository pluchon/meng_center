<template>
  <div
    class="message-images"
    :class="{album,expanded}"
  >
    <button
      v-if="album&&!expanded"
      type="button"
      class="album-cover"
      :aria-label="t('im.expand')"
      @click="expanded=true"
    >
      <img
        :src="imImage(images[0].id,true)"
        :alt="t('im.image')"
        loading="lazy"
        decoding="async"
      >
      <span>{{ t('im.album',{count:images.length-1}) }}</span>
    </button>
    <template v-else>
      <button
        v-for="(item,index) in images"
        :key="item.id"
        type="button"
        :aria-label="t('im.viewImage')"
        @click="preview=index"
      >
        <img
          :src="imImage(item.id,true)"
          :alt="t('im.image')"
          :width="item.width"
          :height="item.height"
          loading="lazy"
          decoding="async"
        >
      </button>
      <button
        v-if="album"
        type="button"
        class="album-collapse"
        @click="expanded=false"
      >
        {{ t('im.collapse') }}
      </button>
    </template>
    <el-image-viewer
      v-if="preview!==null"
      :url-list="urls"
      :initial-index="preview"
      hide-on-click-modal
      @close="preview=null"
    />
  </div>
</template>
<script src="./MessageImages.js"></script>
<style lang="scss" scoped src="./MessageImages.scss"></style>
