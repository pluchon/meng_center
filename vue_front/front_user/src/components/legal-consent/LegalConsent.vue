<template>
  <div
    class="legal-consent"
    :class="{ 'is-invalid': invalid }"
  >
    <span class="consent-mark">
      <input
        type="checkbox"
        class="consent-checkbox"
        :checked="modelValue"
        :aria-invalid="invalid"
        :aria-label="t('auth.register.consent')"
        @change="$emit('update:modelValue', $event.target.checked)"
      >
      <img
        class="consent-tick"
        :src="checkIcon"
        alt=""
        aria-hidden="true"
      >
    </span>
    <span class="consent-copy">
      <button
        type="button"
        class="consent-toggle"
        @click="$emit('update:modelValue', !modelValue)"
      >
        {{ t(invalid ? 'auth.register.consentRequired' : 'auth.register.consent') }}
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
    <span
      v-if="invalid"
      class="consent-alert"
      role="alert"
    >{{ t('auth.register.mustAgree') }}</span>
  </div>
</template>
<script src="./LegalConsent.js"></script>
<style lang="scss" scoped src="./LegalConsent.scss"></style>
