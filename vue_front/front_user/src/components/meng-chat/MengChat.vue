<template>
  <section
    class="chat-card"
    :class="theme"
    :aria-label="t('meng.chat')"
  >
    <aside
      class="topic-sidebar"
      :aria-label="t('meng.sessions')"
    >
      <button
        class="meng-button primary new-topic"
        type="button"
        :disabled="creating || sending || topicFull"
        @click="newTopic"
      >
        <IconPlus />{{ t('meng.newTopic') }}
      </button>
      <p
        v-if="topicFull"
        class="topic-full"
      >
        {{ t('error.COMPANION_SESSION_LIMIT') }}
      </p>
      <div class="topic-list">
        <div
          v-for="s in sessionList"
          :key="s.id"
          class="topic-row"
          :class="{selected:String(sessionId)===String(s.id)}"
        >
          <button
            class="topic-select"
            type="button"
            @click="selectSession(s.id)"
          >
            <span class="topic-icon"><IconMessages /></span><span><strong>{{ s.title || t('meng.newTopic') }}</strong><small>{{ relativeTime(s.lastMessageTime) }}</small></span>
          </button>
          <button
            type="button"
            class="delete-topic"
            :aria-label="t('meng.deleteTopic')"
            :disabled="sending"
            @click="deleteTopic(s.id)"
          >
            <IconX />
          </button>
        </div>
        <button
          v-if="sessionList.length<sessionTotal"
          class="text-button"
          type="button"
          @click="moreTopics"
        >
          {{ t('meng.more') }}
        </button>
      </div>
      <span
        v-if="usage.usage"
        class="topic-remaining"
      ><IconMessageCircle />{{ t('meng.remaining',{count:usage.usage.messages}) }}</span>
    </aside>
    <main class="chat-main">
      <header class="chat-header">
        <img
          class="chat-avatar"
          :src="avatar(code)"
          :alt="character.name"
          width="60"
          height="60"
        >
        <div class="chat-identity">
          <div><h1>{{ character.name }}</h1><span class="stage-stamp">{{ character.stageName }}</span></div><div
            class="chat-progress"
            role="img"
            :aria-label="character.stageName"
          >
            <span :style="{width:progress(character)+'%'}" />
          </div>
        </div>
        <button
          class="meng-button secondary bond-button"
          type="button"
          @click="openBond(code)"
        >
          <IconHeart />{{ t('meng.bond') }}
        </button>
        <button
          v-if="sideClosed"
          class="meng-button secondary"
          type="button"
          @click="sideClosed=false"
        >
          <IconLayoutSidebarRightExpand />{{ t('meng.stateBar') }}
        </button>
      </header>
      <div class="chat-content">
        <component
          :is="watermarkIcon"
          class="chat-watermark"
          aria-hidden="true"
        />
        <MengState
          v-if="loading || loadError"
          :loading="loading"
          :error="loadError"
          @retry="retryHistory"
        />
        <div
          v-else-if="!sessionId"
          class="start-topic"
        >
          <MengState empty /><button
            class="meng-button primary"
            type="button"
            :disabled="creating"
            @click="newTopic"
          >
            {{ t('meng.newConversation') }}
          </button>
        </div>
        <div
          v-else
          :ref="setLog"
          class="chat-log"
          role="log"
          :aria-label="t('meng.chat')"
          aria-live="polite"
        >
          <button
            v-if="before"
            type="button"
            class="earlier-button"
            :disabled="historyLoading"
            @click="loadEarlier"
          >
            <img
              :src="earlierIcon"
              alt=""
              width="20"
              height="20"
            >{{ t('meng.earlier') }}
          </button>
          <template
            v-for="(m,index) in messages"
            :key="m.id"
          >
            <span
              v-if="index===0 || isNewDay(m,messages[index-1])"
              class="chat-time"
            >{{ date(m.createTime) }}</span>
            <MengWorkCard
              v-if="m.workId"
              :work-id="m.workId"
              :title="m.content"
              :item="recentWorks.find(w=>w.id===m.workId)"
              @open="openDialog('works',$event)"
            />
            <div
              v-else
              class="message-row"
              :class="{mine:m.role==='USER', system:m.role==='SYSTEM', continuation:index>0&&m.role===messages[index-1].role}"
            >
              <img
                v-if="m.role==='CHARACTER'"
                class="bubble-avatar"
                :src="avatar(code)"
                alt=""
                width="34"
                height="34"
              >
              <div class="message-bubble">
                <p v-if="m.content">
                  {{ m.content }}
                </p><div
                  v-if="m.imageIds?.length"
                  class="message-images"
                >
                  <a
                    v-for="image in m.imageIds"
                    :key="image"
                    :href="companionImage(image)"
                    target="_blank"
                    rel="noopener noreferrer"
                  ><img
                    :src="companionImage(image)"
                    :alt="t('meng.image')"
                    loading="lazy"
                  ></a>
                </div>
              </div>
            </div>
          </template>
          <div
            v-if="typing"
            class="typing-row"
            role="status"
            :aria-label="t('meng.typing',{name:character.name})"
          >
            <img
              :src="avatar(code)"
              alt=""
              width="34"
              height="34"
            ><span><img
              :src="replyDots"
              alt=""
              width="48"
              height="20"
            ></span>
          </div>
          <div
            v-if="statusText"
            class="tool-progress"
            role="status"
          >
            <IconLoader2 /><span>{{ statusText }}</span>
          </div>
          <aside
            v-if="hotlines.length"
            class="help-card"
          >
            <h2><IconHeartHandshake />{{ t('meng.help') }}</h2><a
              v-for="line in hotlines"
              :key="line.phone || line.url"
              :href="safeHelpUrl(line)"
            >{{ t('meng.hotline',line) }}</a><p>{{ t('meng.crisisHint') }}</p>
          </aside>
        </div>
      </div>
      <div
        class="chat-input-area"
        @dragover.prevent
        @drop="dropImages"
      >
        <p
          v-if="cooldownSeconds"
          class="rest-reminder"
        >
          {{ t('meng.cooldownRemaining',{time:cooldownText}) }}
        </p>
        <div
          v-if="sendError"
          class="send-error"
          role="alert"
        >
          <span>{{ failedText }}</span><button
            v-if="!inputDisabled"
            class="text-button"
            type="button"
            @click="send"
          >
            {{ t('meng.send') }}
          </button>
        </div>
        <p
          v-if="usage.usage?.dailyReminder"
          class="rest-reminder"
        >
          <IconPlant />{{ t('meng.dailyReminder') }}
        </p>
        <p
          v-if="usage.usage?.messages===0"
          class="rest-reminder"
        >
          {{ t('meng.quota') }}
        </p>
        <div
          v-if="attachments.length"
          class="attachments"
        >
          <span
            v-for="(image,index) in attachments"
            :key="image.id"
          ><img
            :src="image.url"
            :alt="t('meng.image')"
          ><button
            type="button"
            :aria-label="t('meng.removeImage')"
            @click="attachments.splice(index,1)"
          ><IconX /></button></span>
        </div>
        <div class="input-row">
          <textarea
            v-model="draft"
            rows="2"
            :maxlength="Companion.MAX_LENGTH"
            :placeholder="t('meng.say',{name:t('meng.'+code+'.short')})"
            :aria-label="t('meng.say',{name:character.name})"
            :disabled="inputDisabled"
            @keydown="keydown"
            @paste="pasteImages"
          /><button
            class="meng-button primary send-button"
            type="button"
            :aria-label="t('meng.send')"
            :disabled="inputDisabled || uploading || (!draft.trim()&&!attachments.length)"
            @click="send"
          >
            <IconSend />
          </button>
        </div>
        <small
          v-if="draft.length>900"
          class="input-count"
        >{{ t('meng.limit',{count:draft.length,max:Companion.MAX_LENGTH}) }}</small>
      </div>
    </main>
    <aside
      class="status-sidebar"
      :class="{closed:sideClosed}"
      :aria-hidden="sideClosed"
      :inert="sideClosed"
      :aria-label="t('meng.statusFiles')"
    >
      <div class="status-inner">
        <div class="status-nav">
          <button
            class="meng-button secondary"
            type="button"
            :aria-label="t('meng.closeSide')"
            @click="sideClosed=true"
          >
            <IconLayoutSidebarRightCollapse />
          </button><button
            class="meng-button secondary"
            type="button"
            @click="goHome"
          >
            <IconChevronLeft />{{ t('meng.back') }}
          </button>
        </div>
        <section class="status-face">
          <div>
            <span class="face-frame"><img
              :src="avatar(code)"
              :alt="character.name"
              width="128"
              height="128"
            ></span><span class="stage-stamp">{{ character.stageName }}</span>
          </div><p><span><IconCalendar />{{ t('meng.days',{count:character.acquaintanceDays}) }}</span><span><IconMessages />{{ t('meng.chatDays',{count:character.chatDays}) }}</span></p>
        </section>
        <section
          v-if="usage.usage"
          class="sidebar-panel"
        >
          <h2><IconClock />{{ t('meng.today') }}</h2><div class="usage-chips">
            <span>{{ t('meng.messages',{count:usage.usage.messages}) }}</span><span class="violet">{{ t('meng.documents',{count:usage.usage.documents}) }}</span><span class="pink">{{ t('meng.images',{count:usage.usage.images}) }}</span>
          </div><p class="chat-duration">
            <IconClock />{{ t('meng.minutes',{count:Math.floor(usage.usage.chatSeconds/60)}) }}
          </p>
        </section>
        <MengState
          v-if="sideLoading && !recentWorks.length && !memories.length"
          loading
          compact
        />
        <MengState
          v-else-if="sideError"
          :error="sideError"
          compact
          @retry="loadSide()"
        />
        <template v-else>
          <section class="sidebar-panel">
            <header>
              <h2><IconFolder />{{ t('meng.shared') }}</h2><button
                class="text-button"
                type="button"
                @click="openDialog('works')"
              >
                {{ t('meng.allWorks') }}
              </button>
            </header><MengState
              v-if="!recentWorks.length"
              kind="works"
              empty
              compact
            /><button
              v-for="w in recentWorks"
              :key="w.id"
              class="sidebar-work"
              type="button"
              @click="openDialog('works',w.id)"
            >
              <component :is="w.kind==='DOCUMENT'?IconFileText:IconPhoto" /><span><strong>{{ w.title }}</strong><small>{{ t('meng.meta',{kind:t(w.kind==='DOCUMENT'?'meng.document':'meng.image'),when:date(w.createTime)}) }}</small></span>
            </button>
          </section>
          <section class="sidebar-panel">
            <header>
              <h2><IconHeart />{{ t('meng.memory') }}</h2><button
                class="text-button"
                type="button"
                @click="openDialog('memory')"
              >
                {{ t('meng.allMemory',{count:memories.length}) }}
              </button>
            </header><MengState
              v-if="!memories.length"
              kind="memory"
              empty
              compact
            /><ul
              v-else
              class="sidebar-memories"
            >
              <li
                v-for="m in memories.slice(0,3)"
                :key="m.id"
              >
                <IconSparkles />{{ m.content }}
              </li>
            </ul>
          </section>
        </template>
      </div>
    </aside>
  </section>
</template>
<script src="./MengChat.js"></script>
<style lang="scss" scoped src="./MengChat.scss"></style>
