<template>
  <main class="legal-layout">
    <ParticleBackground />
    <section class="legal-card">
      <BorderGlow />
      <img class="legal-artwork" :src="legalArtwork" alt="" aria-hidden="true" />
      <header class="legal-header">
        <router-link class="legal-brand" :to="{ name: returnRoute }">
          <img :src="brandIcon" :alt="t('common.brand')" /><span>{{ t('common.brand') }}</span>
        </router-link>
        <div class="legal-tabs" :class="{ 'privacy-selected': selected === LegalDocument.PRIVACY }">
          <span class="legal-tab-indicator" aria-hidden="true" />
          <button
            v-for="kind in [LegalDocument.TERMS, LegalDocument.PRIVACY]"
            :key="kind"
            type="button"
            :class="{ selected: selected === kind }"
            :aria-pressed="selected === kind"
            @click="selected = kind"
          >
            {{ t(kind === LegalDocument.TERMS ? 'auth.register.terms' : 'auth.register.privacy') }}
          </button>
        </div>
        <span class="legal-updated">{{ t('auth.legal.updated', { date: document.version }) }}</span>
      </header>
      <div class="legal-body">
        <nav :aria-label="t('auth.legal.contents')">
          <strong class="toc-title">{{ t('auth.legal.contents') }}</strong>
          <button
            v-for="(section, index) in document.sections"
            :key="section.id"
            type="button"
            :class="{ active: active === section.id }"
            @click="jump(section.id)"
          >
            <span class="section-number">{{ index + 1 }}</span
            >{{ section.title }}
          </button>
          <span class="reading-note">{{ t('auth.legal.takeTime') }}</span>
        </nav>
        <article ref="scrollArea" :key="selected" @scroll="updateActive">
          <h1>
            <span>{{ t(document.titleKey) }}</span>
          </h1>
          <p class="legal-meta">
            {{ t('auth.legal.metadata', { version: document.version }) }}
          </p>
          <aside class="legal-overview">
            <h2>{{ t('auth.legal.overview') }}</h2>
            <p>{{ document.sections[0].paragraphs[0] }}</p>
          </aside>
          <section v-for="(section, index) in document.sections" :id="section.id" :key="section.id">
            <h2>
              <span class="section-number">{{ index + 1 }}</span
              >{{ section.title }}
            </h2>
            <p v-for="paragraph in section.paragraphs" :key="paragraph">
              {{ paragraph }}
            </p>
          </section>
        </article>
      </div>
      <footer class="legal-footer">
        <div class="reading-progress">
          <el-progress :percentage="progress" :show-text="false" />
        </div>
        <router-link class="legal-finished" :to="{ name: returnRoute }">
          {{ t('auth.legal.finished') }}
        </router-link>
      </footer>
    </section>
  </main>
</template>
<script src="./AgreementView.js"></script>
<style lang="scss" scoped src="./AgreementView.scss"></style>
