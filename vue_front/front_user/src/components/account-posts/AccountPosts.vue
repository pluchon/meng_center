<template>
  <div
    class="account-posts"
    :class="{ 'is-likes': !folderId }"
  >
    <div
      v-if="folderId"
      class="account-folder-tools"
    >
      <button
        class="account-folder-back"
        :aria-label="t('account.back')"
        @click="$emit('back')"
      >
        <el-icon><Back /></el-icon>
      </button><div class="account-folder-name">
        <h2>
          <span :class="{ 'is-editing': editing.name }"><input
            v-if="editing.name"
            ref="nameInput"
            v-model="draft.name"
            class="account-inline-name"
            :maxlength="Account.NAME_MAX"
            :aria-label="t('account.editName')"
            :style="{ width: Math.max(4, Array.from(draft.name).length) + 'em' }"
            :disabled="Boolean(saving)"
            @keydown="editKey('name', $event)"
          ><template v-else>{{ folder?.isDefault ? t('account.defaultFolder') : folder?.name }}</template></span>
        </h2><button
          v-if="folder && !folder.isDefault"
          ref="nameEdit"
          class="account-folder-edit"
          :aria-label="t('account.editName')"
          :aria-expanded="editing.name"
          :disabled="Boolean(saving)"
          @click="editField('name')"
        >
          <el-icon><EditPen /></el-icon>
        </button><button
          v-if="editing.name"
          class="account-inline-confirm"
          :aria-label="t('account.confirmName')"
          :aria-busy="saving === 'name'"
          :disabled="Boolean(saving)"
          @click="saveField('name')"
        >
          <el-icon><Check /></el-icon>
        </button>
      </div><span
        class="account-folder-sparkle"
        aria-hidden="true"
      /><div class="account-folder-summary">
        <template v-if="folder && !folder.isDefault">
          <el-button
            class="account-delete"
            :loading="deleting"
            :disabled="Boolean(saving)"
            @click="$emit('delete', folder)"
          >
            <el-icon><Delete /></el-icon>
            {{ t('account.deleteFolder') }}
          </el-button>
        </template><i18n-t
          keypath="account.count"
          scope="global"
          tag="span"
          class="account-folder-total"
        >
          <template #count>
            <b>{{ total }}</b>
          </template>
        </i18n-t>
      </div>
    </div>
    <PageState
      v-if="resource.loading"
      state="loading"
    />
    <PageState
      v-else-if="resource.error"
      state="error"
      @retry="load"
    />
    <PageState
      v-else-if="!resource.data.records.length"
      state="empty"
    />
    <div
      v-else
      class="account-post-grid"
    >
      <article
        v-for="post in resource.data.records"
        :key="post.id"
        class="account-post"
        :class="[{ inactive: !post.active }, 'category-' + post.category.toLowerCase()]"
      >
        <RouterLink
          class="account-post-link"
          :to="{ name: RouteName.POST_DETAIL, params: { id: post.id }, query: { from: 'account', pane: folderId ? 'folders' : 'likes', folder: folderId || undefined, page } }"
        >
          <div
            class="account-post-cover"
            :class="'category-' + post.category.toLowerCase()"
          >
            <img
              v-if="post.cover"
              :src="contentImage(post.cover)"
              alt=""
              loading="lazy"
              decoding="async"
            ><svg
              v-else
              viewBox="0 0 96 72"
              aria-hidden="true"
              preserveAspectRatio="xMidYMid slice"
            ><use :href="accountScenes + '#post-' + post.category.toLowerCase()" /></svg>
          </div>
          <div class="account-post-info">
            <h3>{{ post.title }}</h3><div>
              <span
                class="account-category"
                :class="'category-' + post.category.toLowerCase()"
              >{{ t('home.categories.' + post.category) }}</span><span class="account-post-author"><i aria-hidden="true">{{ (post.author?.nickname || t('home.authorFallback')).slice(0, 1) }}</i><span>{{ post.author?.nickname || t('home.authorFallback') }}</span></span><time>{{ t(folderId ? 'account.savedAt' : 'account.likedAt', { time: date(post.savedAt) }) }}</time>
            </div>
          </div>
        </RouterLink>
        <button
          class="account-post-action"
          :class="{ selected: post.active, pink: !folderId }"
          :disabled="post.pending"
          :aria-pressed="post.active"
          :aria-label="t(folderId ? (post.active ? 'account.unfavorite' : 'account.restoreFavorite') : (post.active ? 'account.unlike' : 'account.restoreLike'))"
          @click="toggle(post)"
        >
          <el-icon>
            <IconHeart
              v-if="!folderId"
              :fill="post.active ? 'currentColor' : 'none'"
            /><Star
              v-else
              :fill="post.active ? 'currentColor' : 'none'"
            />
          </el-icon>
        </button>
      </article>
    </div>
    <footer class="account-post-footer">
      <div
        v-if="folder"
        class="account-folder-description"
      >
        <input
          v-if="editing.description"
          ref="descriptionInput"
          v-model="draft.description"
          class="account-inline-description"
          :maxlength="Account.BIO_MAX"
          :aria-label="t('account.editDescription')"
          :placeholder="t('account.folderDescriptionPlaceholder')"
          :disabled="Boolean(saving)"
          @keydown="editKey('description', $event)"
        ><p v-else>
          {{ folder.isDefault ? t('account.defaultDescription') : folder.description || t('account.noDescription') }}
        </p><button
          v-if="!folder.isDefault"
          ref="descriptionEdit"
          :aria-label="t('account.editDescription')"
          :aria-expanded="editing.description"
          :disabled="Boolean(saving)"
          @click="editField('description')"
        >
          <el-icon><EditPen /></el-icon>
        </button><button
          v-if="editing.description"
          class="account-inline-confirm"
          :aria-label="t('account.confirmDescription')"
          :aria-busy="saving === 'description'"
          :disabled="Boolean(saving)"
          @click="saveField('description')"
        >
          <el-icon><Check /></el-icon>
        </button>
      </div><el-pagination
        v-if="resource.data.total > pageSize"
        :current-page="page"
        :page-size="pageSize"
        :total="resource.data.total"
        :layout="page > 1 ? 'prev, pager, next' : 'pager, next'"
        @current-change="$emit('page', $event)"
      />
    </footer>
  </div>
</template>
<script src="./AccountPosts.js"></script>
<style lang="scss" scoped src="./AccountPosts.scss"></style>
