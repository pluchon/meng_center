<template>
  <el-dialog
    v-model="visible"
    :title="t('auth.captcha.title')"
    width="min(400px, 92vw)"
    class="meng-captcha-dialog"
    align-center
    :close-on-click-modal="false"
    @closed="cancel"
  >
    <div
      class="captcha"
      :class="'is-' + status"
    >
      <div
        ref="board"
        class="captcha-board"
        :class="{ 'is-shaking': shaking }"
        :style="boardStyle"
      >
        <template v-if="challenge">
          <img
            :key="challenge.id"
            class="background"
            :src="challenge.backgroundImage"
            :alt="t('auth.captcha.title')"
            draggable="false"
          >
          <div class="veil" />
          <div
            class="trail"
            :style="trailStyle"
          />
          <img
            class="piece"
            :src="challenge.templateImage"
            :alt="t('auth.captcha.slider')"
            :style="pieceStyle"
            draggable="false"
          >
        </template>
        <div
          v-if="loading"
          class="board-loading"
        >
          <el-icon class="spin">
            <Loading />
          </el-icon>
        </div>
      </div>
      <div class="message-line">
        <p
          v-if="message"
          class="message"
          :class="{ 'is-passed': passed }"
          role="alert"
        >
          {{ message }}
        </p>
      </div>
      <div class="controls">
        <div
          ref="rail"
          class="slider-rail"
        >
          <div
            class="slider-fill"
            :style="fillStyle"
          />
          <button
            class="slider-knob"
            type="button"
            :disabled="busy"
            :aria-label="t('auth.captcha.slider')"
            :style="knobStyle"
            @pointerdown="startDrag"
            @pointermove="moveDrag"
            @pointerup="finishDrag"
            @pointercancel="abortDrag"
          >
            <el-icon>
              <Check v-if="passed" />
              <Right v-else />
            </el-icon>
          </button>
        </div>
        <button
          class="refresh"
          type="button"
          :disabled="busy"
          :aria-label="t('auth.captcha.refresh')"
          :title="t('auth.captcha.refresh')"
          @click="reload"
        >
          <el-icon :class="{ spin: loading }">
            <RefreshRight />
          </el-icon>
        </button>
      </div>
    </div>
  </el-dialog>
</template>
<script src="./CaptchaDialog.js"></script>
<style lang="scss" scoped src="./CaptchaDialog.scss"></style>
