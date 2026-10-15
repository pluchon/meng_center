<template>
  <div
    class="folder-color-picker"
    role="radiogroup"
    :aria-label="t('account.folderColor')"
  >
    <button
      v-for="color in FolderColors"
      :key="color"
      type="button"
      role="radio"
      class="folder-swatch"
      :class="['folder-' + color.toLowerCase(), { selected: color === modelValue }]"
      :aria-checked="color === modelValue"
      :aria-label="t('account.colors.' + color)"
      :title="t('account.colors.' + color)"
      :disabled="disabled"
      @click="$emit('update:modelValue', color)"
    >
      <img
        :src="tickIcon"
        alt=""
        aria-hidden="true"
      >
    </button>
  </div>
</template>
<script src="./FolderColorPicker.js"></script>
<style lang="scss" scoped src="./FolderColorPicker.scss"></style>
