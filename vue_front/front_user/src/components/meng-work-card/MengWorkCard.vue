<template>
  <article class="chat-work">
    <span class="new-work">{{ t('meng.newWork') }}</span>
    <header>
      <span class="work-icon"><IconPhoto v-if="resource.state.data?.kind === 'IMAGE'" /><IconFileText v-else /></span>
      <div>
        <strong>{{ resource.state.data?.title || title }}</strong><small v-if="resource.state.data">{{
          resource.state.data.kind === 'DOCUMENT'
            ? t('meng.documentLength', { count: Array.from(resource.state.data.body || '').length })
            : resource.state.data.kind === 'TOY'
              ? t('meng.toy')
              : t('meng.image')
        }}</small>
      </div>
    </header>
    <p
      v-if="resource.state.loading"
      role="status"
    >
      {{ t('meng.loading') }}
    </p>
    <button
      v-else-if="resource.state.error"
      type="button"
      class="text-button"
      @click="resource.load"
    >
      {{ t('meng.retry') }}
    </button>
    <template v-else-if="resource.state.data">
      <p
        v-if="resource.state.data.kind === 'DOCUMENT'"
        class="work-excerpt"
      >
        {{ resource.state.data.body?.slice(0, 100) }}
      </p>
      <MengToy
        v-else-if="resource.state.data.kind === 'TOY'"
        :body="resource.state.data.body || ''"
        :title="resource.state.data.title"
      />
      <img
        v-else
        class="work-image"
        :src="fileUrl(workId)"
        :alt="title"
        loading="lazy"
      >
      <div class="work-actions">
        <button
          class="meng-button secondary"
          type="button"
          @click="$emit('open', workId)"
        >
          {{ t('meng.open') }}
        </button>
      </div>
    </template>
  </article>
</template>
<script src="./MengWorkCard.js"></script>
<style lang="scss" scoped src="./MengWorkCard.scss"></style>
