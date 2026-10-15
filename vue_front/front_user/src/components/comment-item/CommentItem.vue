<template>
  <article
    :id="'comment-'+comment.id"
    :class="['comment-item',{compact}]"
  >
    <UserContact :author="comment.author" />
    <div class="comment-main">
      <header>
        <strong>{{ comment.author.nickname||t('home.authorFallback') }}</strong><span
          v-if="!compact"
          class="level"
        >{{ t('home.level',{level:comment.author.level}) }}</span><span
          v-if="comment.postAuthor"
          class="author-badge"
        >{{ t('detail.author') }}</span><span
          v-if="comment.pinned"
          class="pin-badge"
        >{{ t('detail.pinnedBadge') }}</span><time :datetime="comment.createTime">{{ relativeTime(comment.createTime) }}</time><span
          v-if="comment.ipRegion"
          class="region"
        ><el-icon><Location /></el-icon>{{ comment.ipRegion }}</span>
      </header>
      <div class="comment-content">
        <span
          v-if="comment.replyToUserId"
          class="reply-target"
        >{{ t('detail.replyTo',{name:comment.replyToNickname||t('home.authorFallback')}) }}</span><CommentBody
          :content="comment.content"
          :mentions="comment.mentions"
        />
      </div>
      <PostImages
        :images="comment.images"
        compact
      />
      <footer>
        <button
          type="button"
          :class="{liked:comment.liked}"
          :aria-label="t('home.like')"
          :aria-pressed="comment.liked"
          :disabled="busy"
          @click="$emit('like',comment)"
        >
          <el-icon><IconHeart /></el-icon>{{ comment.likeCount }}
        </button>
        <button
          type="button"
          class="reply-action"
          @click="$emit('reply',comment)"
        >
          {{ t('detail.reply') }}
        </button>
        <button
          v-if="!compact&&comment.replyCount"
          type="button"
          class="reply-action"
          :aria-expanded="expanded"
          @click="$emit('expand',comment)"
        >
          {{ t('detail.replies',{count:comment.replyCount}) }}<el-icon :class="{expanded}">
            <ArrowDown />
          </el-icon>
        </button>
        <button
          v-if="canPin&&!compact"
          type="button"
          class="pin-action"
          :class="{pinned:comment.pinned}"
          :disabled="busy"
          :aria-pressed="comment.pinned"
          @click="$emit('pin',comment)"
        >
          <el-icon><IconPin /></el-icon>{{ t(comment.pinned?'detail.unpin':'detail.pin') }}
        </button>
        <button
          v-if="own"
          type="button"
          class="moderate"
          :class="{danger:own}"
          :disabled="busy"
          @click="$emit('delete',comment)"
        >
          <el-icon><Delete /></el-icon>{{ t('detail.delete') }}
        </button>
        <button
          v-else
          type="button"
          class="moderate"
          @click="$emit('report',comment)"
        >
          <el-icon><IconFlag /></el-icon>{{ t('detail.report') }}
        </button>
      </footer>
    </div>
  </article>
</template>
<script src="./CommentItem.js"></script>
<style lang="scss" scoped src="./CommentItem.scss"></style>
