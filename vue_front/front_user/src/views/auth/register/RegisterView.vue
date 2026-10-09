<template>
  <AuthLayout registration>
    <template v-if="success">
      <h1 class="auth-heading">
        {{ t('auth.register.success') }}
      </h1>
      <p class="auth-description">
        {{ t('auth.register.successDescription') }}
      </p>
      <router-link :to="{ name: RouteName.LOGIN }">
        {{ t('auth.register.backLogin') }}
      </router-link>
    </template>
    <template v-else>
      <h1 class="auth-heading">
        {{ t('auth.register.join') }}
        <span class="auth-brand">{{ t('common.brand') }}</span>
      </h1>
      <form
        class="auth-form"
        novalidate
        @submit.prevent="submit"
      >
        <label>{{ t('auth.login.username')
        }}<el-input
          v-model="form.username"
          :class="{ 'has-error': Boolean(errors.username) }"
          :aria-invalid="Boolean(errors.username)"
          :aria-describedby="errors.username ? fieldId('username') : undefined"
          autocomplete="username"
          :placeholder="t('auth.login.usernameHint')"
        /><FieldMessage
          :message="errors.username"
          :message-id="fieldId('username')"
        /></label>
        <div class="auth-pair">
          <label>{{ t('auth.login.password')
          }}<el-input
            v-model="form.password"
            :class="{ 'has-error': Boolean(errors.password) }"
            :aria-invalid="Boolean(errors.password)"
            :aria-describedby="errors.password ? fieldId('password') : undefined"
            type="password"
            show-password
            :maxlength="Auth.PASSWORD_MAX"
            :placeholder="t('auth.register.passwordHint')"
            :title="t('auth.register.passwordRule')"
            autocomplete="new-password"
          /><CapsLockHint /><FieldMessage
            :message="errors.password"
            :message-id="fieldId('password')"
          /></label>
          <label>{{ t('auth.register.confirmPassword')
          }}<el-input
            v-model="form.confirmPassword"
            :class="{ 'has-error': Boolean(errors.confirmPassword) }"
            :aria-invalid="Boolean(errors.confirmPassword)"
            :aria-describedby="errors.confirmPassword ? fieldId('confirmPassword') : undefined"
            type="password"
            show-password
            :maxlength="Auth.PASSWORD_MAX"
            :placeholder="t('auth.register.confirmPasswordHint')"
            autocomplete="new-password"
          /><CapsLockHint /><FieldMessage
            :message="errors.confirmPassword"
            :message-id="fieldId('confirmPassword')"
          /></label>
        </div>
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
        <ReasonEditor v-model="form.applyReason" />
        <div class="consent-field">
          <LegalConsent
            v-model="agreed"
            :invalid="Boolean(errors.agreed)"
          />
        </div>
        <el-button
          type="primary"
          class="auth-primary"
          native-type="submit"
          :loading="submitting"
          :disabled="sending"
        >
          {{ t('auth.register.submit') }}
        </el-button>
      </form>
      <p class="auth-footer">
        {{ t('auth.register.haveAccount') }}
        <router-link :to="{ name: RouteName.LOGIN }">
          {{ t('auth.register.backLogin') }}
        </router-link>
      </p>
    </template>
    <CaptchaDialog ref="captcha" />
  </AuthLayout>
</template>
<script src="./RegisterView.js"></script>
<style lang="scss" scoped src="./RegisterView.scss"></style>
