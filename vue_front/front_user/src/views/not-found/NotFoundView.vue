<template>
  <main class="missing-layout">
    <ParticleBackground />
    <section class="missing-card">
      <CardEdge />
      <BorderGlow />
      <img
        class="missing-art"
        :src="statePage404Art"
        alt=""
      >
      <h1>{{ t('common.notFoundTitle') }}</h1>
      <p>{{ t('common.notFound') }}</p>
      <router-link
        class="missing-action"
        :to="{ name: RouteName.HOME }"
      >
        {{ t('common.backHome') }}
      </router-link>
    </section>
  </main>
</template>
<script src="./NotFoundView.js"></script>
<style lang="scss" scoped src="./NotFoundView.scss"></style>
