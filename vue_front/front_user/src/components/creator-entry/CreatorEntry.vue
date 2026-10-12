<template>
  <article
    class="creator-entry"
    :class="{published:!draft}"
    :tabindex="draft?undefined:0"
    @click="open"
    @keydown.enter.self="$emit('open',entry.id)"
  >
    <div class="entry-main">
      <div
        v-if="!draft"
        class="entry-cover"
      >
        <el-image
          v-if="entry.cover"
          :src="contentImage(entry.cover.thumbUrl)"
          :alt="t('creator.cover')"
          fit="cover"
          lazy
        />
        <IconPhoto
          v-else
          :aria-label="t('creator.cover')"
        />
      </div>
      <div class="entry-copy">
        <header class="entry-heading">
          <h3 :class="{placeholder:draft&&!entry.title}">
            {{ entry.title||t('creator.untitled') }}
          </h3>
          <span
            :style="{ maskImage: 'url('+JSON.stringify(communitySparkle)+')' }"
            class="creator-star entry-sparkle"
            aria-hidden="true"
          />
          <time :datetime="draft?entry.updateTime:entry.createTime"><IconClock :size="15" />{{ draft?t('creator.saved',{time:relativeTime(entry.updateTime)}):relativeTime(entry.createTime) }}</time>
        </header>
        <p :class="{placeholder:!entry.summary}">
          {{ entry.summary||t('creator.noBody') }}
        </p>
      </div>
    </div>
    <div
      v-if="draft&&entry.images.length"
      class="draft-images"
    >
      <div
        v-for="(image,index) in entry.images"
        :key="image.id"
      >
        <el-image
          :src="contentImage(image.thumbUrl)"
          :alt="t('home.image')"
          fit="cover"
          lazy
        />
        <span v-if="index===4&&entry.imageCount>5">{{ t('home.extraImages',{count:entry.imageCount-5}) }}</span>
      </div>
    </div>
    <footer class="entry-actions">
      <div
        v-if="!draft"
        class="entry-counts"
      >
        <span :aria-label="t('creator.views')"><IconEye :size="20" />{{ entry.viewCount }}</span>
        <span :aria-label="t('creator.likes')"><IconHeart :size="20" />{{ entry.likeCount }}</span>
        <span :aria-label="t('creator.comments')"><IconMessage :size="20" />{{ entry.commentCount }}</span>
        <span :aria-label="t('creator.favorites')"><IconStar :size="20" />{{ entry.favoriteCount }}</span>
      </div>
      <div class="entry-tools">
        <router-link :to="draft?{name:RouteName.POST_NEW,query:{draft:entry.id}}:{name:RouteName.POST_EDIT,params:{id:entry.id}}">
          <IconPencil :size="17" />{{ t(draft?'creator.continue':'creator.edit') }}
        </router-link>
        <button
          type="button"
          class="delete-entry"
          :disabled="pending"
          @click="$emit('delete',entry.id)"
        >
          <IconTrash :size="17" />{{ t('creator.delete') }}
        </button>
      </div>
      <span
        v-if="entry.category"
        class="entry-category"
        :class="'category-'+entry.category.toLowerCase()"
      >{{ t('home.categories.'+entry.category) }}</span>
    </footer>
  </article>
</template>
<script src="./CreatorEntry.js"></script>
<style lang="scss" scoped src="./CreatorEntry.scss"></style>
