<template>
  <CommunityLayout class="detail-layout">
    <main class="post-detail">
      <picture
        class="detail-background"
        aria-hidden="true"
      >
        <source
          media="(max-width: 760px)"
          :srcset="detailMobileArtwork"
        >
        <img
          :src="detailArtwork"
          :srcset="detailArtworkSmall+' 1600w, '+detailArtwork+' 2560w'"
          sizes="100vw"
          alt=""
          fetchpriority="high"
          decoding="async"
        >
      </picture>
      <PageState
        v-if="loading"
        class="detail-state"
        state="loading"
      />
      <div
        v-else-if="error"
        class="detail-state"
      >
        <PageState
          :state="error.status===404?'empty':'error'"
          :art="error.status===404?'postMissing':''"
          :error="error"
          size="page"
          :text="error.status===404?t('detail.missing'):''"
          @retry="load"
        /><router-link :to="homeLink">
          {{ t(homeLink.name===RouteName.CREATOR?'creator.back':homeLink.name===RouteName.SEARCH?'search.back':'detail.back') }}
        </router-link>
      </div>
      <template v-else-if="post">
        <article class="detail-card">
          <div
            class="detail-decor"
            aria-hidden="true"
          >
            <span class="detail-tape" /><span class="detail-sticker sticker-top">{{ t('detail.sticker.top') }}</span><span class="detail-sticker sticker-left">{{ t('detail.sticker.left') }}</span><span class="detail-sticker sticker-right">{{ t('detail.sticker.right') }}</span><span
              class="detail-sparkle"
              :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
            /><img
              class="detail-bubble"
              :src="detailBubble"
              alt=""
            ><img
              class="detail-flower"
              :src="detailFlower"
              alt=""
            >
          </div>
          <div
            class="detail-scroll-content"
            tabindex="0"
            :aria-label="t('editor.detailTitle')"
          >
            <header class="detail-author">
              <router-link
                class="detail-back"
                :to="homeLink"
                :aria-label="t(homeLink.name===RouteName.CREATOR?'creator.back':homeLink.name===RouteName.SEARCH?'search.back':'detail.back')"
              >
                <el-icon><ArrowLeft /></el-icon>
              </router-link>
              <UserContact
                :author="post.author"
                :size="56"
              />
              <strong>{{ post.author.nickname||t('home.authorFallback') }}</strong><span class="author-level">{{ t('home.level',{level:post.author.level}) }}</span><time :datetime="post.createTime">{{ relativeTime(post.createTime) }}</time><span
                v-if="post.ipRegion"
                class="detail-region"
              ><el-icon><Location /></el-icon>{{ post.ipRegion }}</span>
            </header>
            <h1>
              <span>{{ post.title }}</span><span
                class="title-sparkle"
                :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
              />
            </h1>
            <p
              v-if="post.reviewState==='IN_REVIEW'"
              class="detail-review-state"
              role="status"
            >
              {{ t('ai.review') }}
            </p>
            <MarkdownBody :content="post.content" />
            <PostImages :images="post.images" />
            <div
              v-if="post.tags.length"
              class="detail-tags"
            >
              <span
                v-for="tag in post.tags"
                :key="tag"
              >{{ t('detail.tag',{name:tag}) }}</span>
            </div>
            <footer class="detail-actions">
              <span
                class="view-count"
                :aria-label="t('home.views')"
              ><el-icon><ViewIcon /></el-icon>{{ post.viewCount }}</span>
              <button
                type="button"
                :class="{liked:post.liked}"
                :aria-label="t('home.like')"
                :aria-pressed="post.liked"
                :disabled="pending.like"
                @click="toggle('like')"
              >
                <el-icon><IconHeart /></el-icon>{{ post.likeCount }}
              </button>
              <button
                type="button"
                :aria-label="t('home.comment')"
                @click="scrollComments"
              >
                <el-icon><ChatDotRound /></el-icon>{{ post.commentCount }}
              </button>
              <button
                type="button"
                :class="{favorited:post.favorited}"
                :aria-label="t(post.favorited?'home.unfavorite':'home.favorite')"
                :aria-pressed="post.favorited"
                :disabled="pending.favorite"
                @click="toggle('favorite')"
              >
                <el-icon><IconStar /></el-icon>{{ t('home.favorite') }}<span>{{ post.favoriteCount }}</span>
              </button>
              <div class="detail-moderation">
                <template v-if="own">
                  <router-link :to="{name:RouteName.POST_EDIT,params:{id:post.id},query:homeLink.query}">
                    <el-icon><EditPen /></el-icon>{{ t('detail.edit') }}
                  </router-link><button
                    type="button"
                    class="delete-action"
                    :disabled="pending.delete"
                    @click="remove"
                  >
                    <el-icon><Delete /></el-icon>{{ t('detail.delete') }}
                  </button>
                </template><button
                  v-else
                  type="button"
                  @click="reportTarget={targetType:ReportTarget.POST,targetId:post.id}"
                >
                  <el-icon><IconFlag /></el-icon>{{ t('detail.report') }}
                </button><span :class="['detail-category','category-'+post.category.toLowerCase()]">{{ t('home.categories.'+post.category) }}</span>
              </div>
            </footer>
            <div class="comments-card">
              <PostComments
                id="post-comments"
                :key="post.id"
                :post-id="post.id"
                :initial-page="initialComments"
                :count="post.commentCount"
                :can-pin="own"
                @count="post.commentCount=$event"
                @report="reportTarget=$event"
              /><span
                class="detail-sticker sticker-bottom"
                aria-hidden="true"
              >{{ t('detail.sticker.bottom') }}</span>
            </div>
          </div>
        </article>
      </template>
      <RelatedPosts
        v-if="post"
        :post-id="post.id"
      />
      <ReportDialog
        :target="reportTarget"
        @close="reportTarget=null"
      />
    </main>
  </CommunityLayout>
</template>
<script src="./PostDetailView.js"></script>
<style lang="scss" scoped src="./PostDetailView.scss"></style>
