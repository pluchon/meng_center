<template>
  <div class="reason-field">
    <span
      :id="labelId"
      class="reason-label"
    >{{ t('auth.register.applyReason') }}</span>
    <div class="reason-shell">
      <div
        ref="editor"
        class="reason-editor"
        role="textbox"
        contenteditable="plaintext-only"
        aria-multiline="true"
        :aria-labelledby="labelId"
        :aria-describedby="counterId"
        :data-placeholder="t('auth.register.applyHint')"
        @input="update"
        @beforeinput="beforeInput"
        @paste="paste"
        @compositionstart="composing = true"
        @compositionend="compositionEnd"
      />
      <span
        :id="counterId"
        class="reason-counter"
        aria-live="polite"
      >{{
        t('auth.register.reasonCount', { count: modelValue.length, max: Auth.REASON_MAX })
      }}</span>
    </div>
  </div>
</template>
<script src="./ReasonEditor.js"></script>
<style lang="scss" scoped src="./ReasonEditor.scss"></style>
