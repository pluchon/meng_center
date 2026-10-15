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
        <img
          class="account-mascot"
          :src="editorCirnoMascot"
          alt=""
          aria-hidden="true"
          draggable="false"
        >
        <span
          class="account-face"
          aria-hidden="true"
        >{{ t('account.face') }}</span>
        <PageState
          v-if="profile.loading && !profile.data"
          state="loading"
        />
        <PageState
          v-else-if="profile.error?.status === 404"
          state="empty"
          art="notFound"
          :text="t('account.userMissing')"
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
                :alt="displayName"
                width="112"
                height="112"
              >
              <template v-if="!other">
                <button
                  class="account-camera"
                  :aria-label="t('account.camera')"
                  :title="t('account.avatarHint')"
                  :disabled="uploading"
                  @click="$refs.avatarInput.click()"
                >
                  <img
                    :src="cameraIcon"
                    alt=""
                    aria-hidden="true"
                  >
                </button>
                <input
                  ref="avatarInput"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  hidden
                  @change="uploadAvatar"
                >
              </template>
            </div>
            <div class="account-identity">
              <div class="account-name">
                <h1 :title="displayName">
                  {{ displayName }}
                </h1><component
                  :is="other ? 'span' : 'RouterLink'"
                  class="account-level"
                  :to="other ? undefined : { name: RouteName.GROWTH }"
                >
                  {{ t('growth.level', { level: profile.data.level }) }} {{ profile.data.level > 0 ? t('growth.levels.' + (profile.data.level - 1)) : '' }}
                </component><span
                  class="account-sparkle"
                  aria-hidden="true"
                />
              </div><p
                class="account-bio"
                :class="{ 'is-empty': !profile.data.bio }"
              >
                <i
                  class="account-bio-mark"
                  aria-hidden="true"
                />{{ profile.data.bio || t(other ? 'account.otherBio' : 'account.noBio') }}
              </p>
              <div class="account-social">
                <button
                  class="account-social-sticker blue"
                  @click="followDialog = 'following'"
                >
                  <el-icon><IconUsers /></el-icon>{{ t('account.followCount') }} <b>{{ profile.data.followingCount }}</b>
                </button><button
                  class="account-social-sticker pink"
                  @click="followDialog = 'followers'"
                >
                  <el-icon><IconUsers /></el-icon>{{ t('account.fansCount') }} <b>{{ profile.data.followerCount }}</b>
                </button><button
                  v-if="!other"
                  class="account-social-sticker gold"
                  @click="pointsOpen = true"
                >
                  <el-icon><IconCoin /></el-icon>{{ t('account.pointsSticker') }} <b>{{ pointsBalance }}</b>
                </button>
              </div>
            </div>
            <div
              v-if="other"
              class="account-actions"
            >
              <button
                class="account-action"
                :class="profile.data.followed ? 'is-followed' : 'is-follow'"
                :aria-pressed="profile.data.followed"
                :disabled="followBusy"
                @click="toggleFollow"
              >
                <span
                  class="account-action-icon"
                  aria-hidden="true"
                ><el-icon><IconUserCheck v-if="profile.data.followed" /><IconUserPlus v-else /></el-icon></span>{{ t(profile.data.followed ? 'account.following' : 'account.follow') }}
                <i
                  v-if="!profile.data.followed"
                  class="account-action-sparkle"
                  aria-hidden="true"
                />
              </button><button
                class="account-action is-message"
                @click="message"
              >
                <span
                  class="account-action-icon"
                  aria-hidden="true"
                ><el-icon><IconMessage /></el-icon></span>{{ t('account.message') }}
              </button>
            </div>
            <div
              v-else
              class="account-stats"
            >
              <button
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
            :class="{ 'is-likes': secondPane }"
          >
            <h2 class="account-section-title">
              <el-icon><IconPencil v-if="pane === AccountPane.POSTS" /><IconHeart v-else-if="pane === AccountPane.LIKES" /><Star v-else /></el-icon><span>{{ sectionTitle }}</span><i
                class="account-sparkle"
                aria-hidden="true"
              />
            </h2><i18n-t
              v-if="pane === AccountPane.FOLDERS && !other"
              keypath="account.folderCount"
              scope="global"
              tag="span"
              class="account-folder-count"
            >
              <template #count>
                <b>{{ folderTotal }}</b>
              </template>
            </i18n-t><nav
              class="account-tabs"
              :class="{ 'is-likes': secondPane }"
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
                {{ t(other ? 'account.publicFolders' : 'account.favorites') }}
              </button><button
                :aria-pressed="secondPane"
                @click="goPane(other ? AccountPane.POSTS : AccountPane.LIKES)"
              >
                {{ t(other ? 'account.published' : 'account.likes') }}
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
              v-else-if="secondPane || folderId"
              :key="pane + ':' + folderId"
              :user-id="viewedId"
              :pane="pane"
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
              :user-id="viewedId"
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
      <FollowDialog
        v-if="followDialog"
        :user-id="other ? viewedId : profile.data.id"
        :initial="followDialog"
        :counts="{ following: profile.data.followingCount, followers: profile.data.followerCount }"
        @close="closeFollowDialog"
        @changed="loadProfile"
      />
      <PointsDialog
        v-if="pointsOpen"
        @close="closePoints"
      />
    </main>
  </CommunityLayout>
</template>
<script src="./AccountView.js"></script>
<style lang="scss" scoped src="./AccountView.scss"></style>
