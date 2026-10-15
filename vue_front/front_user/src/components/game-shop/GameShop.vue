<template>
  <DialogShell
    :model-value="true"
    :title="t('game.shop.title')"
    :icon="IconBuildingStore"
    tone="pink"
    width="min(640px,calc(100vw - 32px))"
    :busy="busy !== ''"
    @close="$emit('close')"
  >
    <div class="shop-head">
      <img
        class="shop-portrait"
        :src="portrait"
        alt=""
      >
      <p>{{ t('game.shop.greeting') }}</p>
      <span class="shop-wallet">
        <img
          :src="coinArt"
          alt=""
        >
        <b>{{ coins }}</b>
      </span>
    </div>
    <section
      v-for="group in groups"
      :key="group.kind"
      class="shop-group"
    >
      <h3>{{ t(group.title) }}</h3>
      <ul>
        <li
          v-for="item in group.items"
          :key="item.id"
          :class="{ worn: worn(item) }"
        >
          <span
            class="shop-swatch"
            :style="swatchStyle(item)"
            aria-hidden="true"
          >
            <svg
              v-if="item.kind === ItemKind.HAT"
              viewBox="0 0 48 48"
            >
              <path
                v-if="item.id === 'hat-leaf'"
                d="M24 34V20M24 22c-3-7-10-8-14-5 5 2 9 4 14 5Zm0 0c3-7 10-8 14-5-5 2-9 4-14 5Z"
              />
              <path
                v-else-if="item.id === 'hat-bow'"
                d="M24 26c-6-7-14-8-15-3s7 9 15 3Zm0 0c6-7 14-8 15-3s-7 9-15 3Zm-3 0a3 3 0 1 0 6 0 3 3 0 1 0-6 0Z"
              />
              <path
                v-else
                d="M11 33h26l2-15-8 6-7-10-7 10-8-6Z"
              />
            </svg>
          </span>
          <strong>{{ t('game.items.' + item.id) }}</strong>
          <small v-if="!item.owned">{{ t('game.shop.price', { price: item.price }) }}</small>
          <small v-else-if="worn(item)">{{ t('game.shop.wearing') }}</small>
          <button
            v-if="!item.owned"
            type="button"
            class="shop-action buy"
            :disabled="busy !== '' || coins < item.price"
            @click="buy(item)"
          >
            {{ t('game.shop.buy') }}
          </button>
          <button
            v-else
            type="button"
            class="shop-action"
            :disabled="busy !== ''"
            @click="toggle(item)"
          >
            {{ worn(item) ? t('game.shop.takeOff') : t('game.shop.wear') }}
          </button>
        </li>
      </ul>
    </section>
  </DialogShell>
</template>
<script src="./GameShop.js"></script>
<style lang="scss" scoped src="./GameShop.scss"></style>
