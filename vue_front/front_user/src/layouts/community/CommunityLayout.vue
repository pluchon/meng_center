<template>
  <div class="community-layout">
    <header class="community-nav">
      <router-link
        class="community-brand"
        :to="{name:RouteName.HOME}"
      >
        <span
          class="brand-bubble"
          aria-hidden="true"
        ><img
          class="brand-logo"
          :src="brandIcon"
          alt=""
          width="44"
          height="44"
        ><span
          class="brand-sparkle"
          :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
        /></span><strong>{{ t('home.brand') }}</strong>
      </router-link>
      <nav
        ref="navRoot"
        :aria-label="t('home.title')"
        @pointerover="navSlider.onHover"
        @pointerleave="navSlider.onLeave"
        @focusin="navSlider.onHover"
        @focusout="navSlider.onLeave"
      >
        <span
          class="nav-slider"
          :class="navSlider.sliderClass.value"
          :style="navSlider.sliderStyle.value"
          aria-hidden="true"
        />
        <router-link
          class="nav-home"
          data-slide
          :class="{selected:route.name===RouteName.HOME}"
          :to="{name:RouteName.HOME}"
        >
          <span class="nav-icon"><img
            :src="navHomeIcon"
            alt=""
            aria-hidden="true"
          ></span>{{ t('home.nav.home') }}<span
            class="nav-sparkle"
            aria-hidden="true"
            :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
          />
        </router-link>
        <router-link
          class="nav-creator"
          data-slide
          :class="{selected:route.name===RouteName.CREATOR}"
          :to="{name:RouteName.CREATOR}"
        >
          <span class="nav-icon"><img
            :src="navCreateIcon"
            alt=""
            aria-hidden="true"
          ></span>{{ t('home.nav.creator') }}
        </router-link>
        <router-link
          class="nav-growth-page"
          data-slide
          :class="{selected:route.name===RouteName.GROWTH}"
          :to="{name:RouteName.GROWTH}"
        >
          <span class="nav-icon"><img
            :src="navGrowthIcon"
            alt=""
            aria-hidden="true"
          ></span>{{ t('home.nav.growth') }}
        </router-link>
        <router-link
          data-slide
          :to="{name:RouteName.GAME}"
        >
          <span class="nav-icon"><img
            :src="navIslandIcon"
            alt=""
            aria-hidden="true"
          ></span>{{ t('home.nav.game') }}
        </router-link>
        <button
          type="button"
          data-slide
          @click="unavailable"
        >
          <span class="nav-icon"><img
            :src="navMengIcon"
            alt=""
            aria-hidden="true"
          ></span>{{ t('home.nav.ai') }}
        </button>
      </nav>
      <div class="nav-tools">
        <div class="nav-search-slot">
          <form
            class="nav-search"
            :class="{expanded:searchOpen}"
            role="search"
            @mouseenter="searchHovered=true;searchOpen=true"
            @mouseleave="leaveSearch"
            @focusin="searchOpen=true"
            @focusout="blurSearch"
            @submit.prevent="submitSearch"
          >
            <button
              type="button"
              :aria-label="t('home.nav.search')"
              :aria-expanded="searchOpen"
              @click="activateSearch"
            >
              <img
                class="nav-tool-icon"
                :src="navSearchIcon"
                alt=""
                aria-hidden="true"
              >
            </button>
            <input
              ref="searchInput"
              v-model="searchText"
              type="search"
              :aria-label="t('home.nav.search')"
              :placeholder="t('home.searchPlaceholder')"
              :tabindex="searchOpen?0:-1"
              @keydown.esc="closeSearch"
            >
          </form>
        </div>
        <router-link
          class="nav-messages"
          :to="{name:RouteName.MESSAGES}"
          :aria-label="t('im.title')"
        >
          <img
            class="nav-tool-icon"
            :src="navMessageIcon"
            alt=""
            aria-hidden="true"
          ><span
            v-if="im.totalUnread"
            class="nav-unread"
          >{{ im.totalUnread>99?t('im.badgeMax'):im.totalUnread }}</span>
        </router-link>
        <div
          v-if="growth.data"
          class="nav-growth"
          :title="growth.data.nextLevelExp==null?t('home.maxLevel',{exp:growth.data.exp}):t('home.exp',{exp:growth.data.exp,next:growth.data.nextLevelExp})"
        >
          <span>{{ t('home.level',{level:growth.data.level}) }}</span><div class="nav-experience">
            <small>{{ growth.data.nextLevelExp==null?growth.data.exp:t('home.expValue',{exp:growth.data.exp,next:growth.data.nextLevelExp}) }}</small><el-progress
              :percentage="growth.progress"
              color="var(--meng-growth-green)"
              :show-text="false"
            />
          </div>
        </div>
        <router-link
          class="nav-avatar"
          :to="{name:RouteName.ACCOUNT}"
          :aria-label="t('home.nav.account')"
          :aria-current="route.name===RouteName.ACCOUNT?'page':undefined"
        >
          <el-avatar
            :src="user.current?.avatar || userAvatar"
            :alt="t('home.nav.account')"
          />
        </router-link>
      </div>
    </header>
    <main class="community-main">
      <slot />
    </main>
    <footer
      v-if="route.name!==RouteName.CREATOR&&route.name!==RouteName.GROWTH&&route.name!==RouteName.ACCOUNT"
      class="community-footer"
    >
      <div class="footer-inner">
        <div class="footer-brand">
          <span
            class="footer-brand-icon"
            aria-hidden="true"
          ><img
            :src="brandIcon"
            alt=""
            width="36"
            height="36"
          ></span><strong>{{ t('home.brand') }}</strong>
        </div>
        <p class="footer-note">
          {{ t('home.footer') }}
        </p>
        <div class="footer-links">
          <div>
            <h2>{{ t('home.footerTerms') }}</h2>
            <router-link :to="{name:RouteName.AGREEMENT,query:{document:LegalDocument.TERMS,from:RouteName.HOME}}">
              {{ t('home.terms') }}
            </router-link>
            <router-link :to="{name:RouteName.AGREEMENT,query:{document:LegalDocument.PRIVACY,from:RouteName.HOME}}">
              {{ t('home.privacy') }}
            </router-link>
          </div>
          <div>
            <h2>{{ t('home.footerContact') }}</h2><button
              type="button"
              @click="unavailable"
            >
              {{ t('home.contactEmail') }}
            </button><button
              type="button"
              @click="unavailable"
            >
              {{ t('home.feedback') }}
            </button>
          </div>
        </div>
        <div
          class="footer-art"
          aria-hidden="true"
          :style="{maskImage:'url('+JSON.stringify(communityFooterArt)+')'}"
        />
      </div>
    </footer>
  </div>
</template>
<script src="./CommunityLayout.js"></script>
<style lang="scss" scoped src="./CommunityLayout.scss"></style>
