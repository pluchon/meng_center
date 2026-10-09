<template>
  <div
    class="auth-decor"
    :class="'decor-' + variant"
    aria-hidden="true"
  >
    <svg
      class="decor-item sparkle-a"
      viewBox="0 0 24 24"
    >
      <path d="M12 2c.8 6 4 9.2 10 10-6 .8-9.2 4-10 10-.8-6-4-9.2-10-10 6-.8 9.2-4 10-10z" />
    </svg>
    <svg
      class="decor-item sparkle-b"
      viewBox="0 0 24 24"
    >
      <path d="M12 2c.8 6 4 9.2 10 10-6 .8-9.2 4-10 10-.8-6-4-9.2-10-10 6-.8 9.2-4 10-10z" />
    </svg>
    <svg
      class="decor-item sakura-a"
      viewBox="0 0 24 24"
    >
      <g class="sakura-petals">
        <ellipse
          cx="12"
          cy="6.5"
          rx="3.2"
          ry="5"
        />
        <ellipse
          cx="12"
          cy="6.5"
          rx="3.2"
          ry="5"
          transform="rotate(72 12 12)"
        />
        <ellipse
          cx="12"
          cy="6.5"
          rx="3.2"
          ry="5"
          transform="rotate(144 12 12)"
        />
        <ellipse
          cx="12"
          cy="6.5"
          rx="3.2"
          ry="5"
          transform="rotate(216 12 12)"
        />
        <ellipse
          cx="12"
          cy="6.5"
          rx="3.2"
          ry="5"
          transform="rotate(288 12 12)"
        />
      </g>
      <circle
        class="sakura-heart"
        cx="12"
        cy="12"
        r="1.8"
      />
    </svg>
    <svg
      class="decor-item bubble-a"
      viewBox="0 0 40 40"
    >
      <circle
        cx="20"
        cy="20"
        r="17"
      />
      <path d="M11 17a10 10 0 0 1 6-6" />
    </svg>
    <svg
      class="decor-item bubble-b"
      viewBox="0 0 40 40"
    >
      <circle
        cx="20"
        cy="20"
        r="17"
      />
    </svg>
    <svg
      class="decor-item cloud-a"
      viewBox="0 0 420 190"
    >
      <circle
        cx="110"
        cy="120"
        r="56"
      />
      <circle
        cx="200"
        cy="88"
        r="80"
      />
      <circle
        cx="300"
        cy="118"
        r="60"
      />
      <rect
        x="70"
        y="112"
        width="280"
        height="66"
        rx="33"
      />
    </svg>
    <svg
      class="decor-item wave-a"
      viewBox="0 0 76 16"
    >
      <path d="M2 8c6-8 12 8 18 0s12-8 18 0 12 8 18 0 12-8 18 0" />
    </svg>
    <span class="decor-item sticker sticker-a">{{ t(stickers[0]) }}</span>
    <span class="decor-item sticker sticker-b">{{ t(stickers[1]) }}</span>
  </div>
</template>
<script src="./AuthDecor.js"></script>
<style lang="scss" scoped src="./AuthDecor.scss"></style>
