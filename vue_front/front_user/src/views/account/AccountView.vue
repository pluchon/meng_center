<template>
  <main class="account-layout">
    <section class="account-card">
      <PageState
        v-if="loading"
        state="loading"
      />
      <PageState
        v-else-if="error"
        state="error"
        @retry="load"
      />
      <PageState
        v-else-if="!user.loggedIn"
        state="unauthenticated"
      />
      <PageState
        v-else-if="!data"
        state="empty"
      />
      <template v-else>
        <h1>{{ t('user.account.welcome', { name: data.nickname || data.username }) }}</h1>
        <p>{{ t('user.account.status') }}{{ t('user.status.' + data.status) }}</p>
        <p>{{ t('user.account.level') }}{{ data.level }}</p>
        <el-button @click="logout">
          {{ t('common.logout') }}
        </el-button>
        <h2>{{ t('user.account.changePassword') }}</h2>
        <form
          class="auth-form"
          @submit.prevent="changePassword"
        >
          <label>{{ t('user.account.currentPassword')
          }}<el-input
            v-model="password.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
          /></label>
          <label>{{ t('user.account.newPassword')
          }}<el-input
            v-model="password.password"
            type="password"
            show-password
            autocomplete="new-password"
          /></label>
          <label>{{ t('user.account.confirmPassword')
          }}<el-input
            v-model="password.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
          /></label>
          <p
            v-if="failure"
            class="auth-error"
          >
            {{ failure }}
          </p>
          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
          >
            {{ t('common.confirm') }}
          </el-button>
        </form>
      </template>
    </section>
  </main>
</template>
<script src="./AccountView.js"></script>
<style lang="scss" scoped src="./AccountView.scss"></style>
