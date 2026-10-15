<template>
  <AuthLayout>
    <h1 class="auth-heading">
      <span
        class="heading-badge"
        aria-hidden="true"
      ><img
        :src="icons.key"
        alt=""
      ></span>
      {{ t('auth.login.title') }}
      <span class="auth-brand">{{ t('common.brand') }}</span>
    </h1>
    <form
      class="auth-form"
      novalidate
      @submit.prevent="submit"
    >
      <label><span class="field-name"><img
        :src="icons.user"
        alt=""
        aria-hidden="true"
      >{{ t('auth.login.username') }}</span><el-input
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
      <label><span class="field-name"><img
        :src="icons.lock"
        alt=""
        aria-hidden="true"
      >{{ t('auth.login.password') }}</span><el-input
        v-model="form.password"
        :class="{ 'has-error': Boolean(errors.password) }"
        :aria-invalid="Boolean(errors.password)"
        :aria-describedby="errors.password ? fieldId('password') : undefined"
        type="password"
        show-password
        autocomplete="current-password"
        :placeholder="t('auth.login.passwordHint')"
      /><CapsLockHint /><FieldMessage
        :message="errors.password"
        :message-id="fieldId('password')"
      /></label>
      <div
        v-if="pending"
        class="auth-notice"
      >
        <img
          class="auth-notice-art"
          :src="stateFailReviewArt"
          alt=""
        >
        {{ t('auth.login.pending') }}
      </div>
      <div
        v-if="rejected"
        class="auth-notice"
      >
        <img
          class="auth-notice-art"
          :src="stateFailRejectedArt"
          alt=""
        >
        <p>{{ t('auth.login.rejected') }}</p>
        <p>{{ t('auth.login.reason') }}{{ rejectionReason }}</p>
        <ReasonEditor v-model="form.applyReason" />
        <el-button
          :loading="submitting"
          @click="reapply"
        >
          {{ t('auth.login.reapply') }}
        </el-button>
      </div>
      <div class="login-options">
        <div class="consent-field">
          <LegalConsent
            v-model="agreed"
            :origin="RouteName.LOGIN"
            :invalid="Boolean(errors.agreed)"
          />
        </div>
        <router-link
          class="forgot-password"
          :to="{ name: RouteName.RESET_PASSWORD }"
        >
          <img
            :src="icons.unlock"
            alt=""
            aria-hidden="true"
          >{{ t('auth.login.forgotPassword') }}
        </router-link>
      </div>
      <el-button
        type="primary"
        native-type="submit"
        class="auth-primary"
        :loading="submitting"
      >
        {{ t('auth.login.submit') }}
      </el-button>
    </form>
    <p class="auth-footer">
      {{ t('auth.login.noAccount') }}
      <router-link :to="{ name: RouteName.REGISTER }">
        {{ t('auth.login.register') }}
      </router-link>
    </p>
    <CaptchaDialog ref="captcha" />
  </AuthLayout>
</template>
<script src="./LoginView.js"></script>
<style lang="scss" scoped src="./LoginView.scss"></style>
