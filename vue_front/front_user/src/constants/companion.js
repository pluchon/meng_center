export const Companion = Object.freeze({
  MAX_LENGTH: 1000,
  // 一条消息里图片和表情合计最多几个
  MAX_MEDIA: 5,
  // 奶糖离线那句提示的配音时长（毫秒）
  OFFLINE_VOICE_MS: 8280,
  // 奶糖离线时隔多久再探一次她回来没有
  PRESENCE_PROBE_MS: 15000,
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
