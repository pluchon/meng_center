export const Companion = Object.freeze({
  MAX_LENGTH: 1000,
  MAX_IMAGES: 6,
  PLANE_MS: 1300,
  PLANE_ARC: 60,
  LANDING_MS: 420,
  IMAGE_BYTES: 10_000_000,
  SESSION_LIMIT: 50,
  PAGE_SIZE: 20,
  HISTORY_SIZE: 30,
  TYPE_MS: 40,
  TYPE_MIN_MS: 600,
  TYPE_MAX_MS: 2500,
  POLL_MS: 3000,
  // 网络断开时自动重发同一条消息的等待时间，依次 1、2、4 秒；三次都失败就等浏览器报告网络恢复再重发
  RETRY_DELAYS_MS: [1000, 2000, 4000],
  SCROLL_THRESHOLD: 60,
  VOICE_OUTSIDE_LINES: 3,
  PET_CHEER_MS: 1100,
  PET_CHEER_CHANCE: 0.2,
  PET_IDLE_CHECK_MS: 60_000,
  PET_IDLE_CHEER_CHANCE: 0.03,
  PET_SYLLABLE_MS: 100,
  // 1 闭嘴 2 微张 3 半张 4 大张 5 圆嘴 6 展开，带句间停顿
  PET_SYLLABLES: [1, 3, 4, 3, 1, 6, 3, 2, 5, 3, 4, 2, 1, 1, 3, 6, 4, 3, 2, 5, 2, 1, 3, 4, 6, 3, 2, 1, 1, 1],
})
export const CompanionEvent = Object.freeze({
  STATUS: 'status',
  BUBBLE: 'bubble',
  DONE: 'done',
  ERROR: 'error',
  QUEUED: 'queued',
})
export const CompanionError = Object.freeze({
  BUSY: 'COMPANION_BUSY',
  QUOTA: 'COMPANION_QUOTA_EXCEEDED',
  COOLDOWN: 'COMPANION_COOLDOWN',
  UNAVAILABLE: 'AI_UNAVAILABLE',
  SESSION_LIMIT: 'COMPANION_SESSION_LIMIT',
})
