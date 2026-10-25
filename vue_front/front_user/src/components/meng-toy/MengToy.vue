<template>
  <section class="meng-toy">
    <div class="toy-controls">
      <button
        type="button"
        class="meng-button secondary"
        :disabled="!document"
        @click="run"
      >
        {{ t(running ? 'meng.rerun' : 'meng.runToy') }}
      </button>
      <button
        v-if="running"
        type="button"
        class="meng-button secondary"
        @click="fullscreen"
      >
        {{ t('meng.fullscreen') }}
      </button>
    </div>
    <iframe
      v-if="running && document"
      :key="revision"
      ref="frame"
      :title="title"
      sandbox="allow-scripts"
      :srcdoc="document"
      referrerpolicy="no-referrer"
    />
  </section>
</template>
<script src="./MengToy.js"></script>
<style lang="scss" scoped src="./MengToy.scss"></style>
