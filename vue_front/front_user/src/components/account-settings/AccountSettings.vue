<template>
  <form
    class="account-settings"
    :aria-label="t('account.settings')"
    @submit.prevent="save"
  >
    <div class="account-settings-columns">
      <div class="account-settings-column">
        <label class="account-setting blue"><span><el-icon><User /></el-icon>{{ t('account.nickname') }}</span><el-input
          v-model="form.nickname"
          :maxlength="16"
          show-word-limit
          :aria-label="t('account.nickname')"
        /></label>
        <label class="account-setting gold"><span><el-icon><EditPen /></el-icon>{{ t('account.bio') }}</span><el-input
          v-model="form.bio"
          type="textarea"
          :rows="2"
          :maxlength="Account.BIO_MAX"
          show-word-limit
          :aria-label="t('account.bio')"
        /></label>
        <section class="account-setting violet">
          <h3><el-icon><Lock /></el-icon>{{ t('account.password') }}</h3><div>
            <span
              class="account-password-mask"
              aria-hidden="true"
            /><el-button @click="$emit('dialog', 'password')">
              {{ t('account.edit') }}
            </el-button>
          </div>
        </section>
        <section class="account-setting device">
          <h3><el-icon><Monitor /></el-icon>{{ t('account.devices') }}</h3><div>
            <span>{{ devices.data ? t('account.totalDevices', { count: devices.data.length }) : t(devices.error ? 'account.retry' : 'common.loading') }}</span><el-button @click="$emit('dialog', 'devices')">
              {{ t('account.view') }}
            </el-button>
          </div>
        </section>
      </div>
      <div class="account-settings-column">
        <label class="account-setting mint"><span><el-icon><UserFilled /></el-icon>{{ t('account.username') }}</span><el-input
          v-model="form.username"
          :disabled="cooldown"
          :maxlength="32"
          show-word-limit
          :title="cooldown ? t('account.cooldown', { date: availableDate }) : undefined"
          :aria-label="t('account.username')"
        /><small v-if="cooldown">{{ t('account.cooldown', { date: availableDate }) }}</small></label>
        <section class="account-setting pink">
          <h3><el-icon><Message /></el-icon>{{ t('account.email') }}</h3><div>
            <span>{{ profile.email || t('account.unbound') }}</span><el-button @click="$emit('dialog', 'email')">
              {{ t(profile.email ? 'account.edit' : 'account.bind') }}
            </el-button>
          </div>
        </section>
        <section class="account-setting apricot">
          <h3><el-icon><Iphone /></el-icon>{{ t('account.phone') }}</h3><div>
            <span>{{ profile.phone || t('account.unbound') }}</span><el-button @click="$emit('dialog', 'phone')">
              {{ t(profile.phone ? 'account.edit' : 'account.bind') }}
            </el-button>
          </div>
        </section>
        <section class="account-setting gray">
          <h3><el-icon><CircleClose /></el-icon>{{ t('account.blocks') }}</h3><div>
            <span>{{ blocks.data ? t('account.totalBlocks', { count: blocks.data.total }) : t(blocks.error ? 'account.retry' : 'common.loading') }}</span><el-button @click="$emit('dialog', 'blocks')">
              {{ t('account.manage') }}
            </el-button>
          </div>
        </section>
      </div>
    </div>
    <p
      v-if="failure"
      class="account-settings-error"
      role="alert"
    >
      {{ failure }}
    </p>
    <footer class="account-settings-footer">
      <el-button
        class="account-logout"
        :loading="loggingOut"
        @click="$emit('logout')"
      >
        <el-icon><IconLogout /></el-icon>
        {{ t('account.logout') }}
      </el-button><el-button
        class="account-save"
        native-type="submit"
        :disabled="!changed"
        :loading="saving"
      >
        <span
          v-if="!saving"
          class="account-save-symbol"
          aria-hidden="true"
        ><el-icon><IconCheck /></el-icon></span>
        {{ t('account.save') }}
        <span
          class="account-save-sparkle"
          aria-hidden="true"
        />
      </el-button>
    </footer>
  </form>
</template>
<script src="./AccountSettings.js"></script>
<style lang="scss" scoped src="./AccountSettings.scss"></style>
