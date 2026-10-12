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
        ><span
          class="brand-drop"
          :style="{maskImage:'url('+JSON.stringify(communityDrop)+')'}"
        /><span
          class="brand-sparkle"
          :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
        /></span><strong>{{ t('home.brand') }}</strong>
      </router-link>
      <nav :aria-label="t('home.title')">
        <router-link
          class="nav-home"
          :class="{selected:route.name===RouteName.HOME}"
          :to="{name:RouteName.HOME}"
        >
          <span class="nav-icon home-icon"><el-icon><House /></el-icon></span>{{ t('home.nav.home') }}<span
            class="nav-sparkle"
            aria-hidden="true"
            :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
          />
        </router-link>
        <button
          type="button"
          @click="unavailable"
        >
          <span class="nav-icon message-icon"><el-icon><Message /></el-icon></span>{{ t('home.nav.messages') }}
        </button>
        <button
          type="button"
          @click="unavailable"
        >
          <span class="nav-icon growth-icon"><span
            class="nav-mask"
            aria-hidden="true"
            :style="{maskImage:'url('+JSON.stringify(communitySprout)+')'}"
          /></span>{{ t('home.nav.growth') }}
        </button>
        <button
          type="button"
          @click="unavailable"
        >
          <span class="nav-icon ai-icon"><span
            class="nav-mask"
            aria-hidden="true"
            :style="{maskImage:'url('+JSON.stringify(communitySparkle)+')'}"
          /></span>{{ t('home.nav.ai') }}
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
              <el-icon><SearchIcon /></el-icon>
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
        <button
          type="button"
          :aria-label="t('home.nav.notifications')"
          @click="unavailable"
        >
          <el-icon><Bell /></el-icon>
        </button>
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
    <footer class="community-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span
            class="footer-brand-icon"
            aria-hidden="true"
          ><span :style="{maskImage:'url('+JSON.stringify(communityDrop)+')'}" /></span><strong>{{ t('home.brand') }}</strong>
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
