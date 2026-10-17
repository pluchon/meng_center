import {
  companionTsundereDefaultPortrait,
  companionTsundereHappyPortrait,
  companionTsundereShyPortrait,
  companionTsundereWorriedPortrait,
  companionTsundereAngryPortrait,
  companionTsundereAvatar,
  companionGenkiDefaultPortrait,
  companionGenkiHappyPortrait,
  companionGenkiShyPortrait,
  companionGenkiWorriedPortrait,
  companionGenkiAngryPortrait,
  companionGenkiAvatar,
  companionNekoDefaultPortrait,
  companionNekoHappyPortrait,
  companionNekoShyPortrait,
  companionNekoWorriedPortrait,
  companionNekoAngryPortrait,
  companionNekoAvatar,
} from '@/assets/images'
export const companionArt = {
  TSUNDERE: {
    avatar: companionTsundereAvatar,
    portraits: [
      companionTsundereDefaultPortrait,
      companionTsundereHappyPortrait,
      companionTsundereShyPortrait,
    ],
    worried: companionTsundereWorriedPortrait,
    angry: companionTsundereAngryPortrait,
  },
  GENKI: {
    avatar: companionGenkiAvatar,
    portraits: [
      companionGenkiDefaultPortrait,
      companionGenkiHappyPortrait,
      companionGenkiShyPortrait,
    ],
    worried: companionGenkiWorriedPortrait,
    angry: companionGenkiAngryPortrait,
  },
  NEKO: {
    avatar: companionNekoAvatar,
    portraits: [companionNekoDefaultPortrait, companionNekoHappyPortrait, companionNekoShyPortrait],
    worried: companionNekoWorriedPortrait,
    angry: companionNekoAngryPortrait,
  },
}
