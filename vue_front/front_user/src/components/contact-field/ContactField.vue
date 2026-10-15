<template>
  <div
    class="contact-field field-group"
    :class="{ 'is-sms': channel === ContactChannel.SMS }"
  >
    <span class="contact-label field-name"><img
      :src="mailIcon"
      alt=""
      aria-hidden="true"
    >{{ t('auth.register.contact') }}</span>
    <div
      class="contact-control"
      :class="{ 'email-control': channel === ContactChannel.EMAIL, 'has-error': Boolean(error) }"
    >
      <el-select
        v-if="channel === ContactChannel.SMS"
        v-model="countryCodeValue"
        class="contact-region"
        :aria-label="t('auth.register.region')"
        popper-class="meng-region-popper"
      >
        <el-option
          v-for="region in Regions"
          :key="region.key"
          :value="region.code"
          :label="t('auth.regions.' + region.key) + ' ' + region.code"
        />
      </el-select>
      <el-input
        v-if="channel === ContactChannel.EMAIL"
        v-model="emailValue"
        class="contact-input"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? messageId : undefined"
        type="email"
        autocomplete="email"
        :aria-label="t('auth.register.email')"
        :placeholder="t('auth.register.emailHint')"
      />
      <el-input
        v-else
        v-model="phoneValue"
        class="contact-input"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? messageId : undefined"
        type="tel"
        autocomplete="tel-national"
        :aria-label="t('auth.register.phone')"
        :placeholder="t('auth.register.phoneHint')"
      />
      <el-button
        class="contact-switch"
        :class="{ 'is-switching': switching }"
        :disabled="disabled"
        :aria-label="
          t('auth.register.switchTo', {
            channel: t(
              channel === ContactChannel.SMS ? 'auth.register.email' : 'auth.register.phone',
            ),
          })
        "
        @animationend="switching = false"
        @click="switchChannel"
      >
        <el-icon><ContactSwitchIcon /></el-icon>
      </el-button>
    </div>
    <FieldMessage
      :message="error"
      :message-id="messageId"
    />
  </div>
</template>
<script src="./ContactField.js"></script>
<style lang="scss" scoped src="./ContactField.scss"></style>
