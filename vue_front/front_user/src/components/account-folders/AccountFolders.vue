<template>
  <div class="account-folders">
    <input
      ref="coverInput"
      class="account-folder-file"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      tabindex="-1"
      @change="coverSelected"
    >
    <div class="account-folder-grid">
      <button
        v-for="(folder, index) in rows"
        :key="folder.id"
        class="account-folder"
        :class="'folder-' + (folder.color || 'GOLD').toLowerCase()"
        :style="{ '--folder-delay': index % 8 * 35 + 'ms' }"
        @click="$emit('open', folder)"
      >
        <div
          class="account-folder-cover"
          :class="'tone-' + index % 3"
        >
          <Transition
            name="folder-cover"
            mode="out-in"
          >
            <img
              :key="folder.cover || 'default'"
              :src="folder.cover ? contentImage(folder.cover) : accountFolderCover"
              alt=""
              loading="lazy"
              decoding="async"
            >
          </Transition>
          <span
            v-if="!folder.isDefault"
            class="account-folder-change"
            :class="{ busy: changing === folder.id }"
            role="button"
            tabindex="0"
            @click.stop="pickCover(folder)"
            @keydown.enter.stop.prevent="pickCover(folder)"
            @keydown.space.stop.prevent="pickCover(folder)"
          >
            <CoverCamera />{{ changing === folder.id ? t('account.coverChanging') : t('account.changeCover') }}
          </span>
        </div>
        <span
          v-if="folder.isDefault"
          class="account-default-badge"
        >{{ t('account.defaultBadge') }}</span>
        <h3>{{ folder.isDefault ? t('account.defaultFolder') : folder.name }}</h3>
        <div class="account-folder-meta">
          <span class="account-folder-quantity"><b>{{ folder.count }}</b> {{ t('account.articles') }}</span><span>{{ folder.latestAt ? t('account.recentSaved', { time: date(folder.latestAt) }) : t('account.noDate') }}</span>
        </div>
      </button>
      <button
        v-if="!resource.error && !resource.loading && rows.length >= total && total < Account.FOLDERS_MAX"
        class="account-folder account-folder-create"
        aria-haspopup="dialog"
        @click="$emit('create')"
      >
        <el-icon><Plus /></el-icon><b>{{ t('account.create') }}</b>
      </button>
    </div>
    <PageState
      v-if="resource.loading"
      state="loading"
    />
    <PageState
      v-else-if="resource.error"
      state="error"
      @retry="loadMore"
    />
    <div
      ref="sentinel"
      class="account-folder-sentinel"
    >
      <el-button
        v-if="rows.length < total && !resource.loading"
        @click="loadMore"
      >
        {{ t('account.loadMore') }}
      </el-button>
    </div>
  </div>
</template>
<script src="./AccountFolders.js"></script>
<style lang="scss" scoped src="./AccountFolders.scss"></style>
