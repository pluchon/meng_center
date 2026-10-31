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
