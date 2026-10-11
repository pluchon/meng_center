<template>
  <div :class="['collapse-panel', { open }]">
    <div
      class="collapse-panel-content"
      :inert="!open"
      :aria-hidden="!open"
    >
      <slot />
    </div>
  </div>
</template>
<script src="./CollapsePanel.js"></script>
<style lang="scss" scoped src="./CollapsePanel.scss"></style>
