<template>
  <main class="login-layout">
    <section class="login-card">
      <h1>{{ t('auth.login.adminTitle') }}</h1>
      <p>{{ t('auth.login.adminSubtitle') }}</p>
      <el-form
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item :label="t('auth.login.username')">
          <el-input
            v-model="form.username"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item :label="t('auth.login.password')">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-alert
          v-if="failure"
          :title="failure"
          type="error"
          :closable="false"
        />
        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
        >
          {{ t('common.login') }}
        </el-button>
      </el-form>
    </section>
    <CaptchaDialog ref="captcha" />
  </main>
</template>
<script src="./LoginView.js"></script>
<style lang="scss" scoped src="./LoginView.scss"></style>
