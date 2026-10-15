<template>
  <form
    class="comment-composer"
    novalidate
    @submit.prevent="send"
    @paste="paste"
  >
    <el-avatar
      class="composer-avatar"
      :src="avatar"
      aria-hidden="true"
    />
    <Transition
      name="reply-target"
      mode="out-in"
    >
      <div
        v-if="target"
        :key="target.id"
        class="composer-target"
      >
        {{ t('detail.replyTo', { name: target.author.nickname || t('home.authorFallback') })
        }}<button
          type="button"
          :aria-label="t('detail.removeReply')"
          :disabled="busy"
          @click="$emit('clear-target')"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </Transition>
    <div
      v-if="images.length || selectedEmotions.length"
      class="composer-images"
    >
      <div
        v-for="item in selectedEmotions"
        :key="item.key"
        class="composer-image"
      >
        <img
          :src="item.image"
          :alt="item.name"
        >
        <button
          type="button"
          :aria-label="t('editor.removeImage')"
          :disabled="busy"
          @click="removeEmotion(item)"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>
      <div
        v-for="image in images"
        :key="image.key"
        class="composer-image"
      >
        <img
          :src="image.preview"
          :alt="t('editor.imageAlt')"
        ><button
          type="button"
          :aria-label="t('editor.removeImage')"
          :disabled="busy"
          @click="remove(image)"
        >
          <el-icon><Close /></el-icon>
        </button><button
          v-if="image.status === 'failed'"
          type="button"
          class="retry"
          :aria-label="t('editor.retryImage')"
          @click="retry(image)"
        >
          <el-icon><Refresh /></el-icon>
        </button><small v-else-if="image.status !== 'ready'">{{
          t('editor.uploadProgress', { progress: image.progress })
        }}</small>
      </div>
      <button
        v-if="attachmentCount < Comment.ATTACHMENTS"
        type="button"
        class="add-image"
        :aria-label="t('editor.addImage')"
        :disabled="busy"
        @click="input.click()"
      >
        <el-icon><Plus /></el-icon>
      </button><small class="composer-image-count">{{
        t('editor.counters', { count: attachmentCount, max: Comment.ATTACHMENTS })
      }}</small>
    </div>
    <div class="composer-field">
      <textarea
        ref="field"
        v-model="text"
        :placeholder="t('detail.placeholder')"
        :aria-label="t('detail.write')"
        :disabled="busy"
        rows="3"
        @input="onInput"
        @compositionend="onInput"
        @keydown="mentionKey"
        @keyup="detect"
        @click="detect"
        @blur="closeMention"
      /><small>{{ t('editor.counters', { count, max: Comment.LENGTH }) }}</small>
      <Transition name="mention-pop">
        <ul
          v-if="showMentions"
          class="mention-list"
          role="listbox"
          :aria-label="t('detail.mentionTitle')"
        >
          <li
            v-for="(item, index) in matches"
            :key="item.id"
            role="option"
            :aria-selected="index === mentionActive"
            :class="{ active: index === mentionActive }"
            @mousedown.prevent="pick(item)"
            @mouseenter="mentionActive = index"
          >
            <el-avatar
              :size="28"
              :src="item.avatar ? avatarUrl(item.avatar) : userAvatar"
            />
            <span class="mention-name">{{ item.nickname }}</span>
            <em v-if="item.postAuthor">{{ t('detail.author') }}</em>
          </li>
          <li
            v-if="!matches.length"
            class="mention-empty"
          >
            {{ t('detail.mentionEmpty') }}
          </li>
        </ul>
      </Transition>
    </div>
    <CollapsePanel :open="panel">
      <div class="composer-emotions">
        <EmotionPanel @select="insert" />
      </div>
    </CollapsePanel>
    <footer>
      <button
        type="button"
        :class="{ selected: panel }"
        :aria-expanded="panel"
        :disabled="busy"
        @click="panel = !panel"
      >
        <el-icon><IconMoodSmile /></el-icon>{{ t('detail.emotion') }}
      </button><button
        type="button"
        :disabled="busy || attachmentCount >= Comment.ATTACHMENTS"
        @click="input.click()"
      >
        <el-icon><PictureIcon /></el-icon>{{ t('detail.images') }}
      </button><el-button
        class="send-button"
        type="primary"
        native-type="submit"
        :disabled="!valid"
        :loading="busy"
      >
        <el-icon class="send-icon">
          <IconSend />
        </el-icon>{{ t('detail.send') }}<i
          class="send-spark"
          aria-hidden="true"
          :style="{ maskImage: 'url(' + JSON.stringify(communitySparkle) + ')' }"
        />
      </el-button>
    </footer>
    <input
      ref="input"
      class="file-input"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif"
      multiple
      :disabled="busy"
      @change="picked"
    >
  </form>
</template>
<script src="./CommentComposer.js"></script>
<style lang="scss" scoped src="./CommentComposer.scss"></style>
