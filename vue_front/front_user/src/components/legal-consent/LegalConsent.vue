<template>
  <div class="legal-consent">
    <input
      type="checkbox"
      class="consent-checkbox"
      :checked="modelValue"
      :aria-label="t('auth.register.consent')"
      @change="$emit('update:modelValue', $event.target.checked)"
    >
    <span class="consent-copy">
      <button
        type="button"
        class="consent-toggle"
        @click="$emit('update:modelValue', !modelValue)"
      >
        {{ t('auth.register.consent') }}
      </button>
      <router-link
        :to="{ name: RouteName.AGREEMENT, query: { document: LegalDocument.TERMS, from: origin } }"
        target="_blank"
        rel="noopener"
      >{{ t('auth.register.terms') }}</router-link>
      <span>{{ t('auth.register.and') }}</span>
      <router-link
        :to="{
          name: RouteName.AGREEMENT,
          query: { document: LegalDocument.PRIVACY, from: origin },
        }"
        target="_blank"
        rel="noopener"
      >{{ t('auth.register.privacy') }}</router-link>
    </span>
  </div>
</template>
<script src="./LegalConsent.js"></script>
<style lang="scss" scoped src="./LegalConsent.scss"></style>
