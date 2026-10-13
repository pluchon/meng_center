<template>
  <el-dialog
    :model-value="favorites.open"
    :title="t('account.chooseFolder')"
    width="420px"
    class="favorite-picker"
    append-to-body
    @close="favorites.finish(null)"
  >
    <div class="favorite-picker-list">
      <button
        v-for="folder in favorites.folders"
        :key="folder.id"
        @click="favorites.finish(folder.id)"
      >
        <el-icon><FolderOpened /></el-icon><span>{{ folder.isDefault ? t('account.defaultFolder') : folder.name }}</span><small>{{ t('account.count', { count: folder.count }) }}</small>
      </button>
    </div>
  </el-dialog>
</template>
<script src="./FavoritePicker.js"></script>
<style lang="scss" src="./FavoritePicker.scss"></style>
