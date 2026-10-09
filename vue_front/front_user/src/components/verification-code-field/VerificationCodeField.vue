<template>
  <label class="code-field">{{ t('auth.register.code') }}
    <el-input
      v-model="code"
      :class="{ 'has-error': Boolean(error) }"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? messageId : undefined"
      inputmode="numeric"
      autocomplete="one-time-code"
      :maxlength="6"
      :placeholder="t('auth.register.codeHint')"
    >
      <template #suffix>
        <el-button
          class="code-send"
          :loading="sending"
          :disabled="seconds > 0 || disabled"
          @click="$emit('send')"
        >
          {{ seconds ? t('auth.register.countdown', { seconds }) : t('auth.register.sendCode') }}
        </el-button>
      </template>
    </el-input>
    <FieldMessage
      :message="error"
      :message-id="messageId"
    />
  </label>
</template>
<script src="./VerificationCodeField.js"></script>
<style lang="scss" scoped src="./VerificationCodeField.scss"></style>
