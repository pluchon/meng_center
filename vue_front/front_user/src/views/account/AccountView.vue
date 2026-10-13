<template>
  <CommunityLayout class="account-community">
    <main class="account-layout">
      <DarkVeil />
      <section class="account-card">
        <img
          v-for="side in ['left', 'right']"
          :key="'flower-' + side"
          class="account-decoration account-flower"
          :class="side"
          :src="detailFlower"
          alt=""
          aria-hidden="true"
        >
        <img
          v-for="side in ['left', 'right']"
          :key="'bubble-' + side"
          class="account-decoration account-bubble"
          :class="side"
          :src="detailBubble"
          alt=""
          aria-hidden="true"
        >
        <span
          v-for="side in ['left', 'right', 'bottom']"
          :key="'sparkle-' + side"
          class="account-decoration account-edge-sparkle"
          :class="side"
          aria-hidden="true"
        />
        <span
          class="account-face"
          aria-hidden="true"
        >{{ t('account.face') }}</span>
        <PageState
          v-if="profile.loading && !profile.data"
          state="loading"
        />
        <PageState
          v-else-if="profile.error"
          state="error"
          @retry="loadProfile"
        />
        <template v-else-if="profile.data">
          <header class="account-header">
            <div class="account-portrait">
              <img
                :src="avatarUrl(profile.data.avatar) || userAvatar"
                :alt="profile.data.nickname || profile.data.username"
                width="112"
                height="112"
              >
              <button
                class="account-camera"
                :aria-label="t('account.camera')"
                :title="t('account.avatarHint')"
                :disabled="uploading"
                @click="$refs.avatarInput.click()"
              >
                <el-icon><CameraIcon /></el-icon>
              </button>
              <input
                ref="avatarInput"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                hidden
                @change="uploadAvatar"
              >
            </div>
            <div class="account-identity">
              <div class="account-name">
                <h1 :title="profile.data.nickname || profile.data.username">
                  {{ profile.data.nickname || profile.data.username }}
                </h1><RouterLink
                  class="account-level"
                  :to="{ name: RouteName.GROWTH }"
                >
                  {{ t('growth.level', { level: profile.data.level }) }} {{ profile.data.level > 0 ? t('growth.levels.' + (profile.data.level - 1)) : '' }}
                </RouterLink><span
                  class="account-sparkle"
                  aria-hidden="true"
                />
              </div><p>{{ profile.data.bio || t('account.noBio') }}</p>
            </div>
            <div class="account-stats">
              <span class="account-sticker blue">{{ t('account.joined') }} <b>{{ joinedDays }}</b> {{ t('account.days') }}</span><template v-if="counts.data">
                <span class="account-sticker gold">{{ t('account.favorites') }} <b>{{ counts.data.favorites }}</b> {{ t('account.articles') }}</span><span class="account-sticker pink">{{ t('account.likes') }} <b>{{ counts.data.likes }}</b> {{ t('account.articles') }}</span>
              </template><button
                v-else-if="counts.error"
                @click="loadCounts"
              >
                {{ t('account.retry') }}
              </button><button
                class="account-sticker account-settings-button"
                :class="pane === AccountPane.SETTINGS ? 'mint' : 'violet'"
                :aria-pressed="pane === AccountPane.SETTINGS"
                @click="goPane(pane === AccountPane.SETTINGS ? AccountPane.FOLDERS : AccountPane.SETTINGS)"
              >
                <el-icon><IconChevronLeft v-if="pane === AccountPane.SETTINGS" /><Setting v-else /></el-icon>{{ pane === AccountPane.SETTINGS ? t('account.back') : t('account.settings') }}
              </button>
            </div>
          </header>
          <div
            v-if="pane !== AccountPane.SETTINGS && !folderId"
            class="account-section-heading"
            :class="{ 'is-likes': pane === AccountPane.LIKES }"
          >
            <h2 class="account-section-title">
              <el-icon><IconHeart v-if="pane === AccountPane.LIKES" /><Star v-else /></el-icon><span>{{ pane === AccountPane.LIKES ? t('account.liked') : folder ? folderName(folder) : t('account.collections') }}</span><i
                class="account-sparkle"
                aria-hidden="true"
              />
            </h2><i18n-t
              v-if="pane === AccountPane.FOLDERS"
              keypath="account.folderCount"
              scope="global"
              tag="span"
              class="account-folder-count"
            >
              <template #count>
                <b>{{ folderTotal }}</b>
              </template>
            </i18n-t><nav
              v-if="!folderId"
              class="account-tabs"
              :class="{ 'is-likes': pane === AccountPane.LIKES }"
              :aria-label="t('account.collections')"
            >
              <span
                class="account-tab-indicator"
                aria-hidden="true"
              />
              <button
                :aria-pressed="pane === AccountPane.FOLDERS"
                @click="goPane(AccountPane.FOLDERS)"
              >
                {{ t('account.favorites') }}
              </button><button
                :aria-pressed="pane === AccountPane.LIKES"
                @click="goPane(AccountPane.LIKES)"
              >
                {{ t('account.likes') }}
              </button>
            </nav>
          </div>
          <Transition
            name="account-panel"
            mode="out-in"
          >
            <AccountSettings
              v-if="pane === AccountPane.SETTINGS"
              key="settings"
              ref="settings"
              :logging-out="loggingOut"
              :profile="profile.data"
              @saved="updatedProfile"
              @dialog="openDialog"
              @logout="logout"
            />
            <AccountPosts
              v-else-if="pane === AccountPane.LIKES || folderId"
              :key="pane + ':' + folderId"
              :folder-id="folderId"
              :folder="folder"
              :page="page"
              :deleting="deleting"
              @page="goPage"
              @back="goPane(AccountPane.FOLDERS)"
              @changed="loadCounts"
              @folder-updated="updatedFolder"
              @delete="deleteFolder"
            />
            <AccountFolders
              v-else
              key="folders"
              :revision="folderRevision"
              @open="openFolder"
              @create="openDialog('folder')"
              @loaded="folders = $event"
              @total="folderTotal = $event"
            />
          </Transition>
        </template>
        <span
          class="account-bottom-face"
          aria-hidden="true"
        >{{ t('account.bottomFace') }}</span>
      </section>
      <AccountDialogs
        v-if="dialog"
        :type="dialog"
        :profile="profile.data"
        @close="dialog = null"
        @saved="dialogSaved"
        @changed="$refs.settings?.refresh($event)"
      />
    </main>
  </CommunityLayout>
</template>
<script src="./AccountView.js"></script>
<style lang="scss" scoped src="./AccountView.scss"></style>
