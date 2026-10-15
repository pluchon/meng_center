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
])
export function emotionToken(name) {
  return `[表情:${name}]`
}
export function commentSegments(text) {
  const lookup = new Map(Emotions.map((item) => [item.name, item.image]))
  return text
    .split(/(\[表情:[^\s[\]]{1,12}\])/u)
    .filter(Boolean)
    .map((part) => {
      const name = part.startsWith('[表情:') ? part.slice(4, -1) : null
      return { text: part, image: lookup.get(name), name }
    })
}
