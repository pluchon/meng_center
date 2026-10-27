<template>
  <div class="naitang-assistant">
    <button
      v-if="!opened"
      class="naitang-launcher"
      type="button"
      :aria-label="t('companion.open')"
      @click="open"
    >
      <img
        :src="companionNekoAvatar"
        :alt="t('companion.name')"
      >
    </button>
    <section
      v-else
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
        <div class="naitang-menu">
          <button
            type="button"
            :aria-label="t('companion.more')"
            :aria-expanded="menu"
            @click="menu = !menu"
          >
            <IconDots />
          </button>
          <div
            v-if="menu"
            class="naitang-menu-popover"
          >
            <button
              type="button"
              @click="clearMemories"
            >
              {{ t('companion.clearMemories') }}
            </button>
          </div>
        </div>
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
        <button
          class="naitang-back"
          type="button"
          @click="listing = false"
        >
          <IconArrowLeft />{{ t('companion.back') }}
        </button>
        <h3>{{ t('companion.history') }}</h3>
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
            :key="message.id"
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
              <div
                v-if="message.imageIds?.length"
                class="naitang-message-images"
              >
                <a
                  v-for="id in message.imageIds"
                  :key="id"
                  :href="companionImage(id)"
                  target="_blank"
                  rel="noopener"
                ><img
                  :src="companionImage(id)"
                  :alt="t('companion.image')"
                  loading="lazy"
                ></a>
              </div>
              <ul
                v-if="message.sources?.length"
                class="naitang-sources"
              >
                <li
                  v-for="source in message.sources"
                  :key="source.postId"
                >
                  <router-link :to="{ name: RouteName.POST_DETAIL, params: { id: source.postId } }">
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
          <p
            v-if="status"
            class="naitang-status"
          >
            {{ status }}
          </p>
          <p
            v-if="typing"
            class="naitang-note"
          >
            {{ t('companion.typing') }}
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
          <textarea
            ref="editor"
            v-model="input"
            :disabled="blocked || !selected"
            :maxlength="Companion.MAX_LENGTH"
            :placeholder="t('companion.placeholder')"
            :aria-label="t('companion.placeholder')"
            rows="2"
            @keydown.enter.exact.prevent="!$event.isComposing && send()"
          />
          <div class="naitang-compose-tools">
            <input
              ref="picker"
              type="file"
              accept="image/*"
              multiple
              hidden
              @change="pick"
            >
            <button
              type="button"
              :disabled="blocked || !selected || attachments.length >= Companion.MAX_IMAGES"
              :aria-label="t('companion.addImage')"
              @click="picker.click()"
            >
              <IconPhoto />
            </button>
            <small v-if="usage.messages !== null">{{
              t('companion.remaining', { count: usage.messages })
            }}</small>
            <button
              class="naitang-send"
              type="submit"
              :disabled="!canSend"
              :aria-label="t('companion.send')"
            >
              <IconSend />
            </button>
          </div>
        </form>
      </template>
      <footer class="naitang-credit">
        {{ t('companion.disclaimer') }}
      </footer>
    </section>
  </div>
</template>
<script src="./NaitangAssistant.js"></script>
<style lang="scss" scoped src="./NaitangAssistant.scss"></style>
