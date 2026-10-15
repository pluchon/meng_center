<template>
  <section
    ref="section"
    class="post-comments"
  >
    <header class="comments-heading">
      <h2><span class="comments-badge"><span class="comments-title">{{ t('detail.comments') }}</span><span class="comments-count">{{ count }}</span></span><span class="comments-cheer">{{ t('detail.commentCheer') }}</span></h2><button
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
    /><PageState
      v-else-if="!records.length"
      state="empty"
      art="chat"
      size="large"
      :text="t('detail.empty')"
    />
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
    <FeedPagination
      v-if="totalPages>1"
      compact
      :page="page"
      :total-pages="totalPages"
      @page="value=>page=value"
    />
  </section>
</template>
<script src="./PostComments.js"></script>
<style lang="scss" scoped src="./PostComments.scss"></style>
