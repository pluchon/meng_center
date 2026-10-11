<template>
  <section
    ref="section"
    class="post-comments"
  >
    <header class="comments-heading">
      <h2><span class="comments-title">{{ t('detail.comments') }}</span><span class="comments-count">{{ count }}</span><span class="comments-cheer">{{ t('detail.commentCheer') }}</span></h2><button
        type="button"
        :aria-expanded="open"
        @click="toggleComposer"
      >
        <el-icon><EditPen /></el-icon>{{ t(open?'detail.collapse':'detail.write') }}
      </button>
    </header>
    <CollapsePanel :open="open">
      <CommentComposer
        v-if="composerMounted"
        ref="composer"
        :key="postId"
        :post-id="postId"
        :target="target"
        @sent="sent"
        @clear-target="target=null"
      />
    </CollapsePanel>
    <PageState
      v-if="loading"
      state="loading"
    /><PageState
      v-else-if="error"
      state="error"
      @retry="load"
    /><p
      v-else-if="!records.length"
      class="comments-empty"
    >
      {{ t('detail.empty') }}
    </p>
    <div
      v-else
      class="comment-list"
    >
      <div
        v-for="comment in records"
        :key="comment.id"
        class="comment-thread"
      >
        <CommentItem
          :comment="comment"
          :busy="pending[comment.id]||false"
          :expanded="replies[comment.id]?.open||false"
          @like="like"
          @reply="reply"
          @delete="remove"
          @report="report"
          @expand="expand"
        />
        <CollapsePanel :open="replies[comment.id]?.open||false">
          <div
            v-if="replies[comment.id]"
            class="replies"
          >
            <CommentItem
              v-for="item in replies[comment.id].records"
              :key="item.id"
              compact
              :comment="item"
              :busy="pending[item.id]||false"
              @like="like"
              @reply="reply"
              @delete="remove"
              @report="report"
            />
            <PageState
              v-if="replies[comment.id].loading"
              state="loading"
            /><PageState
              v-else-if="replies[comment.id].error"
              state="error"
              @retry="loadReplies(comment,replies[comment.id].page===0)"
            />
            <button
              v-else-if="replies[comment.id].records.length<replies[comment.id].total"
              type="button"
              class="more-replies"
              @click="loadReplies(comment)"
            >
              {{ t('detail.moreReplies',{count:replies[comment.id].total-replies[comment.id].records.length}) }}
            </button>
          </div>
        </CollapsePanel>
      </div>
    </div>
    <nav
      v-if="pages.length>1"
      class="comments-pagination"
      :aria-label="t('detail.comments')"
    >
      <template
        v-for="(value,index) in pages"
        :key="index"
      >
        <button
          v-if="value"
          type="button"
          :class="{selected:value===page}"
          :aria-current="value===page?'page':undefined"
          :aria-label="t('home.page',{page:value})"
          @click="page=value"
        >
          {{ value }}
        </button><span v-else>{{ t('home.ellipsis') }}</span>
      </template>
    </nav>
  </section>
</template>
<script src="./PostComments.js"></script>
<style lang="scss" scoped src="./PostComments.scss"></style>
