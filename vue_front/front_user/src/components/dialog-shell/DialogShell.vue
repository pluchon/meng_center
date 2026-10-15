<template>
  <el-dialog
    :model-value="modelValue"
    :width="width"
    :class="['meng-dialog','tone-'+tone]"
    modal-class="meng-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    @close="$emit('close')"
  >
    <template #header>
      <span
        v-if="face"
        class="shell-face"
        aria-hidden="true"
      >{{ face }}</span>
      <div class="shell-head">
        <span
          class="shell-icon"
          aria-hidden="true"
        ><el-icon><component :is="icon" /></el-icon></span>
        <slot name="title">
          <h2><span>{{ title }}</span></h2>
        </slot>
        <button
          type="button"
          class="shell-close"
          :aria-label="t('common.close')"
          :disabled="busy"
          @click="$emit('close')"
        />
      </div>
    </template>
    <slot />
    <template
      v-if="$slots.footer"
      #footer
    >
      <slot name="footer" />
    </template>
  </el-dialog>
</template>
<script src="./DialogShell.js"></script>
