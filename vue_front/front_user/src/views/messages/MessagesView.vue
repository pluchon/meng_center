<template>
  <main
    class="messages-view"
    :data-connection="im.connection"
  >
    <div
      class="messages-background"
      aria-hidden="true"
    >
      <PixelBlast />
    </div>
    <div
      class="messages-card"
      data-pixel-exclude
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
          <span class="messages-title-icon"><IconMessageCircle :size="26" /></span><h1>{{ t('im.title') }}</h1><router-link
            :to="{name:RouteName.HOME}"
            :aria-label="t('im.back')"
          >
            <IconArrowBackUp :size="24" />
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
            {{ t('im.'+value) }}<span
              v-if="im.unread[value]"
              class="unread-badge"
            >{{ im.unread[value]>99?t('im.badgeMax'):im.unread[value] }}</span>
          </button>
        </nav>
        <div class="sidebar-list">
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
              v-for="item in im.conversations"
              :key="item.id"
              class="conversation-row"
              :class="{selected:im.current?.peer.id===item.peer.id}"
            >
              <button
                class="conversation-select"
                type="button"
                :aria-pressed="im.current?.peer.id===item.peer.id"
                @click="selectPeer(item.peer.id)"
              >
                <MessageAvatar :user="item.peer" /><span class="conversation-info"><strong>{{ item.peer.nickname||t('home.authorFallback') }}</strong><small>{{ item.preview===t('im.imagePreview')?t('im.imagePreview'):item.preview }}</small></span><span class="conversation-tail"><time>{{ item.lastMessageTime?relativeTime(item.lastMessageTime):'' }}</time><span
                  v-if="item.unreadCount"
                  class="unread-badge"
                >{{ item.unreadCount>99?t('im.badgeMax'):item.unreadCount }}</span></span>
              </button>
              <el-dropdown
                trigger="click"
                placement="bottom-end"
                @command="action($event,item)"
              >
                <button
                  class="conversation-menu"
                  type="button"
                  :aria-label="t('im.menu')"
                >
                  <IconDots :size="18" />
                </button><template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="block">
                      {{ t(item.blocked?'im.unblock':'im.block') }}
                    </el-dropdown-item><el-dropdown-item command="delete">
                      {{ t('im.remove') }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
              <span class="group-icon"><IconPlant2
                v-if="key==='GROWTH'"
                :size="24"
              /><IconShieldCheck
                v-else-if="key==='REPORT'||key==='APPROVAL'"
                :size="24"
              /><IconHeart
                v-else-if="tab===MessageTab.LIKE"
                :size="24"
              /><IconMessages
                v-else
                :size="24"
              /></span><span class="group-info"><strong>{{ t('im.'+key) }}</strong><small><span
                v-if="nickname(groupRow(key))"
                class="summary-name"
              >{{ nickname(groupRow(key)) }}</span>{{ summary(groupRow(key)) }}</small></span><span class="group-tail"><time v-if="groupRow(key)?.latest">{{ relativeTime(groupRow(key).latest.notice.createTime) }}</time><span
                v-if="groupRow(key)?.unreadCount"
                class="unread-badge"
              >{{ groupRow(key).unreadCount>99?t('im.badgeMax'):groupRow(key).unreadCount }}</span></span>
            </button>
          </template>
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
                <i />{{ t(im.hidden?'im.hidden':'im.online') }}<IconChevronUp :size="14" />
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
        <Transition
          name="message-pane"
          mode="out-in"
        >
          <div
            :key="tab"
            class="message-panel-content"
          >
            <template v-if="tab===MessageTab.CHAT">
              <p
                v-if="im.opening"
                class="messages-state"
                role="status"
              >
                {{ t('im.loading') }}
              </p>
              <div
                v-else-if="im.currentError"
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
                class="messages-state"
              >
                <IconMessageCircle :size="60" /><p>{{ t('im.choose') }}</p>
              </div>
            </template>
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
