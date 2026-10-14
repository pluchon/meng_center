<template>
  <el-dialog
    v-model="open"
    width="1080px"
    class="image-viewer"
    modal-class="image-viewer-overlay"
    align-center
    :show-close="false"
    append-to-body
    destroy-on-close
    @closed="$emit('close')"
  >
    <template #header>
      <div class="viewer-head">
        <span
          class="viewer-icon"
          aria-hidden="true"
        ><img
          :src="messagePhotoIcon"
          alt=""
        ></span>
        <h2><span>{{ t('viewer.title') }}</span></h2>
        <span
          :key="'c'+index"
          class="viewer-pill viewer-counter"
        >{{ t('viewer.counter',{index:index+1,total}) }}</span>
        <span
          v-if="size"
          :key="'s'+index"
          class="viewer-pill viewer-size"
        >{{ t('viewer.original',size) }}</span>
        <button
          type="button"
          class="viewer-btn viewer-close"
          :aria-label="t('viewer.close')"
          @click="open=false"
        >
          <img
            :src="messageCloseIcon"
            alt=""
          >
        </button>
      </div>
    </template>
    <div
      class="viewer-stage"
      @wheel.prevent="wheel"
    >
      <div
        v-if="!ready&&!failed"
        class="viewer-loading"
        aria-hidden="true"
      >
        <i /><i /><i />
      </div>
      <Transition
        :name="'slide-'+direction"
        mode="out-in"
      >
        <img
          v-if="!failed"
          :key="index"
          :class="['viewer-img','zoom-'+zoom,'rotate-'+rotation]"
          :src="urls[index]"
          :alt="t('viewer.title')"
          draggable="false"
          @load="loaded"
          @error="failed=true"
        >
        <div
          v-else
          :key="'f'+index"
          class="viewer-failed"
          role="status"
        >
          <el-icon><PictureIcon /></el-icon>{{ t('viewer.failed') }}
        </div>
      </Transition>
      <button
        type="button"
        class="viewer-arrow viewer-prev"
        :aria-label="t('viewer.prev')"
        :disabled="index===0"
        @click="go(index-1)"
      >
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <button
        type="button"
        class="viewer-arrow viewer-next"
        :aria-label="t('viewer.next')"
        :disabled="index===total-1"
        @click="go(index+1)"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>
    </div>
    <div class="viewer-foot">
      <div
        v-if="total>1"
        class="viewer-strip"
      >
        <button
          v-if="total>VISIBLE"
          type="button"
          class="viewer-btn viewer-strip-step"
          :aria-label="t('viewer.stripPrev')"
          :disabled="strip===0"
          @click="shift(-3)"
        >
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div class="viewer-strip-window">
          <div
            class="viewer-strip-track"
            :style="{transform:'translateX('+(-strip*88)+'px)'}"
          >
            <button
              v-for="(thumb,i) in thumbList"
              :key="i"
              type="button"
              :class="['viewer-thumb','tilt-'+i%5,{current:i===index}]"
              :style="{'--i':i-strip}"
              :aria-label="t('viewer.nth',{index:i+1})"
              :aria-current="i===index"
              @click="go(i)"
            >
              <img
                :src="thumb"
                alt=""
                loading="lazy"
                decoding="async"
              >
            </button>
          </div>
        </div>
        <button
          v-if="total>VISIBLE"
          type="button"
          class="viewer-btn viewer-strip-step"
          :aria-label="t('viewer.stripNext')"
          :disabled="strip===maxStrip"
          @click="shift(3)"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
      <div class="viewer-tools">
        <button
          type="button"
          class="viewer-btn tone-blue"
          :aria-label="t('viewer.zoomOut')"
          :disabled="zoom===0"
          @click="zoom--"
        >
          <el-icon><ZoomOut /></el-icon>
        </button>
        <span
          :key="'z'+zoom"
          class="viewer-zoom"
        >{{ t('viewer.zoomValue',{value:ZOOMS[zoom]}) }}</span>
        <button
          type="button"
          class="viewer-btn tone-blue"
          :aria-label="t('viewer.zoomIn')"
          :disabled="zoom===ZOOMS.length-1"
          @click="zoom++"
        >
          <el-icon><ZoomIn /></el-icon>
        </button>
        <span
          class="viewer-divider"
          aria-hidden="true"
        />
        <button
          type="button"
          class="viewer-btn tone-violet"
          :aria-label="t('viewer.rotateLeft')"
          @click="rotation=(rotation+3)%4"
        >
          <el-icon><RefreshLeft /></el-icon>
        </button>
        <button
          type="button"
          class="viewer-btn tone-violet"
          :aria-label="t('viewer.rotateRight')"
          @click="rotation=(rotation+1)%4"
        >
          <el-icon><RefreshRight /></el-icon>
        </button>
        <span
          class="viewer-divider"
          aria-hidden="true"
        />
        <button
          type="button"
          class="viewer-btn viewer-fit"
          @click="reset"
        >
          <span><el-icon><FullScreen /></el-icon></span>{{ t('viewer.fit') }}
        </button>
      </div>
      <button
        type="button"
        class="viewer-btn viewer-download"
        :aria-label="t('viewer.download')"
        :title="t('viewer.download')"
        @click="download"
      >
        <el-icon><Download /></el-icon>
      </button>
    </div>
  </el-dialog>
</template>
<script src="./ImageViewer.js"></script>
<style lang="scss" src="./ImageViewer.scss"></style>
