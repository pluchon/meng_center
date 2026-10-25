import { useEmotionStore } from '@/stores/emotions'
import {
  userAvatar,
  mengAvatar,
  articleEditAvatar,
  resetAvatar,
  waterDropIcon,
  cirnoConfusedEmotion,
  cirnoMonkeyKingEmotion,
  cirnoWorkEmotion,
  cirnoCheerEmotion,
  cirnoLoveEmotion,
  cirnoAwesomeEmotion,
  cirnoNoChaosEmotion,
  cirnoPopsicleEmotion,
  cirnoShakeEmotion,
  cirnoAngryEmotion,
  cirnoShyEmotion,
  cirnoOverheatedEmotion,
  cirnoAirplaneEmotion,
  cirnoHoorayEmotion,
  cirnoExpertEmotion,
  cirnoSleepyEmotion,
  cirnoRoarEmotion,
  cirnoHeadpatEmotion,
  cirnoWowEmotion,
} from '@/assets/images'
export const Comment = Object.freeze({ SIZE: 10, LENGTH: 500, ATTACHMENTS: 10 })
export const ReportTarget = Object.freeze({ POST: 'POST', COMMENT: 'COMMENT' })
export const ReportReasons = Object.freeze([
  'ILLEGAL_CONTENT',
  'ABUSE',
  'ADVERTISEMENT',
  'SPOILER',
  'OTHER',
])
export const emotionImages = Object.freeze({
  userAvatar,
  mengAvatar,
  articleEditAvatar,
  resetAvatar,
  waterDropIcon,
  cirnoConfusedEmotion,
  cirnoMonkeyKingEmotion,
  cirnoWorkEmotion,
  cirnoCheerEmotion,
  cirnoLoveEmotion,
  cirnoAwesomeEmotion,
  cirnoNoChaosEmotion,
  cirnoPopsicleEmotion,
  cirnoShakeEmotion,
  cirnoAngryEmotion,
  cirnoShyEmotion,
  cirnoOverheatedEmotion,
  cirnoAirplaneEmotion,
  cirnoHoorayEmotion,
  cirnoExpertEmotion,
  cirnoSleepyEmotion,
  cirnoRoarEmotion,
  cirnoHeadpatEmotion,
  cirnoWowEmotion,
})
export function emotionToken(name) {
  return `[表情:${name}]`
}
export function commentSegments(text) {
  const lookup = new Map(useEmotionStore().items.map((item) => [item.name, item.image]))
  return text
    .split(/(\[表情:[^\s[\]]{1,12}\])/u)
    .filter(Boolean)
    .map((part) => {
      const name = part.startsWith('[表情:') ? part.slice(4, -1) : null
      return { text: part, image: lookup.get(name), name }
    })
}
