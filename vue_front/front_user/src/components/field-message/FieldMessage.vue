<template>
  <span
    v-if="message"
    :id="messageId"
    class="field-message"
    role="alert"
  >{{ message }}</span>
</template>
<script src="./FieldMessage.js"></script>
<style lang="scss" scoped src="./FieldMessage.scss"></style>
