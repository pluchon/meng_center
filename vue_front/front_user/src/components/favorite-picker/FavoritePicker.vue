<template>
  <el-dialog
    :model-value="favorites.open"
    width="740px"
    class="favorite-picker"
    modal-class="favorite-picker-overlay"
    align-center
    :show-close="false"
    :before-close="beforeClose"
    append-to-body
    @close="favorites.close()"
  >
    <template #header>
      <div class="favorite-picker-head">
        <span
          class="favorite-picker-icon"
          aria-hidden="true"
        ><el-icon><FolderOpened /></el-icon></span>
        <h2>{{ t('account.chooseFolder') }}</h2>
        <button
          type="button"
          class="favorite-picker-close"
          :aria-label="t('account.closePicker')"
          @click="leave"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>
    <PageState
      v-if="favorites.error"
      state="error"
      @retry="favorites.turn(favorites.page)"
    />
    <div
      v-else
      class="favorite-picker-list"
      :aria-busy="favorites.loading"
      :class="{loading:favorites.loading}"
    >
      <button
        v-for="(folder,index) in favorites.records"
        :key="folder.id"
        type="button"
        role="checkbox"
        :aria-checked="favorites.selected.includes(folder.id)"
        :class="['tone-'+index%3,{checked:favorites.selected.includes(folder.id)}]"
        :disabled="favorites.saving"
        @click="pick(folder)"
      >
        <span class="favorite-picker-cover"><img
          :src="folder.cover?contentImage(folder.cover):accountFolderCover"
          alt=""
          loading="lazy"
          decoding="async"
        ></span>
        <span class="favorite-picker-text">
          <strong>{{ folder.isDefault ? t('account.defaultFolder') : folder.name }}</strong>
          <small>{{ t('account.count', { count: folder.count }) }}</small>
          <span
            class="favorite-picker-check"
            aria-hidden="true"
          ><el-icon><Check /></el-icon></span>
        </span>
        <em v-if="folder.isDefault">{{ t('account.defaultBadge') }}</em>
      </button>
    </div>
    <template #footer>
      <div class="favorite-picker-foot">
        <button
          v-if="favorites.dirty"
          type="button"
          class="favorite-picker-save"
          :disabled="favorites.saving"
          @click="favorites.save()"
        >
          <el-icon><Check /></el-icon>{{ t('account.pickerSave',{count:favorites.selected.length,max:Account.POST_FOLDERS_MAX}) }}
        </button>
        <span
          v-else
          class="favorite-picker-count"
        >{{ t('account.pickerCount',{count:favorites.selected.length,max:Account.POST_FOLDERS_MAX}) }}</span>
        <FeedPagination
          v-if="totalPages>1"
          :page="favorites.page"
          :total-pages="totalPages"
          :show-top="false"
          @page="favorites.turn"
        />
      </div>
    </template>
  </el-dialog>
</template>
<script src="./FavoritePicker.js"></script>
<style lang="scss" src="./FavoritePicker.scss"></style>
