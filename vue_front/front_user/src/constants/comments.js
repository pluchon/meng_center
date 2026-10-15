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
export const Emotions = Object.freeze([
  { name: '头像', image: userAvatar },
  { name: '萌萌', image: mengAvatar },
  { name: '写帖', image: articleEditAvatar },
  { name: '找回', image: resetAvatar },
  { name: '水滴', image: waterDropIcon },
  { name: '疑惑', image: cirnoConfusedEmotion },
  { name: '齐天小圣', image: cirnoMonkeyKingEmotion },
  { name: '开工', image: cirnoWorkEmotion },
  { name: '好耶', image: cirnoCheerEmotion },
  { name: '超喜欢', image: cirnoLoveEmotion },
  { name: '牛', image: cirnoAwesomeEmotion },
  { name: '不准发癫', image: cirnoNoChaosEmotion },
  { name: '吃冰棍', image: cirnoPopsicleEmotion },
  { name: '摇葫芦', image: cirnoShakeEmotion },
  { name: '生气', image: cirnoAngryEmotion },
  { name: '躲一下', image: cirnoShyEmotion },
  { name: '太烧了', image: cirnoOverheatedEmotion },
  { name: '呼呼呼', image: cirnoAirplaneEmotion },
  { name: '欢呼', image: cirnoHoorayEmotion },
  { name: '直呼内行', image: cirnoExpertEmotion },
  { name: '困了', image: cirnoSleepyEmotion },
  { name: '嗷呜', image: cirnoRoarEmotion },
  { name: '摸摸', image: cirnoHeadpatEmotion },
  { name: '好家伙', image: cirnoWowEmotion },
])
// 表情系列：经典是最早的几个，其余的都归琪露诺；以后新增系列在这里分组，新加进 Emotions 的默认进最后一个系列
const classicNames = new Set(['头像', '萌萌', '写帖', '找回', '水滴'])
export const EmotionSeries = Object.freeze([
  {
    key: 'classic',
    items: Object.freeze(Emotions.filter((item) => classicNames.has(item.name))),
  },
  {
    key: 'cirno',
    items: Object.freeze(Emotions.filter((item) => !classicNames.has(item.name))),
  },
])
export function emotionToken(name) {
  return `[表情:${name}]`
}
const emotionLookup = new Map(Emotions.map((item) => [item.name, item.image]))
export function commentSegments(text) {
  const lookup = emotionLookup
  return text
    .split(/(\[表情:[^\s[\]]{1,12}\])/u)
    .filter(Boolean)
    .map((part) => {
      const name = part.startsWith('[表情:') ? part.slice(4, -1) : null
      return { text: part, image: lookup.get(name), name }
    })
}
