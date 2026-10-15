<template>
  <section
    class="message-notices"
    :class="tab"
  >
    <header class="notices-header">
      <span
        class="header-badge"
        aria-hidden="true"
      ><img
        class="message-art-icon"
        :src="headerIcon"
        width="32"
        height="32"
        alt=""
      ></span>
      <h2>{{ t(tab===MessageTab.SYSTEM?'im.'+group:tab===MessageTab.COMMENT?'im.commentTitle':'im.likeTitle') }}</h2><span class="notices-total">{{ t('im.total',{count:total}) }}</span>
    </header>
    <div
      class="notices-list"
      :aria-busy="loading"
    >
      <Transition
        name="notice-swap"
        mode="out-in"
      >
        <div
          :key="view"
          class="notices-body"
        >
          <PageState
            v-if="view==='loading'"
            state="loading"
            :text="t('im.loading')"
          />
          <PageState
            v-else-if="view==='error'"
            state="error"
            @retry="$emit('retry')"
          />
          <PageState
            v-else-if="view==='empty'"
            state="empty"
            art="notification"
            size="tall"
            :text="t('im.noNotices')"
          />
          <TransitionGroup
            v-else
            name="notice-rise"
          >
            <button
              v-for="(item,index) in records"
              :key="item.notice.id"
              type="button"
              class="notice-card"
              :class="[item.notice.type.toLowerCase(),{new:!item.notice.readState}]"
              :style="{'--i':index}"
              @click="open(item)"
            >
              <div
                v-if="item.actors.length"
                class="notice-avatars"
              >
                <MessageAvatar
                  v-for="actor in item.actors"
                  :key="actor.id"
                  :size="42"
                  :user="actor"
                />
              </div>
              <span
                v-else
                class="notice-badge"
                aria-hidden="true"
              ><img
                class="message-art-icon"
                :src="typeIcon(item)"
                width="28"
                height="28"
                alt=""
              ></span>
              <div class="notice-main">
                <div class="notice-heading">
                  <span
                    v-if="item.actors.length"
                    class="actor-tag"
                  >{{ item.notice.actorCount>2?t('im.actors',{names:names(item),count:item.notice.actorCount}):names(item) }}</span>
                  <strong><img
                    v-if="item.actors.length"
                    class="message-art-icon notice-type"
                    :src="typeIcon(item)"
                    width="18"
                    height="18"
                    alt=""
                    aria-hidden="true"
                  >{{ t('im.action.'+item.notice.type) }}</strong>
                  <span
                    v-if="!item.notice.readState"
                    class="new-label"
                  >{{ t('im.new') }}</span>
                  <span
                    v-if="item.notice.postTitle"
                    class="notice-post"
                  >{{ t('im.postTitle',{title:item.notice.postTitle}) }}</span>
                </div>
                <CommentBody
                  v-if="item.notice.excerpt"
                  :content="item.notice.excerpt"
                />
                <p
                  v-if="item.notice.originalExcerpt"
                  class="original-comment"
                >
                  {{ t('im.original',{text:item.notice.originalExcerpt}) }}
                </p>
                <p
                  v-if="item.notice.type===NoticeType.LEVEL_CHANGED"
                  class="notice-level"
                >
                  <strong>{{ t('im.toLevel',{level:item.notice.level,name:levelName(item.notice.level)}) }}</strong>
                </p>
                <p
                  v-if="item.notice.type===NoticeType.USER_APPROVED"
                  class="notice-welcome"
                >
                  {{ t('im.welcome') }}
                </p>
              </div>
              <time :datetime="item.notice.createTime">{{ relativeTime(item.notice.createTime) }}</time>
            </button>
          </TransitionGroup>
        </div>
      </Transition>
    </div>
    <footer class="notices-footer">
      <FeedPagination
        :page="page"
        :total-pages="totalPages"
        compact
        @page="$emit('page',$event)"
      />
    </footer>
  </section>
</template>
<script src="./MessageNotices.js"></script>
<style lang="scss" scoped src="./MessageNotices.scss"></style>
