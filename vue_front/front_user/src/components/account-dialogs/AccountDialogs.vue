<template>
  <el-dialog
    :model-value="true"
    width="440px"
    class="account-dialog"
    :class="'account-dialog-' + type"
    append-to-body
    align-center
    destroy-on-close
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :before-close="close"
  >
    <template #header>
      <span
        class="account-dialog-tape"
        aria-hidden="true"
      />
      <span
        class="account-dialog-face"
        aria-hidden="true"
      >{{ dialogFace }}</span>
      <span
        class="account-dialog-sparkle"
        aria-hidden="true"
      />
      <h2 class="account-dialog-title">
        <el-icon>
          <svg
            v-if="type === 'folder'"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.3"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path d="M3.5 7.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v7.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z" /><path d="M12 11.5v5M9.5 14h5" /></svg><component
            :is="dialogIcon"
            v-else
          />
        </el-icon><span>{{ title }}</span>
      </h2>
    </template>
    <form
      v-if="type !== 'devices' && type !== 'blocks'"
      class="account-dialog-form"
      @submit.prevent="submit"
    >
      <template v-if="type === 'folder'">
        <div
          class="account-cover-choice"
        >
          <input
            ref="coverInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            hidden
            @change="selectCover"
          >
          <button
            type="button"
            class="account-cover-picker"
            :disabled="busy"
            @click="$refs.coverInput.click()"
          >
            <img
              v-if="coverPreview"
              :src="coverPreview"
              alt=""
            >
            <span
              v-else
              class="account-cover-symbol"
              aria-hidden="true"
            ><el-icon><PictureIcon /></el-icon></span>
            <span class="account-cover-caption">{{ t('account.optionalCover') }}</span>
          </button>
          <button
            v-if="cover"
            type="button"
            class="account-cover-remove"
            :disabled="busy"
            :aria-label="t('account.removeCover')"
            @click="clearCover"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <label>{{ t('account.name') }}<el-input
          v-model="form.name"
          :maxlength="Account.NAME_MAX"
          :aria-label="t('account.name')"
          :placeholder="t('account.folderNamePlaceholder')"
          :disabled="busy"
          show-word-limit
          autofocus
        /></label>
        <label>{{ t('account.description') }}<el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          :maxlength="Account.BIO_MAX"
          show-word-limit
          :aria-label="t('account.description')"
          :placeholder="t('account.folderDescriptionPlaceholder')"
          :disabled="busy"
        /></label>
      </template>
      <template v-else-if="type === 'password'">
        <label>{{ t('account.currentPassword') }}<el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          autocomplete="current-password"
          :aria-label="t('account.currentPassword')"
        /></label>
        <label>{{ t('account.newPassword') }}<el-input
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
          :aria-label="t('account.newPassword')"
        /></label>
        <label>{{ t('account.confirmPassword') }}<el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          :aria-label="t('account.confirmPassword')"
        /></label>
        <p class="account-dialog-hint account-password-rule">
          {{ t('account.passwordRule') }}
        </p>
      </template>
      <template v-else>
        <div
          v-if="type === 'email' && profile.email"
          class="account-current-email"
        >
          <span>{{ t('account.currentEmail') }}</span><b>{{ profile.email }}</b>
        </div>
        <label v-if="type === 'email'">{{ t('account.newEmail') }}<el-input
          v-model="form.email"
          type="email"
          autocomplete="email"
          :maxlength="254"
          :placeholder="t('account.emailPlaceholder')"
          :aria-label="t('account.newEmail')"
        /></label>
        <div
          v-else
          class="account-phone-field"
        >
          <label for="account-phone-number">{{ t('account.phone') }}</label><div class="account-phone-row">
            <el-select
              v-model="form.countryCode"
              :aria-label="t('account.region')"
            >
              <el-option
                v-for="region in Regions"
                :key="region.code"
                :value="region.code"
                :label="t('auth.regions.' + region.key) + ' ' + region.code"
              />
            </el-select><el-input
              id="account-phone-number"
              v-model="form.phone"
              name="phone"
              type="tel"
              autocomplete="section-account-phone tel-national"
              :maxlength="32"
              :aria-label="t('account.phone')"
            />
          </div>
        </div>
        <label>{{ t('account.code') }}<div class="account-code-row"><el-input
          v-model="form.code"
          inputmode="numeric"
          autocomplete="one-time-code"
          :maxlength="6"
          :aria-label="t('account.code')"
        /><el-button
          :disabled="seconds > 0 || sending || busy"
          :loading="sending"
          @click="sendCode"
        >{{ seconds ? t('account.resend', { seconds }) : t('account.sendCode') }}</el-button></div></label>
        <label>{{ t('account.currentPassword') }}<el-input
          v-model="form.password"
          type="password"
          :show-password="type !== 'phone'"
          autocomplete="current-password"
          :aria-label="t('account.currentPassword')"
        /></label>
      </template>
      <p
        v-if="failure"
        class="account-dialog-error"
        role="alert"
      >
        {{ failure }}
      </p>
      <div class="account-dialog-actions">
        <el-button
          :disabled="busy"
          @click="close"
        >
          {{ t('account.cancel') }}
        </el-button><el-button
          type="primary"
          :loading="busy"
          native-type="submit"
        >
          <span
            v-if="!busy"
            class="account-confirm-symbol"
            aria-hidden="true"
          ><el-icon><Check /></el-icon></span>
          {{ confirmText }}
        </el-button>
      </div>
    </form>
    <template v-else>
      <PageState
        v-if="resource.loading"
        state="loading"
      /><PageState
        v-else-if="resource.error"
        state="error"
        @retry="load"
      /><template v-else>
        <PageState
          v-if="!list.length"
          state="empty"
        />
        <div class="account-dialog-list">
          <article
            v-for="item in list"
            :key="type === 'devices' ? item.id : item.user.id"
            class="account-dialog-list-row"
          >
            <el-icon
              v-if="type === 'devices'"
              class="account-device-icon"
            >
              <component :is="deviceIcon(item)" />
            </el-icon><MessageAvatar
              v-else
              :user="item.user"
              :size="40"
            />
            <div class="account-device-info">
              <h3 :title="type === 'devices' ? item.name : item.user.nickname">
                {{ type === 'devices' ? item.name || t('account.unknownDevice') : item.user.nickname || t('home.authorFallback') }}
              </h3><p>{{ type === 'devices' ? t('account.deviceActivity', { region: item.region || t('account.noRegion'), time: activity(item.lastActiveAt) }) : t('account.blockedAt', { time: date(item.blockedAt) }) }}</p>
            </div>
            <span
              v-if="type === 'devices' && item.current"
              class="account-current-device"
            >{{ t('account.currentDevice') }}</span>
            <el-button
              v-if="!item.current"
              :loading="pending === (type === 'devices' ? item.id : item.user.id)"
              :disabled="!!pending"
              @click="remove(item)"
            >
              {{ t(type === 'devices' ? 'account.offline' : 'account.unblock') }}
            </el-button>
          </article>
        </div>
        <el-pagination
          v-if="type === 'blocks' && resource.data.total > 20"
          v-model:current-page="page"
          :page-size="20"
          :total="resource.data.total"
          layout="prev, pager, next"
          @current-change="load"
        />
        <p
          v-if="type === 'blocks'"
          class="account-block-hint"
        >
          {{ t('account.blockHint') }}
        </p>
        <div
          v-else-if="list.some(item => !item.current)"
          class="account-dialog-actions account-device-actions"
        >
          <el-button
            class="account-offline-others"
            :disabled="!!pending"
            :loading="pending === 'others'"
            @click="removeOthers"
          >
            {{ t('account.offlineOthers') }}
          </el-button>
        </div>
      </template>
    </template>
    <CaptchaDialog
      v-if="type === 'email' || type === 'phone'"
      ref="captcha"
    />
  </el-dialog>
</template>
<script src="./AccountDialogs.js"></script>
<style lang="scss" src="./AccountDialogs.scss"></style>
