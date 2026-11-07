<template>
  <DialogShell
    :model-value="true"
    :icon="IconUsers"
    :tone="following ? 'blue' : 'pink'"
    :face="t(following ? 'account.followingFace' : 'account.followersFace')"
    width="min(520px,calc(100vw - 32px))"
    @close="$emit('close')"
  >
    <template #title>
      <h2
        class="follow-title"
        :class="following ? 'blue' : 'pink'"
      >
        {{ t(following ? 'account.followCount' : 'account.fansCount') }}<b>{{ count }}</b>
      </h2>
    </template>
    <PageState
      v-if="resource.loading && !resource.data.records.length"
      class="follow-state"
      state="loading"
      size="large"
    />
    <PageState
      v-else-if="resource.error"
      class="follow-state"
      state="error"
      size="large"
      @retry="load"
    />
    <PageState
      v-else-if="!resource.data.records.length"
      class="follow-state"
      state="empty"
      art="collection"
      size="large"
      :text="t(following ? 'account.noFollowing' : 'account.noFollowers')"
    />
    <ul
      v-else
      class="follow-list"
      :aria-label="t(following ? 'account.followingList' : 'account.followersList')"
    >
      <li
        v-for="row in resource.data.records"
        :key="row.id"
        class="follow-row"
      >
        <button
          type="button"
          class="follow-person"
          @click="openUser(row)"
        >
          <MessageAvatar
            :user="row"
            :size="46"
          />
          <span class="follow-info">
            <span class="follow-name-line">
              <strong>{{ row.nickname || t('home.authorFallback') }}</strong><em>{{ t('home.level', { level: row.level }) }}</em>
            </span>
            <small :class="{ 'is-empty': !row.bio }">{{ row.bio || t('account.otherBio') }}</small>
          </span>
        </button>
        <button
          v-if="!row.self"
          type="button"
          class="follow-action"
          :class="actionTone(row)"
          :disabled="Boolean(pending[row.id])"
          @click="toggle(row)"
        >
          <el-icon><component :is="actionIcon(row)" /></el-icon>{{ t(actionLabel(row)) }}
        </button>
      </li>
    </ul>
    <div class="follow-pager-slot">
      <FeedPagination
        :page="page"
        :total-pages="totalPages"
        :show-top="false"
        compact
        @page="goPage"
      />
    </div>
  </DialogShell>
</template>
<script src="./FollowDialog.js"></script>
<style lang="scss" scoped src="./FollowDialog.scss"></style>
