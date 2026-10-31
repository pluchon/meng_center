<template>
  <div class="naitang-assistant">
    <button
      class="naitang-launcher"
      type="button"
      :aria-label="t(opened ? 'companion.close' : 'companion.open')"
      :aria-expanded="opened"
      @click="opened ? close() : open()"
    >
      <img
        :src="companionNekoAvatar"
        :alt="t('companion.name')"
      >
    </button>
    <span
      ref="plane"
      class="naitang-plane"
      aria-hidden="true"
    ><IconSend /></span>
    <Transition name="naitang-pop">
      <div
        v-if="opened"
        class="naitang-bubble-shell"
      >
        <span
          class="naitang-ear left"
          aria-hidden="true"
        />
        <span
          class="naitang-ear right"
          aria-hidden="true"
        />
        <span
          class="naitang-tail"
          aria-hidden="true"
        />
        <section
          class="naitang-panel"
          role="dialog"
          aria-labelledby="naitang-title"
          @keydown.esc.stop="close"
        >
          <header class="naitang-header">
            <img
              class="naitang-avatar"
              :src="companionNekoAvatar"
              alt=""
            >
            <h2 id="naitang-title">
              {{ t('companion.title') }}
            </h2>
            <span
              class="naitang-spark first"
              aria-hidden="true"
              :style="sparkleMask"
            />
            <span
              class="naitang-spark second"
              aria-hidden="true"
              :style="sparkleMask"
            />
            <button
              type="button"
              :disabled="atLimit || creating"
              :title="atLimit ? t('companion.sessionLimit') : t('companion.newSession')"
              :aria-label="t('companion.newSession')"
              @click="newSession"
            >
              <IconPlus />
            </button>
            <button
              type="button"
              :aria-label="t('companion.history')"
              :aria-expanded="listing"
              @click="listing = !listing"
            >
              <IconHistory />
            </button>
            <button
              type="button"
              :disabled="sending > 0"
              :title="t('companion.clearMemories')"
              :aria-label="t('companion.clearMemories')"
              @click="clearMemories"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="m16 22-1-4" />
                <path d="M19 14a1 1 0 0 0 1-1v-1a2 2 0 0 0-2-2h-3a1 1 0 0 1-1-1V4a2 2 0 0 0-4 0v5a1 1 0 0 1-1 1H6a2 2 0 0 0-2 2v1a1 1 0 0 0 1 1" />
                <path d="M5 14h14l2 6.8a1 1 0 0 1-1 1.2H4a1 1 0 0 1-1-1.2z" />
                <path d="m8 22 1-4" />
              </svg>
            </button>
            <button
              type="button"
              :aria-label="t('companion.close')"
              @click="close"
            >
              <IconX />
            </button>
          </header>
          <div
            v-if="listing"
            class="naitang-history"
          >
            <div class="naitang-history-head">
              <h3>{{ t('companion.history') }}</h3>
              <button
                type="button"
                @click="listing = false"
              >
                <IconArrowLeft />{{ t('companion.back') }}
              </button>
            </div>
            <div
              v-for="session in sessions"
              :key="session.id"
              class="naitang-session"
              :class="{ selected: session.id === selected }"
            >
              <button
                type="button"
                @click="choose(session.id)"
              >
                <span>{{ session.title || t('companion.untitled') }}</span>
                <small v-if="session.generating">{{ t('companion.generating') }}</small>
              </button>
              <button
                type="button"
                :aria-label="t('companion.deleteSession')"
                @click="removeSession(session.id)"
              >
                <IconTrash />
              </button>
            </div>
            <button
              v-if="sessions.length < total"
              type="button"
              @click="listSessions(true).catch((error) => (failure = error))"
            >
              {{ t('companion.moreSessions') }}
            </button>
          </div>
          <template v-else>
            <div
              ref="log"
              class="naitang-messages"
              role="log"
              :aria-label="t('companion.title')"
              aria-live="polite"
            >
              <button
                v-if="before"
                class="naitang-older"
                type="button"
                :disabled="loading"
                @click="history(true)"
              >
                {{ t('companion.older') }}
              </button>
              <p
                v-if="loading"
                class="naitang-note"
              >
                {{ t('companion.loading') }}
              </p>
              <div
                v-if="loadError"
                class="naitang-error"
                role="alert"
              >
                <p>{{ loadError.message }}</p>
                <button
                  type="button"
                  @click="selected ? history() : open()"
                >
                  {{ t('companion.retry') }}
                </button>
              </div>
              <p
                v-if="!selected && !loading"
                class="naitang-note"
              >
                {{ t('companion.empty') }}
              </p>
              <article
                v-for="message in messages"
                :key="message.clientMessageId || message.id"
                class="naitang-message"
                :class="{ own: message.role === 'USER' }"
              >
                <img
                  v-if="message.role === 'CHARACTER'"
                  class="naitang-avatar"
                  :src="companionNekoAvatar"
                  :alt="t('companion.name')"
                >
                <div class="naitang-message-body">
                  <img
                    v-if="message.kind === 'STICKER' && sticker(message.sticker)"
                    class="naitang-sticker"
                    :src="sticker(message.sticker)"
                    :alt="message.sticker"
                  >
                  <p
                    v-else-if="message.content"
                    class="naitang-bubble"
                  >
                    <span
                      v-for="(part, index) in segments(message.content)"
                      :key="index"
                      :class="{ action: part.action }"
                    >{{ part.text }}</span>
                  </p>
                  <MessageImages
                    v-if="message.imageIds?.length"
                    :items="gallery(message)"
                  />
                  <ul
                    v-if="message.sources?.length"
                    class="naitang-sources"
                  >
                    <li
                      v-for="source in message.sources"
                      :key="source.type === 'WEB' ? source.url : source.postId"
                    >
                      <a
                        v-if="source.type === 'WEB'"
                        :href="source.url"
                        target="_blank"
                        rel="noopener noreferrer nofollow"
                      >
                        <span
                          v-if="source.site !== source.title"
                          class="naitang-source-site"
                        >{{ source.site }}</span>{{ source.title }}
                      </a>
                      <router-link
                        v-else
                        :to="{ name: RouteName.POST_DETAIL, params: { id: source.postId } }"
                      >
                        {{ source.title }}
                      </router-link>
                    </li>
                  </ul>
                  <button
                    v-if="message.failed || message.turnState === 'FAILED'"
                    class="naitang-retry"
                    type="button"
                    :disabled="sending > 0 || blocked"
                    @click="retry(message)"
                  >
                    {{ t('companion.retrySend') }}
                  </button>
                </div>
              </article>
              <article
                v-if="thinking"
                class="naitang-message naitang-thinking"
                role="status"
                :aria-label="t('companion.typing')"
              >
                <img
                  class="naitang-avatar"
                  :src="companionNekoAvatar"
                  alt=""
                >
                <div class="naitang-message-body">
                  <p class="naitang-bubble naitang-dots">
                    <span /><span /><span />
                  </p>
                  <small v-if="status">{{ status }}</small>
                </div>
              </article>
              <p
                v-if="memoryCleared"
                class="naitang-note"
              >
                {{ t('companion.cleared') }}
              </p>
              <aside
                v-if="hotlines.length"
                class="naitang-help"
              >
                <strong>{{ t('companion.crisis') }}</strong>
                <a
                  v-for="line in hotlines"
                  :key="line.phone"
                  :href="safeHelp(line)"
                  target="_blank"
                  rel="noopener"
                >{{ line.name }} {{ line.phone }}</a>
              </aside>
            </div>
            <form
              class="naitang-composer"
              @submit.prevent="send"
              @paste="paste"
              @dragover.prevent
              @drop.prevent="drop"
            >
              <p
                v-if="failure"
                class="naitang-error"
                role="alert"
              >
                {{ failure.message }}
              </p>
              <p
                v-if="usage.cooldownSeconds > 0"
                class="naitang-note"
              >
                {{ t('companion.cooldown', { seconds: Math.ceil(usage.cooldownSeconds) }) }}
              </p>
              <p
                v-else-if="usage.messages === 0"
                class="naitang-note"
              >
                {{ t('companion.quota') }}
              </p>
              <div
                v-if="attachments.length"
                class="naitang-attachments"
              >
                <div
                  v-for="image in attachments"
                  :key="image.preview"
                >
                  <img
                    :src="image.preview"
                    :alt="t('companion.image')"
                  >
                  <button
                    type="button"
                    :aria-label="t('companion.removeImage')"
                    @click="removeAttachment(image)"
                  >
                    <IconX />
                  </button>
                </div>
              </div>
              <p
                v-if="uploading"
                class="naitang-note"
              >
                {{ t('companion.uploading') }}
              </p>
              <div class="naitang-compose-row">
                <input
                  ref="picker"
                  type="file"
                  accept="image/*"
                  multiple
                  hidden
                  @change="pick"
                >
                <button
                  class="naitang-pick"
                  type="button"
                  :disabled="blocked || !selected || attachments.length >= Companion.MAX_IMAGES"
                  :aria-label="t('companion.addImage')"
                  @click="picker.click()"
                >
                  <IconPhoto />
                </button>
                <div class="naitang-field">
                  <textarea
                    ref="editor"
                    v-model="input"
                    :disabled="blocked || !selected"
                    :maxlength="Companion.MAX_LENGTH"
                    :placeholder="t('companion.placeholder')"
                    :aria-label="t('companion.placeholder')"
                    rows="1"
                    @keydown.enter.exact.prevent="!$event.isComposing && send()"
                  />
                  <button
                    ref="sendButton"
                    class="naitang-send"
                    type="submit"
                    :disabled="!canSend"
                    :aria-label="t('companion.send')"
                  >
                    <IconSend />
                  </button>
                </div>
              </div>
            </form>
          </template>
        </section>
      </div>
    </Transition>
  </div>
</template>
<script src="./NaitangAssistant.js"></script>
<style lang="scss" scoped src="./NaitangAssistant.scss"></style>
