<template>
  <section class="message-chat">
    <header class="chat-header">
      <MessageAvatar
        :user="im.current.peer"
        :size="54"
      />
      <h2>{{ im.current.peer.nickname||t('home.authorFallback') }}</h2>
      <span
        class="presence-pill"
        :class="{online:im.current.online}"
      ><i />{{ t(im.current.online?'im.online':'im.offline') }}</span>
    </header>
    <div
      ref="scroll"
      class="chat-history"
      role="log"
      :aria-label="t('im.chat')"
      @scroll.passive="scrollHistory"
    >
      <button
        v-if="im.hasMore"
        class="history-more"
        type="button"
        :disabled="im.historyLoading"
        @click="earlier"
      >
        {{ t(im.historyLoading?'im.loading':'im.earlier') }}
      </button>
      <button
        v-if="im.historyError"
        class="history-error"
        type="button"
        @click="earlier"
      >
        {{ im.historyError }}{{ t('im.separator') }}{{ t('im.retry') }}
      </button>
      <p
        v-if="!rows.length"
        class="chat-empty"
      >
        {{ t('im.emptyChat') }}
      </p>
      <TransitionGroup
        name="message-appear"
        tag="div"
        class="message-list"
      >
        <div
          v-for="item in rows"
          :key="item.senderId+'-'+item.clientMsgId"
          class="message-row"
          :class="{self:item.senderId===im.userId,pending:item.pending,failed:item.error}"
        >
          <time
            v-if="item.timeLabel"
            class="message-time"
            :datetime="item.createTime"
          >{{ formatTime(item.createTime) }}</time>
          <div class="message-content">
            <div
              v-if="item.type===MessageKind.TEXT"
              class="message-bubble"
            >
              <CommentBody :content="item.content" />
            </div>
            <MessageImages
              v-else
              :images="item.images"
            />
            <button
              v-if="item.error"
              class="message-failure"
              type="button"
              :title="item.error"
              @click="im.send('',[],item)"
            >
              <img
                class="message-art-icon"
                :src="messageRefreshIcon"
                width="14"
                height="14"
                alt=""
                aria-hidden="true"
              >{{ t('im.failed') }}
            </button>
            <small
              v-else
              class="message-receipt"
              :class="{unread:item.senderId===im.userId&&item.id>im.current.peerReadMessageId}"
            >{{ t(item.pending?'im.sending':item.id<=(item.senderId===im.userId?im.current.peerReadMessageId:im.current.lastReadMessageId)?'im.read':'im.unread') }}</small>
          </div>
        </div>
      </TransitionGroup>
    </div>
    <div class="chat-composer">
      <p
        v-if="disabled"
        class="composer-warning"
        role="status"
      >
        {{ t(im.current.unavailable?'im.unavailable':'im.waitReply') }}
      </p>
      <p
        v-else-if="remaining!==null"
        class="composer-hint"
      >
        {{ t('im.remaining',{count:remaining}) }}
      </p>
      <div class="composer-tools">
        <el-popover
          v-model:visible="emotions"
          placement="top-start"
          :width="250"
          trigger="click"
        >
          <template #reference>
            <button
              class="emotion-button"
              type="button"
              :disabled="disabled||busy"
              :aria-expanded="emotions"
            >
              <img
                class="message-art-icon"
                :src="navMengIcon"
                width="18"
                height="18"
                alt=""
                aria-hidden="true"
              >{{ t('im.emotion') }}
            </button>
          </template>
          <div class="message-emotions">
            <button
              v-for="item in Emotions"
              :key="item.name"
              type="button"
              :title="item.name"
              @click="insert(item)"
            >
              <img
                :src="item.image"
                :alt="item.name"
              >
            </button>
          </div>
        </el-popover>
        <button
          class="image-button"
          type="button"
          :disabled="disabled||busy"
          @click="input.click()"
        >
          <img
            class="message-art-icon"
            :src="messagePhotoIcon"
            width="18"
            height="18"
            alt=""
            aria-hidden="true"
          >{{ t('im.image') }}
        </button>
        <small :class="{over:count>Im.TEXT_MAX}">{{ t('im.count',{count,max:Im.TEXT_MAX}) }}</small>
      </div>
      <div
        v-if="uploads.images.value.length"
        class="composer-images"
      >
        <div
          v-for="item in uploads.images.value"
          :key="item.key"
          class="composer-image"
        >
          <img
            :src="item.preview"
            :alt="t('im.image')"
          ><small v-if="item.status!=='ready'">{{ t('im.progress',{count:item.progress}) }}</small>
          <button
            type="button"
            :aria-label="t('im.removeImage')"
            @click="uploads.remove(item)"
          >
            <img
              class="message-art-icon"
              :src="messageCloseIcon"
              width="14"
              height="14"
              alt=""
              aria-hidden="true"
            >
          </button>
          <button
            v-if="item.status==='failed'"
            type="button"
            class="upload-retry"
            @click="uploads.retry(item)"
          >
            {{ t('im.retry') }}
          </button>
        </div>
      </div>
      <p
        v-if="error||uploads.notice.value"
        class="composer-warning"
        role="alert"
      >
        {{ error||uploads.notice.value }}
      </p>
      <div class="composer-field">
        <textarea
          ref="field"
          v-model="text"
          rows="1"
          :disabled="disabled||busy"
          :aria-label="t('im.placeholder')"
          :placeholder="t('im.placeholder')"
          @input="resize"
          @keydown="keydown"
          @paste="paste"
        />
        <button
          class="send-button"
          type="button"
          :disabled="!valid"
          :aria-label="t('im.send')"
          @click="send"
        >
          <img
            class="message-art-icon"
            :src="messageSendIcon"
            width="24"
            height="24"
            alt=""
            aria-hidden="true"
          >
        </button>
      </div>
      <input
        ref="input"
        class="upload-input"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        multiple
        @change="picked"
      >
    </div>
  </section>
</template>
<script src="./MessageChat.js"></script>
<style lang="scss" scoped src="./MessageChat.scss"></style>
