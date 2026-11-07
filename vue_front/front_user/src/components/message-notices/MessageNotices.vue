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
              <div class="notice-main">
                <p class="notice-heading">
                  <template v-if="item.actors.length">
                    <strong class="actor-name">{{ item.notice.actorCount>2?t('im.actors',{names:names(item),count:item.notice.actorCount}):names(item) }}</strong>
                    <template v-if="inlinePost(item)">
                      <span>{{ t('im.inPost') }}</span><span class="notice-post">
                        <el-icon><IconNotes /></el-icon><span>{{ item.notice.postTitle }}</span>
                      </span>
                    </template>
                    <span>{{ t((inlinePost(item)?'im.inlineAction.':'im.action.')+item.notice.type) }}</span>
                  </template>
                  <strong v-else>{{ t('im.action.'+item.notice.type) }}</strong>
                  <span
                    v-if="item.notice.type===NoticeType.LEVEL_CHANGED"
                    class="notice-level"
                  >{{ t('im.toLevel',{level:item.notice.level,name:levelName(item.notice.level)}) }}</span>
                  <span
                    v-if="!item.notice.readState"
                    class="new-label"
                  >{{ t('im.new') }}</span>
                  <time :datetime="item.notice.createTime">{{ relativeTime(item.notice.createTime) }}</time>
                </p>
                <p
                  v-if="item.notice.excerpt"
                  class="notice-excerpt"
                >
                  <span class="excerpt-text">{{ excerpt(item).text }}</span><span
                    v-if="excerpt(item).emotions.length"
                    class="excerpt-emotions"
                  ><img
                    v-for="(emotion,position) in excerpt(item).emotions.slice(0,EXCERPT_EMOTIONS)"
                    :key="position"
                    :src="emotion.image"
                    :alt="emotion.name"
                    loading="lazy"
                    decoding="async"
                  ><small v-if="excerpt(item).emotions.length>EXCERPT_EMOTIONS">{{ t('im.album',{count:excerpt(item).emotions.length-EXCERPT_EMOTIONS}) }}</small></span>
                </p>
                <p
                  v-if="item.notice.originalExcerpt"
                  class="original-comment"
                >
                  {{ t('im.original',{text:plain(item.notice.originalExcerpt)}) }}
                </p>
                <p
                  v-if="item.notice.postTitle&&!inlinePost(item)"
                  class="notice-second"
                >
                  <span class="notice-post">
                    <el-icon><IconNotes /></el-icon><span>{{ item.notice.postTitle }}</span>
                  </span>
                </p>
                <p
                  v-if="item.notice.type===NoticeType.USER_APPROVED"
                  class="notice-second notice-welcome"
                >
                  {{ t('im.welcome') }}
                </p>
              </div>
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
