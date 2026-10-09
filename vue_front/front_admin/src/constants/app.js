export const Request = Object.freeze({ TIMEOUT: 20000 })
export const Paging = Object.freeze({ SIZE: 20, FIRST: 1 })
export const UserRole = Object.freeze({ ADMIN: 'ADMIN', USER: 'USER' })
export const UserStatus = Object.freeze({
  PENDING: 'PENDING',
  NORMAL: 'NORMAL',
  REJECTED: 'REJECTED',
})
export const Theme = Object.freeze({ LIGHT: 'light', DARK: 'dark' })
export const StorageKey = Object.freeze({ THEME: 'meng.theme' })
export const ContactChannel = Object.freeze({ EMAIL: 'EMAIL', SMS: 'SMS' })
// 滑块弹窗的状态，以及拖错后停留、通过后展示的毫秒数
export const Captcha = Object.freeze({
  IDLE: 'idle',
  DRAGGING: 'dragging',
  CHECKING: 'checking',
  FAILED: 'failed',
  PASSED: 'passed',
  FAILURE_HOLD: 900,
  SUCCESS_HOLD: 450,
  DEFAULT_RATIO: '5 / 3',
})
export const Auth = Object.freeze({
  CODE_SECONDS: 60,
  REASON_MAX: 200,
  PASSWORD_MIN: 8,
  PASSWORD_BYTES_MAX: 72,
})
export const LegalVersion = Object.freeze({ TERMS: '2026-10-08', PRIVACY: '2026-10-08' })
export const Regions = Object.freeze([
  { key: 'taiwan', code: '+886' },
  { key: 'hongkong', code: '+852' },
  { key: 'macau', code: '+853' },
  { key: 'mainland', code: '+86' },
  { key: 'japan', code: '+81' },
  { key: 'singapore', code: '+65' },
  { key: 'malaysia', code: '+60' },
  { key: 'northamerica', code: '+1' },
])
