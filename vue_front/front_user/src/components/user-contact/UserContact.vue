<template>
  <el-popover
    placement="bottom-start"
    trigger="click"
    :width="220"
  >
    <template #reference>
      <button
        class="user-contact-avatar"
        type="button"
        :aria-label="t('im.profile',{name:author.nickname||t('home.authorFallback')})"
      >
        <el-avatar
          :src="author.avatar||userAvatar"
          :size="size"
        />
      </button>
    </template>
    <div class="user-contact-card">
      <strong>{{ author.nickname||t('home.authorFallback') }}</strong><small>{{ t('home.level',{level:author.level}) }}</small><button
        v-if="author.id!==user.current?.id"
        type="button"
        @click="message"
      >
        {{ t('im.sendMessage') }}
      </button>
    </div>
  </el-popover>
</template>
<script src="./UserContact.js"></script>
<style lang="scss" scoped src="./UserContact.scss"></style>
