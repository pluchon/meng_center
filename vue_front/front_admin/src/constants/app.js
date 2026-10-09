export const Request = Object.freeze({ TIMEOUT: 20000 })
export const Paging = Object.freeze({ SIZE: 20, FIRST: 1 })
export const UserRole = Object.freeze({ ADMIN: 'ADMIN', USER: 'USER' })
export const UserStatus = Object.freeze({
  PENDING: 'PENDING',
  NORMAL: 'NORMAL',
  REJECTED: 'REJECTED',
})
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
// 拒绝原因的长度上限，与后端校验一致
export const Auth = Object.freeze({ REASON_MAX: 200 })
