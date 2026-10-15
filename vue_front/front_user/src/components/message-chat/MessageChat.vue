<template>
  <section class="message-chat">
    <header class="chat-header">
      <MessageAvatar
        :user="im.current.peer"
        :size="62"
      />
      <h2>{{ im.current.peer.nickname||t('home.authorFallback') }}</h2>
      <div class="chat-actions">
        <label
          class="block-toggle"
          :class="{checked:blocked,changing}"
        >
          <input
            type="checkbox"
            :checked="blocked"
            :disabled="changing"
            @change="toggleBlock"
          >
          <span
            class="block-box"
            aria-hidden="true"
          ><img
            :src="tickIcon"
            alt=""
          ></span>{{ t('im.block') }}
        </label>
        <span
          class="presence-pill"
          :class="{online:im.current.online}"
        ><i />{{ t(im.current.online?'im.online':'im.offline') }}</span>
        <button
          v-if="im.current.id"
          class="delete-chat"
          type="button"
          :disabled="changing"
          :title="t('im.remove')"
          :aria-label="t('im.remove')"
          @click="removeChat"
        >
          <img
            class="message-art-icon"
            :src="messageTrashIcon"
            width="22"
            height="22"
            alt=""
            aria-hidden="true"
          >
        </button>
      </div>
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
      <PageState
        v-if="!rows.length"
        state="empty"
        art="chat"
        size="large"
        :text="t('im.emptyChat')"
      />
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
          <Transition
            name="revoked-swap"
            mode="out-in"
          >
            <div
              v-if="item.revoked"
              key="revoked"
              class="message-revoked"
            >
              <span>{{ t(item.senderId===im.userId?'im.revokedSelf':'im.revokedPeer') }}</span>
              <button
                v-if="item.senderId===im.userId&&(item.text||item.items.length)"
                class="revoke-edit"
                type="button"
                :disabled="disabled||busy"
                @click="reedit(item)"
              >
                {{ t('im.reedit') }}
              </button>
            </div>
            <div
              v-else
              key="live"
              class="message-content"
            >
              <div class="message-stack">
                <div
                  v-if="item.text||item.items.length===1"
                  class="message-bubble"
                >
                  <CommentBody
                    v-if="item.text"
                    :content="item.text"
                  />
                  <MessageImages
                    v-if="item.items.length===1"
                    class="bubble-attachment"
                    large
                    :items="item.items"
                  />
                </div>
                <MessageImages
                  v-if="item.items.length>1"
                  :items="item.items"
                />
              </div>
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
              <div
                v-else
                class="message-meta"
              >
                <Transition name="revoke-btn">
                  <button
                    v-if="canRevoke(item)"
                    class="message-revoke"
                    type="button"
                    :disabled="revoking===item.id"
                    @click="revoke(item)"
                  >
                    <el-icon><IconArrowBackUp /></el-icon>{{ t('im.revoke') }}
                  </button>
                </Transition>
                <small
                  class="message-receipt"
                  :class="{unread:item.senderId===im.userId&&item.id>im.current.peerReadMessageId}"
                >{{ t(item.pending?'im.sending':item.id<=(item.senderId===im.userId?im.current.peerReadMessageId:im.current.lastReadMessageId)?'im.read':'im.unread') }}</small>
              </div>
            </div>
          </Transition>
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
          width="min(388px, calc(100vw - 32px))"
          popper-class="message-emotion-popper"
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
          <EmotionPanel @select="insert" />
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
        <TransitionGroup
          name="chip"
          tag="div"
          class="composer-chips"
        >
          <span
            v-for="item in selectedEmotions"
            :key="item.key"
            class="chip emotion-chip"
          >
            <img
              class="chip-image"
              :src="item.image"
              :alt="item.name"
            >
            <button
              type="button"
              :aria-label="t('im.removeEmotion')"
              @click="removeEmotion(item)"
            >
              <img
                class="message-art-icon"
                :src="messageCloseIcon"
                width="12"
                height="12"
                alt=""
                aria-hidden="true"
              >
            </button>
          </span>
          <span
            v-for="item in uploads.images.value"
            :key="item.key"
            class="chip image-chip"
            :class="item.status"
          >
            <img
              class="chip-image"
              :src="item.preview"
              :alt="t('im.image')"
            >
            <small v-if="item.status!=='ready'">{{ t('im.progress',{count:item.progress}) }}</small>
            <button
              type="button"
              :aria-label="t('im.removeImage')"
              @click="uploads.remove(item)"
            >
              <img
                class="message-art-icon"
                :src="messageCloseIcon"
                width="12"
                height="12"
                alt=""
                aria-hidden="true"
              >
            </button>
            <button
              v-if="item.status==='failed'"
              type="button"
              class="chip-retry"
              :aria-label="t('im.retry')"
              @click="uploads.retry(item)"
            >
              <img
                class="message-art-icon"
                :src="messageRefreshIcon"
                width="12"
                height="12"
                alt=""
                aria-hidden="true"
              >
            </button>
          </span>
        </TransitionGroup>
      </div>
      <div class="composer-field">
        <div class="composer-box">
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
          <small
            class="composer-count"
            :class="{over:count>Im.TEXT_MAX}"
          >{{ t('im.count',{count,max:Im.TEXT_MAX}) }}</small>
        </div>
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
