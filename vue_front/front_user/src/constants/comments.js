import { userAvatar, mengAvatar, articleEditAvatar, resetAvatar, brandIcon } from '@/assets/images'
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
  { name: '水滴', image: brandIcon },
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
