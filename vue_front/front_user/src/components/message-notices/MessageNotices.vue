<template>
  <section
    class="message-notices"
    :class="tab"
  >
    <header class="notices-header">
      <h2>{{ t(tab===MessageTab.SYSTEM?'im.'+group:tab===MessageTab.COMMENT?'im.commentTitle':'im.likeTitle') }}</h2><span>{{ t('im.total',{count:total}) }}</span>
    </header>
    <div
      class="notices-list"
      :aria-busy="loading"
    >
      <p
        v-if="loading&&!records.length"
        class="notices-state"
        role="status"
      >
        {{ t('im.loading') }}
      </p>
      <div
        v-else-if="error"
        class="notices-state"
        role="alert"
      >
        <p>{{ error }}</p><button
          type="button"
          @click="$emit('retry')"
        >
          {{ t('im.retry') }}
        </button>
      </div>
      <p
        v-else-if="!records.length"
        class="notices-state"
      >
        {{ t('im.noNotices') }}
      </p>
      <TransitionGroup name="notice-rise">
        <button
          v-for="item in records"
          :key="item.notice.id"
          type="button"
          class="notice-card"
          :class="[item.notice.type.toLowerCase(),{new:!item.notice.readState}]"
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
          <i
            v-else
            class="notice-dot"
            aria-hidden="true"
          />
          <div class="notice-main">
            <div class="notice-heading">
              <span
                v-if="item.actors.length"
                class="actor-tag"
              >{{ item.notice.actorCount>2?t('im.actors',{names:names(item),count:item.notice.actorCount}):names(item) }}</span>
              <strong>{{ t('im.action.'+item.notice.type) }}</strong>
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
