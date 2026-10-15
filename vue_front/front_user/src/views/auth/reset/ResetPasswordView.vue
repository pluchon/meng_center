<template>
  <main class="reset-layout">
    <ParticleBackground />
    <section
      class="reset-card tone-mint"
      aria-labelledby="reset-title"
    >
      <CardEdge />
      <BorderGlow />
      <AuthDecor :variant="AuthDecorVariant.RESET" />
      <img
        class="reset-avatar"
        :src="avatar"
        :alt="t('auth.reset.avatar')"
      >
      <span class="reset-comfort">{{ t('auth.reset.comfort') }}</span>
      <template v-if="!success">
        <h1
          id="reset-title"
          class="auth-heading"
        >
          <span
            class="heading-badge"
            aria-hidden="true"
          ><img
            :src="icons.unlock"
            alt=""
          ></span>
          <span class="reset-action">{{ t('auth.reset.reset') }}</span>
          <span class="reset-highlight">{{ t('auth.reset.password') }}</span>
        </h1>
        <form
          class="auth-form"
          novalidate
          @submit.prevent="submit"
        >
          <ContactField
            v-model:channel="channel"
            v-model:email="form.email"
            v-model:phone="form.phone"
            v-model:country-code="form.countryCode"
            :error="errors.contact"
            :message-id="fieldId('contact')"
            :disabled="sending || submitting"
          />
          <VerificationCodeField
            v-model="form.code"
            :error="errors.code"
            :message-id="fieldId('code')"
            :seconds="seconds"
            :sending="sending"
            :disabled="submitting"
            @send="sendCode"
          />
          <div class="auth-pair">
            <label><span class="field-name"><img
              :src="icons.lock"
              alt=""
              aria-hidden="true"
            >{{ t('auth.reset.newPassword') }}</span><el-input
              v-model="form.password"
              :class="{ 'has-error': Boolean(errors.password) }"
              :aria-invalid="Boolean(errors.password)"
              :aria-describedby="errors.password ? fieldId('password') : undefined"
              type="password"
              show-password
              autocomplete="new-password"
              :maxlength="Auth.PASSWORD_MAX"
              :placeholder="t('auth.register.passwordHint')"
              :title="t('auth.register.passwordRule')"
            /><CapsLockHint /><FieldMessage
              :message="errors.password"
              :message-id="fieldId('password')"
            /></label>
            <label><span class="field-name"><img
              :src="icons.lock"
              alt=""
              aria-hidden="true"
            >{{ t('auth.register.confirmPassword') }}</span><el-input
              v-model="form.confirmPassword"
              :class="{ 'has-error': Boolean(errors.confirmPassword) }"
              :aria-invalid="Boolean(errors.confirmPassword)"
              :aria-describedby="errors.confirmPassword ? fieldId('confirmPassword') : undefined"
              type="password"
              show-password
              autocomplete="new-password"
              :maxlength="Auth.PASSWORD_MAX"
              :placeholder="t('auth.register.confirmPasswordHint')"
            /><CapsLockHint /><FieldMessage
              :message="errors.confirmPassword"
              :message-id="fieldId('confirmPassword')"
            /></label>
          </div>
          <el-button
            type="primary"
            native-type="submit"
            class="auth-primary"
            :loading="submitting"
            :disabled="sending"
          >
            {{ t('auth.reset.submit') }}
          </el-button>
        </form>
        <p class="auth-footer">
          {{ t('auth.reset.remember') }}
          <router-link :to="{ name: RouteName.LOGIN }">
            {{ t('auth.reset.backLogin') }}
          </router-link>
        </p>
      </template>
      <div
        v-else
        class="reset-success"
        role="status"
      >
        <h1
          id="reset-title"
          class="auth-heading"
        >
          <span
            class="heading-badge"
            aria-hidden="true"
          ><img
            :src="icons.key"
            alt=""
          ></span>
          {{ t('auth.reset.success') }}
        </h1>
        <p class="reset-description">
          {{ t('auth.reset.successDescription') }}
        </p>
        <router-link
          class="auth-return"
          :to="{ name: RouteName.LOGIN }"
        >
          {{ t('auth.reset.backLogin') }}
        </router-link>
      </div>
      <CaptchaDialog ref="captcha" />
    </section>
  </main>
</template>
<script src="./ResetPasswordView.js"></script>
<style lang="scss" scoped src="./ResetPasswordView.scss"></style>
