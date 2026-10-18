<template>
  <CommunityLayout class="meng-layout">
    <ShapeGrid />
    <div class="meng-page">
      <MengState
        v-if="(characters.loading && !characters.data.length) || characters.error"
        :error="characters.error"
        :loading="characters.loading"
        page
        @retry="start"
      />
      <MengState
        v-else-if="chatting && !character"
        kind="character"
        page
        @retry="goHome"
      />
      <MengChat v-else-if="chatting" />
      <MengHome v-else />
      <MengDialogs v-if="dialog || stageNotice" />
    </div>
  </CommunityLayout>
</template>
<script src="./MengView.js"></script>
<style lang="scss" scoped src="./MengView.scss"></style>
