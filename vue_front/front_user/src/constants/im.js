export const MessageTab = Object.freeze({
  CHAT: 'chat',
  COMMENT: 'comment',
  LIKE: 'like',
  SYSTEM: 'system',
})
export const MessageTabs = Object.freeze(Object.values(MessageTab))
export const NotificationChannel = Object.freeze({
  comment: 'COMMENT',
  like: 'LIKE',
  system: 'SYSTEM',
})
export const Im = Object.freeze({
  MESSAGE_SIZE: 30,
  NOTICE_SIZE: 10,
  TEXT_MAX: 500,
  IMAGE_MAX: 10,
  EMOTION_MAX: 10,
  TIME_GAP: 5 * 60000,
  HEARTBEAT_MS: 25000,
  HEARTBEAT_TIMEOUT: 60000,
  RECONNECT_MIN: 1000,
  RECONNECT_MAX: 30000,
  REFRESH_DELAY: 120,
})
export const MessageKind = Object.freeze({ TEXT: 'TEXT', IMAGE: 'IMAGE' })
export const NoticeType = Object.freeze({
  COMPANION_STAGE_REACHED: 'COMPANION_STAGE_REACHED',
  COMPANION_PROMISE_DUE: 'COMPANION_PROMISE_DUE',
  COMPANION_DRAWING_FINISHED: 'COMPANION_DRAWING_FINISHED',
  POST_COMMENT: 'POST_COMMENT',
  COMMENT_REPLY: 'COMMENT_REPLY',
  COMMENT_MENTION: 'COMMENT_MENTION',
  POST_LIKED: 'POST_LIKED',
  POST_FAVORITED: 'POST_FAVORITED',
  COMMENT_LIKED: 'COMMENT_LIKED',
  LEVEL_CHANGED: 'LEVEL_CHANGED',
  USER_APPROVED: 'USER_APPROVED',
  FOLLOWED: 'FOLLOWED',
  POST_REVIEW_CHANGED: 'POST_REVIEW_CHANGED',
  COMMENT_REVIEW_CHANGED: 'COMMENT_REVIEW_CHANGED',
  PROFILE_REVIEW_CHANGED: 'PROFILE_REVIEW_CHANGED',
})
export const MessageAvatarTones = Object.freeze(['blue', 'mint', 'violet', 'gold', 'pink'])
