<template>
  <main
    class="messages-view"
    :data-connection="im.connection"
  >
    <div
      class="messages-background"
      aria-hidden="true"
    >
      <PixelBlast ref="pixel" />
    </div>
    <div
      class="messages-card"
      data-pixel-exclude
      @scroll.capture.passive="pausePixels"
    >
      <span
        class="messages-tape"
        aria-hidden="true"
      /><span
        class="messages-sticker"
        aria-hidden="true"
      >{{ t('im.sticker') }}</span>
      <span
        class="messages-sparkle"
        aria-hidden="true"
        :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
      />
      <aside class="messages-sidebar">
        <header class="messages-title">
          <span class="messages-title-icon"><img
            class="message-art-icon"
            :src="navMessageIcon"
            width="26"
            height="26"
            alt=""
            aria-hidden="true"
          ></span><h1>{{ t('im.title') }}</h1><router-link
            :to="{name:RouteName.HOME}"
            :aria-label="t('im.back')"
          >
            <img
              class="message-art-icon"
              :src="messageBackIcon"
              width="24"
              height="24"
              alt=""
              aria-hidden="true"
            >
          </router-link>
        </header>
        <nav
          class="message-tabs"
          :aria-label="t('im.title')"
          role="tablist"
        >
          <button
            v-for="value in MessageTabs"
            :id="'message-tab-'+value"
            :key="value"
            type="button"
            role="tab"
            :aria-selected="tab===value"
            :tabindex="tab===value?0:-1"
            :aria-controls="'message-panel'"
            :class="[value,{selected:tab===value}]"
            @click="switchTab(value)"
            @keydown="tabKey($event,value)"
          >
            <img
              class="tab-icon message-art-icon"
              :src="tabIcons[value]"
              width="30"
              height="30"
              alt=""
              aria-hidden="true"
            ><span class="tab-label">{{ t('im.'+value) }}</span><span
              v-if="im.unread[value]"
              class="unread-badge"
            >{{ im.unread[value]>99?t('im.badgeMax'):im.unread[value] }}</span>
          </button>
        </nav>
        <div class="sidebar-list">
          <Transition
            name="sidebar-swap"
            mode="out-in"
          >
            <div
              :key="tab"
              class="sidebar-pane"
            >
              <template v-if="tab===MessageTab.CHAT">
                <p
                  v-if="im.conversationsLoading&&!im.conversations.length"
                  class="sidebar-state"
                  role="status"
                >
                  {{ t('im.loading') }}
                </p>
                <div
                  v-else-if="im.conversationsError"
                  class="sidebar-state"
                  role="alert"
                >
                  <p>{{ im.conversationsError }}</p><button
                    type="button"
                    @click="im.loadConversations()"
                  >
                    {{ t('im.retry') }}
                  </button>
                </div>
                <div
                  v-else-if="!im.conversations.length"
                  class="sidebar-state"
                >
                  <p>{{ t('im.noConversations') }}</p><small>{{ t('im.noConversationsHint') }}</small>
                </div>
                <div
                  v-for="(item,index) in im.conversations"
                  :key="item.id"
                  class="conversation-row"
                  :class="['tone-'+index%5,{selected:im.current?.peer.id===item.peer.id}]"
                  :style="{'--i':index}"
                >
                  <button
                    class="conversation-select"
                    type="button"
                    :aria-pressed="im.current?.peer.id===item.peer.id"
                    @click="selectPeer(item.peer.id)"
                  >
                    <MessageAvatar :user="item.peer" /><span class="conversation-info"><strong>{{ item.peer.nickname||t('home.authorFallback') }}</strong><small>{{ previewText(item.preview) }}</small></span><span class="conversation-tail"><time>{{ item.lastMessageTime?relativeTime(item.lastMessageTime):'' }}</time><span
                      v-if="item.unreadCount"
                      class="unread-badge"
                    >{{ item.unreadCount>99?t('im.badgeMax'):item.unreadCount }}</span></span>
                  </button>
                </div>
                <button
                  v-if="im.conversations.length<im.conversationTotal"
                  class="sidebar-more"
                  type="button"
                  :disabled="im.conversationsLoading"
                  @click="im.loadConversations(true)"
                >
                  {{ t(im.conversationsLoading?'im.loading':'im.more') }}
                </button>
              </template>
              <template v-else>
                <p
                  v-if="groupsLoading&&!groups.length"
                  class="sidebar-state"
                  role="status"
                >
                  {{ t('im.loading') }}
                </p>
                <div
                  v-if="groupsError"
                  class="sidebar-state"
                  role="alert"
                >
                  <p>{{ groupsError }}</p><button
                    type="button"
                    @click="loadGroups"
                  >
                    {{ t('im.retry') }}
                  </button>
                </div>
                <button
                  v-for="key in availableGroups"
                  :key="key"
                  class="notification-group"
                  :class="[tab,key.toLowerCase(),{selected:group===key}]"
                  type="button"
                  :aria-pressed="group===key"
                  @click="selectGroup(key)"
                >
                  <span class="group-icon"><img
                    v-if="key==='GROWTH'"
                    class="message-art-icon"
                    :src="navGrowthIcon"
                    width="24"
                    height="24"
                    alt=""
                    aria-hidden="true"
                  ><img
                    v-else-if="key==='REPORT'||key==='APPROVAL'"
                    class="message-art-icon"
                    :src="messageShieldIcon"
                    width="24"
                    height="24"
                    alt=""
                    aria-hidden="true"
                  ><img
                    v-else-if="tab===MessageTab.LIKE"
                    class="message-art-icon"
                    :src="messageHeartIcon"
                    width="24"
                    height="24"
                    alt=""
                    aria-hidden="true"
                  ><img
                    v-else
                    class="message-art-icon"
                    :src="navMessageIcon"
                    width="24"
                    height="24"
                    alt=""
                    aria-hidden="true"
                  ></span><span class="group-info"><strong>{{ t('im.'+key) }}</strong><small><span
                    v-if="nickname(groupRow(key))"
                    class="summary-name"
                  >{{ nickname(groupRow(key)) }}</span>{{ summary(groupRow(key)) }}</small></span><span class="group-tail"><time v-if="groupRow(key)?.latest">{{ relativeTime(groupRow(key).latest.notice.createTime) }}</time><span
                    v-if="groupRow(key)?.unreadCount"
                    class="unread-badge"
                  >{{ groupRow(key).unreadCount>99?t('im.badgeMax'):groupRow(key).unreadCount }}</span></span>
                </button>
              </template>
            </div>
          </Transition>
        </div>
        <footer class="messages-profile">
          <el-avatar
            :src="user.current?.avatar||userAvatar"
            :size="52"
          /><strong>{{ user.current?.nickname||user.current?.username }}</strong><el-popover
            v-model:visible="presenceOpen"
            placement="top-end"
            :width="170"
            trigger="click"
          >
            <template #reference>
              <button
                class="presence-toggle"
                type="button"
                :class="{hidden:im.hidden}"
                :disabled="changingPresence"
                :aria-expanded="presenceOpen"
                :aria-label="t('im.presence')"
              >
                <i />{{ t(im.hidden?'im.hidden':'im.online') }}<img
                  class="message-art-icon"
                  :src="messageChevronIcon"
                  width="14"
                  height="14"
                  alt=""
                  aria-hidden="true"
                >
              </button>
            </template><div class="presence-options">
              <button
                type="button"
                :class="{selected:!im.hidden}"
                :disabled="changingPresence"
                @click="setPresence(false)"
              >
                {{ t('im.automatic') }}
              </button><button
                type="button"
                :class="{selected:im.hidden}"
                :disabled="changingPresence"
                @click="setPresence(true)"
              >
                {{ t('im.hidden') }}
              </button>
            </div>
          </el-popover>
        </footer>
      </aside>
      <section
        id="message-panel"
        class="messages-main"
        role="tabpanel"
        :aria-labelledby="'message-tab-'+tab"
      >
        <img
          class="messages-mascot"
          :src="editorCirnoMascot"
          alt=""
          aria-hidden="true"
          draggable="false"
        >
        <Transition
          name="message-pane"
          mode="out-in"
        >
          <div
            :key="tab"
            class="message-panel-content"
          >
            <Transition
              v-if="tab===MessageTab.CHAT"
              name="chat-swap"
              mode="out-in"
            >
              <p
                v-if="im.opening"
                key="opening"
                class="messages-state"
                role="status"
              >
                <span
                  class="state-dots"
                  aria-hidden="true"
                ><i /><i /><i /></span>{{ t('im.loading') }}
              </p>
              <div
                v-else-if="im.currentError"
                key="error"
                class="messages-state"
                role="alert"
              >
                <p>{{ im.currentError }}</p><button
                  type="button"
                  @click="im.open(Number(route.query.with))"
                >
                  {{ t('im.retry') }}
                </button>
              </div>
              <MessageChat
                v-else-if="im.current"
                :key="im.current.peer.id"
              />
              <div
                v-else
                key="empty"
                class="messages-state choose"
              >
                <span class="choose-badge"><img
                  class="message-art-icon"
                  :src="navMessageIcon"
                  width="64"
                  height="64"
                  alt=""
                  aria-hidden="true"
                ></span><p>{{ t('im.choose') }}</p><small>{{ t('im.sticker') }}</small>
              </div>
            </Transition>
            <MessageNotices
              v-else
              :key="tab+'-'+group"
              :tab="tab"
              :group="group"
              :records="records"
              :total="total"
              :page="page"
              :total-pages="totalPages"
              :loading="loading"
              :error="error"
              @page="turn"
              @retry="loadNotices"
            />
          </div>
        </Transition>
      </section>
    </div>
    <p
      v-if="im.connection!=='connected'"
      class="connection-state"
      role="status"
      :class="{connected:im.connection==='connected'}"
    >
      {{ t('im.'+(im.connection==='offline'?'connecting':im.connection)) }}
    </p>
  </main>
</template>
<script src="./MessagesView.js"></script>
<style lang="scss" scoped src="./MessagesView.scss"></style>
