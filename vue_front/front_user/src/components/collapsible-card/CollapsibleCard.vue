<template>
  <div
    class="collapsible-card"
    :class="{collapsed}"
  >
    <button
      type="button"
      class="collapse-toggle"
      :aria-label="t(collapsed?'home.expandCard':'home.collapseCard',{name:label})"
      :aria-expanded="!collapsed"
      @click="$emit('update:collapsed',!collapsed)"
    >
      <el-icon><component :is="collapsed?icon:Minus" /></el-icon>
    </button>
    <div
      class="card-reveal"
      :inert="collapsed"
    >
      <div class="card-content">
        <slot />
      </div>
    </div>
  </div>
</template>
<script src="./CollapsibleCard.js"></script>
<style lang="scss" scoped src="./CollapsibleCard.scss"></style>
