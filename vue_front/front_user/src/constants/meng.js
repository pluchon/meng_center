export const Companion = Object.freeze({
  CHARACTERS: ['TSUNDERE', 'GENKI', 'NEKO'],
  MAX_LENGTH: 1000,
  MAX_IMAGES: 3,
  SESSION_LIMIT: 50,
  PAGE_SIZE: 20,
  HISTORY_SIZE: 30,
  TYPE_MS: 40,
  TYPE_MIN_MS: 600,
  TYPE_MAX_MS: 2500,
})
export const CompanionEvent = Object.freeze({
  STATUS: 'status',
  BUBBLE: 'bubble',
  WORK: 'work',
  STAGE: 'stage',
  DONE: 'done',
  ERROR: 'error',
})
export const CompanionError = Object.freeze({
  BUSY: 'COMPANION_BUSY',
  QUOTA: 'COMPANION_QUOTA_EXCEEDED',
  COOLDOWN: 'COMPANION_COOLDOWN',
  UNAVAILABLE: 'AI_UNAVAILABLE',
  SESSION_LIMIT: 'COMPANION_SESSION_LIMIT',
})
export const CompanionContext = Symbol('companion')
