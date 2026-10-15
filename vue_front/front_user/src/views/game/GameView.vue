<template>
  <main class="game-page">
    <section
      v-if="phase === GamePhase.TITLE"
      class="game-title"
      :style="{ backgroundImage: `url(${titleArt})` }"
    >
      <div class="title-card">
        <h1>{{ t('game.title') }}</h1>
        <p>{{ t('game.tagline') }}</p>
        <div class="title-actions">
          <button
            type="button"
            class="game-button primary"
            @click="enter"
          >
            {{ t('game.enter') }}
          </button>
          <router-link
            class="game-button"
            :to="{ name: RouteName.HOME }"
          >
            {{ t('game.back') }}
          </router-link>
        </div>
      </div>
    </section>

    <div
      v-show="phase === GamePhase.PLAYING || phase === GamePhase.LOADING"
      class="game-stage"
    >
      <canvas
        ref="canvas"
        class="game-canvas"
        tabindex="0"
        :aria-label="t('game.title')"
      />
      <template v-if="phase === GamePhase.PLAYING">
        <div class="hud-top">
          <div class="hud-chip">
            <img
              :src="coinArt"
              alt=""
            >
            <span class="sr-only">{{ t('game.coins') }}</span>
            <b>{{ profile.coins }}</b>
          </div>
          <div
            class="hud-chip hud-stars"
            :aria-label="t('game.stars')"
          >
            <img
              v-for="n in 3"
              :key="n"
              :src="starArt"
              alt=""
              :class="{ dim: n > profile.stars }"
            >
          </div>
          <span
            v-if="profile.cleared"
            class="hud-badge"
          >{{ t('game.guardian') }}</span>
          <button
            type="button"
            class="hud-pause"
            :aria-label="t('game.pause')"
            @click="pause"
          >
            <svg
              viewBox="0 0 32 32"
              aria-hidden="true"
            >
              <rect
                x="9"
                y="8"
                width="5"
                height="16"
                rx="2.5"
              />
              <rect
                x="18"
                y="8"
                width="5"
                height="16"
                rx="2.5"
              />
            </svg>
          </button>
        </div>
        <transition-group
          name="hud-toast"
          tag="div"
          class="hud-toasts"
          aria-live="polite"
        >
          <span
            v-for="toast in toasts"
            :key="toast.id"
            class="hud-toast"
          >{{ toast.text }}</span>
        </transition-group>
        <p
          v-if="!locked && !paused && !shopOpen && !mapOpen && !ending"
          class="hud-hint"
        >
          {{ t('game.lookHint') }}
        </p>
        <GameMap
          :open="mapOpen"
          :shrine="shrine"
          :locate="locate"
          @toggle="toggleMap"
        />
        <p
          v-if="prompt"
          class="hud-prompt"
        >
          <kbd v-if="prompt !== Prompt.SHRINE_LIT && !(prompt === Prompt.SHRINE && missing)">{{ t('game.controls.interact') }}</kbd>
          {{ promptText }}
        </p>
      </template>
      <PageState
        v-if="phase === GamePhase.LOADING"
        class="game-loading"
        state="loading"
        size="page"
        :text="t('game.loading')"
      />
    </div>

    <PageState
      v-if="phase === GamePhase.ERROR"
      class="game-error"
      state="error"
      size="page"
      :error="error"
      :text="errorText"
      @retry="enter"
    />

    <GameShop
      v-if="shopOpen"
      :items="items"
      :coins="profile.coins"
      :hat="profile.hat"
      :outfit="profile.outfit"
      @bought="bought"
      @equipped="equipped"
      @close="closeOverlay"
    />

    <DialogShell
      :model-value="paused"
      :title="t('game.pause')"
      :icon="IconPlayerPause"
      tone="blue"
      @close="closeOverlay"
    >
      <h3 class="pause-heading">
        {{ t('game.controlsTitle') }}
      </h3>
      <dl class="pause-controls">
        <template
          v-for="key in controlKeys"
          :key="key"
        >
          <dt><kbd>{{ t('game.controls.' + key) }}</kbd></dt>
          <dd>{{ t('game.controls.' + key + 'Text') }}</dd>
        </template>
      </dl>
      <template #footer>
        <div class="pause-actions">
          <router-link
            class="game-button"
            :to="{ name: RouteName.HOME }"
          >
            {{ t('game.back') }}
          </router-link>
          <button
            type="button"
            class="game-button primary"
            @click="closeOverlay"
          >
            {{ t('game.resume') }}
          </button>
        </div>
      </template>
    </DialogShell>

    <transition name="game-ending">
      <section
        v-if="ending"
        class="game-ending"
        role="dialog"
        aria-modal="true"
        :aria-label="t('game.ending.title')"
      >
        <img
          :src="endingArt"
          alt=""
        >
        <div class="ending-card">
          <h2>{{ t('game.ending.title') }}</h2>
          <p>{{ t('game.ending.line') }}</p>
          <p class="ending-badge">
            {{ t('game.ending.badge') }}
          </p>
          <p
            v-if="ending.reward"
            class="ending-reward"
          >
            {{ t('game.ending.reward', { count: ending.reward }) }}
          </p>
          <button
            type="button"
            class="game-button primary"
            @click="closeOverlay"
          >
            {{ t('game.ending.again') }}
          </button>
        </div>
      </section>
    </transition>
  </main>
</template>
<script src="./GameView.js"></script>
<style lang="scss" scoped src="./GameView.scss"></style>
