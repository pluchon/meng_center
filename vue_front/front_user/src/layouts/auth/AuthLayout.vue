<template>
  <main class="auth-layout">
    <section class="auth-card">
      <BorderGlow />
      <aside class="poster">
        <img
          v-if="!imageFailed"
          :src="poster"
          :alt="t('auth.poster.alt')"
          @error="imageFailed = true"
        />
        <div v-else class="poster-fallback">
          <el-icon :size="64">
            <PosterIcon />
          </el-icon>
          <p>{{ t('auth.poster.unavailable') }}</p>
        </div>
        <div class="poster-brand">
          <img :src="brandIcon" :alt="t('common.brand')" />
          {{ t('common.brand') }}
        </div>
        <span class="poster-chip chip-one">{{ t('auth.poster.chipOne') }}</span>
        <span class="poster-chip chip-two">{{ t('auth.poster.chipTwo') }}</span>
        <span class="poster-chip chip-three">{{ t('auth.poster.chipThree') }}</span>
      </aside>
      <section class="form-area" :class="{ 'login-area': !registration }">
        <slot />
      </section>
    </section>
  </main>
</template>
<script src="./AuthLayout.js"></script>
<style lang="scss" scoped src="./AuthLayout.scss"></style>
