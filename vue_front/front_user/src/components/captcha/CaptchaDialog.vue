<template>
  <el-dialog
    v-model="visible"
    :title="t('auth.captcha.title')"
    width="min(400px, 92vw)"
    class="meng-captcha-dialog"
    :close-on-click-modal="false"
    @closed="cancel"
  >
    <PageState
      v-if="loading"
      state="loading"
    />
    <template v-else-if="challenge">
      <p class="hint">
        {{ t('auth.captcha.hint') }}
      </p>
      <div
        ref="board"
        class="captcha-board"
        :style="{
          aspectRatio: challenge.backgroundImageWidth + '/' + challenge.backgroundImageHeight,
        }"
      >
        <img
          class="background"
          :src="challenge.backgroundImage"
          :alt="t('auth.captcha.title')"
        >
        <img
          class="piece"
          :src="challenge.templateImage"
          :alt="t('auth.captcha.slider')"
          :style="{ width: pieceWidth + '%', left: position + '%' }"
        >
      </div>
      <div
        ref="rail"
        class="slider-rail"
      >
        <button
          class="slider-knob"
          type="button"
          :disabled="checking"
          :aria-label="t('auth.captcha.slider')"
          :style="knobStyle"
          @pointerdown="startDrag"
          @pointermove="moveDrag"
          @pointerup="finishDrag"
          @pointercancel="abortDrag"
        >
          <el-icon><Right /></el-icon>
        </button>
      </div>
    </template>
    <p
      v-if="failure"
      class="failure"
      role="alert"
    >
      {{ failure }}
    </p>
    <el-button
      :disabled="loading || checking"
      @click="refresh"
    >
      {{ t('auth.captcha.refresh') }}
    </el-button>
  </el-dialog>
</template>
<script src="./CaptchaDialog.js"></script>
<style lang="scss" scoped src="./CaptchaDialog.scss"></style>
