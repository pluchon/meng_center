<template>
  <span
    class="naitang-pet"
    :class="{ cheering }"
    aria-hidden="true"
  >
    <span class="naitang-pet-stage">
      <span class="naitang-pet-idle">
        <span class="naitang-pet-tail"><img
          :src="companionPetTailStrip"
          alt=""
        ></span>
        <img
          :src="companionPetBody"
          alt=""
        >
        <img
          class="naitang-pet-eyes half"
          :src="companionPetEyesHalf"
          alt=""
        >
        <img
          class="naitang-pet-eyes closed"
          :src="companionPetEyesClosed"
          alt=""
        >
        <img
          v-for="(mouth, index) in mouths"
          v-show="talking && syllable === index + 1"
          :key="mouth"
          :src="mouth"
          alt=""
        >
      </span>
      <img
        class="naitang-pet-pose smile"
        :src="companionPetPoseSmile"
        alt=""
      >
      <img
        class="naitang-pet-pose crouch"
        :src="companionPetPoseCrouch"
        alt=""
      >
      <img
        class="naitang-pet-pose leap"
        :src="companionPetPoseLeap"
        alt=""
      >
    </span>
  </span>
</template>
<script src="./NaitangPet.js"></script>
<style lang="scss" scoped src="./NaitangPet.scss"></style>
