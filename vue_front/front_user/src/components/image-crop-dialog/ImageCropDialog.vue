<template>
  <DialogShell
    :model-value="true"
    class="crop-dialog"
    :icon="IconCrop"
    :title="title"
    tone="gold"
    :width="dialogWidth"
    @close="$emit('close')"
  >
    <div
      class="crop-body"
      :class="{ 'with-presets': presetsOpen }"
      :style="{ '--crop-main': frameWidth + 'px', '--crop-panel': panelWidth + 'px' }"
    >
      <div class="crop-main">
        <div
          ref="frame"
          class="crop-frame"
          :class="{ round }"
          :style="{ aspectRatio: String(aspect) }"
          @pointerdown="grab"
          @pointermove="drag"
          @pointerup="release"
          @pointercancel="release"
          @wheel.prevent="wheel"
        >
          <img
            v-if="url"
            :src="url"
            alt=""
            draggable="false"
            :style="{ width: natural.width * scale + 'px', translate: offset.x + 'px ' + offset.y + 'px' }"
            @load="loaded"
          >
          <span
            class="crop-grid"
            aria-hidden="true"
          />
          <span
            v-if="round"
            class="crop-round"
            aria-hidden="true"
          />
        </div>
        <label class="crop-zoom">
          <el-icon aria-hidden="true"><IconZoomOut /></el-icon>
          <input
            v-model.number="zoom"
            type="range"
            min="1"
            max="3"
            step="0.01"
            :aria-label="t('common.crop.zoom')"
          >
          <el-icon aria-hidden="true"><IconZoomIn /></el-icon>
        </label>
      </div>
      <section
        v-if="presets.length"
        class="crop-presets"
        :aria-label="t('common.crop.presets')"
        :aria-hidden="!presetsOpen"
        :inert="!presetsOpen"
      >
        <TransitionGroup
          tag="div"
          name="crop-preset"
          class="crop-preset-grid"
          :style="{ gridTemplateColumns: 'repeat(' + presetColumns + ', minmax(0, 1fr))' }"
        >
          <button
            v-for="(preset, index) in shownPresets"
            :key="preset.key"
            type="button"
            class="crop-preset"
            :class="{ active: chosen === preset.key, round }"
            :style="{ '--i': index }"
            :aria-pressed="chosen === preset.key"
            :title="preset.label"
            @click="pick(preset)"
          >
            <span
              class="crop-preset-art"
              :style="{ aspectRatio: String(aspect) }"
            ><img
              v-if="preset.src"
              :src="preset.src"
              alt=""
            ><el-icon v-else><IconPhoto /></el-icon></span><small>{{ preset.label }}</small>
          </button>
        </TransitionGroup>
        <FeedPagination
          class="crop-preset-pager"
          :page="presetPage"
          :total-pages="presetPages"
          :show-top="false"
          compact
          @page="presetPage = $event"
        />
      </section>
    </div>
    <input
      ref="picker"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      hidden
      @change="replace"
    >
    <template #footer>
      <div
        class="crop-actions"
        :class="{ three: presets.length }"
      >
        <el-button
          v-if="presets.length"
          class="crop-preset-toggle"
          :class="{ on: presetsOpen }"
          :aria-pressed="presetsOpen"
          @click="presetsOpen = !presetsOpen"
        >
          {{ t('common.crop.presets') }}
        </el-button><el-button @click="$refs.picker.click()">
          {{ t('common.crop.replace') }}
        </el-button><el-button
          type="primary"
          :disabled="!ready"
          @click="confirm"
        >
          {{ t('common.crop.confirm') }}
        </el-button>
      </div>
    </template>
  </DialogShell>
</template>
<script src="./ImageCropDialog.js"></script>
<style lang="scss" scoped src="./ImageCropDialog.scss"></style>
