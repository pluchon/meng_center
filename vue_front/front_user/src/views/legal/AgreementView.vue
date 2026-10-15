<template>
  <main class="legal-layout">
    <ParticleBackground />
    <section class="legal-card">
      <CardEdge />
      <BorderGlow />
      <img
        class="legal-artwork"
        :src="legalArtwork"
        alt=""
        aria-hidden="true"
      >
      <img
        class="legal-flower"
        :src="detailFlower"
        alt=""
        aria-hidden="true"
      >
      <span
        class="legal-sparkle"
        aria-hidden="true"
        :style="{ maskImage: 'url(' + JSON.stringify(communitySparkle) + ')' }"
      />
      <header class="legal-header">
        <router-link
          class="legal-brand"
          :to="{ name: returnRoute }"
        >
          <img
            :src="brandIcon"
            :alt="t('common.brand')"
          ><span>{{ t('common.brand') }}</span>
        </router-link>
        <div
          class="legal-tabs"
          :class="{ 'privacy-selected': selected === LegalDocument.PRIVACY }"
        >
          <span
            class="legal-tab-indicator"
            aria-hidden="true"
          />
          <button
            v-for="kind in [LegalDocument.TERMS, LegalDocument.PRIVACY]"
            :key="kind"
            type="button"
            :class="{ selected: selected === kind }"
            :aria-pressed="selected === kind"
            @click="selected = kind"
          >
            <img
              class="tab-icon"
              :src="kind === LegalDocument.TERMS ? legalTermsIcon : legalPrivacyIcon"
              alt=""
              aria-hidden="true"
            >{{ t(kind === LegalDocument.TERMS ? 'auth.register.terms' : 'auth.register.privacy') }}
          </button>
        </div>
        <span class="legal-updated">
          <img
            :src="growthClockIcon"
            alt=""
            aria-hidden="true"
          >{{ t('auth.legal.updated', { date: document.version }) }}
        </span>
      </header>
      <div class="legal-body">
        <nav
          ref="toc"
          :aria-label="t('auth.legal.contents')"
          @pointerover="tocSlider.onHover"
          @pointerleave="tocSlider.onLeave"
          @focusin="tocSlider.onHover"
          @focusout="tocSlider.onLeave"
        >
          <span
            class="toc-slider"
            :class="tocSlider.sliderClass.value"
            :style="tocSlider.sliderStyle.value"
            aria-hidden="true"
          />
          <strong class="toc-title">{{ t('auth.legal.contents') }}</strong>
          <button
            v-for="(section, index) in document.sections"
            :key="section.id"
            type="button"
            :class="{ active: active === section.id }"
            @click="jump(section.id)"
          >
            <span class="section-number">{{ index + 1 }}</span>{{ section.title }}
          </button>
          <span class="reading-note">{{ t('auth.legal.takeTime') }}</span>
        </nav>
        <article
          ref="scrollArea"
          :key="selected"
          @scroll="updateActive"
        >
          <div class="legal-title-row">
            <h1>
              <span><img
                :src="selected === LegalDocument.PRIVACY ? legalPrivacyIcon : legalTermsIcon"
                alt=""
                aria-hidden="true"
              >{{ t(document.titleKey) }}</span>
            </h1>
            <p class="legal-meta">
              {{ t('auth.legal.metadata', { version: document.version }) }}
            </p>
          </div>
          <aside class="legal-overview">
            <h2>
              <img
                :src="growthFlagIcon"
                alt=""
                aria-hidden="true"
              >{{ t('auth.legal.overview') }}
            </h2>
            <p>{{ document.sections[0].paragraphs[0] }}</p>
          </aside>
          <section
            v-for="(section, index) in document.sections"
            :id="section.id"
            :key="section.id"
            :style="{ '--i': index }"
          >
            <h2>
              <span class="section-number">{{ index + 1 }}</span>{{ section.title }}
            </h2>
            <p
              v-for="paragraph in section.paragraphs"
              :key="paragraph"
            >
              {{ paragraph }}
            </p>
          </section>
        </article>
      </div>
      <footer class="legal-footer">
        <div class="reading-progress">
          <div
            class="reading-track"
            :class="{ full: progress >= 100 }"
            role="progressbar"
            :aria-label="t('auth.legal.progress')"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="progress"
          >
            <i
              class="reading-fill"
              :style="{ width: progress + '%' }"
            />
          </div>
          <b class="reading-percent">{{ t('auth.legal.percent', { value: progress }) }}</b>
        </div>
        <router-link
          class="legal-finished"
          :class="{ done: progress >= 100 }"
          :to="{ name: returnRoute }"
        >
          <img
            :src="growthCheckIcon"
            alt=""
            aria-hidden="true"
          >{{ t('auth.legal.finished') }}
        </router-link>
      </footer>
    </section>
  </main>
</template>
<script src="./AgreementView.js"></script>
<style lang="scss" scoped src="./AgreementView.scss"></style>
