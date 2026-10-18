export const ParticleMotion = Object.freeze({
  MAX_COMPONENT_POINTS: 8,
  MAX_NEIGHBORS: 3,
  DISPERSE_SECONDS: 2,
  DISPERSE_SPEED: 24,
  MIN_POINTS: 28,
  MAX_POINTS: 85,
  AREA_PER_POINT: 18000,
  LINK_DISTANCE: 185,
  LINK_START_DISTANCE: 150,
  NETWORK_INTERVAL: 0.1,
  STEERING_EASE: 4,
  LINK_EASE: 8,
  SPEED: 18,
  RADIUS: 2,
  POINT_OPACITY: 0.4,
  LINE_OPACITY: 0.24,
  MAX_PIXEL_RATIO: 2,
  MAX_DELTA: 0.05,
  POINTER_RADIUS: 150,
  POINTER_FORCE: 150,
  BREAK_RADIUS: 70,
  RECONNECT_SECONDS: 3,
  RECONNECT_SPEED: 24,
  RECONNECT_RADIUS: 320,
  RECONNECT_SPACING: 100,
})

export const CursorMotion = Object.freeze({
  POINTS: 20,
  MAX_PIXEL_RATIO: 1.25,
  FOLLOW_SPEED: 0.48,
  CHAIN_SPEED: 0.5,
  FRAME_SECONDS: 1 / 60,
  MAX_DELTA: 0.05,
  WIDTH: 3,
  HALO_WIDTH: 8,
  HEAD_HALO: 12,
  OPACITY: 0.72,
  IDLE_MILLISECONDS: 90,
  FADE_SECONDS: 0.35,
  MIN_FADE: 0.01,
  TAPER_POWER: 2.3,
  MAX_DUST: 22,
  DUST_INTERVAL: 0.025,
  DUST_SPEED_THRESHOLD: 70,
  DUST_SPREAD: 24,
  DUST_LIFETIME: 0.55,
  DUST_RADIUS: 1.3,
})

export const CardSwapMotion = Object.freeze({
  DURATION: 760,
  EASING: 'cubic-bezier(0.22, 0.7, 0.22, 1)',
  LIFT_RATIO: 0.58,
  MAX_LIFT: 360,
  BACK_DEPTH: 180,
  BACK_SCALE: 0.94,
  BACK_X: 16,
  BACK_Y: -14,
})

// 指针离边缘多近开始发光、光源跟随的缓动比例、停止补帧的像素阈值
export const BorderGlowMotion = Object.freeze({ EDGE_DISTANCE: 150, FOLLOW: 0.22, SETTLE: 0.5 })

export const AuthDecorVariant = Object.freeze({
  LOGIN: 'login',
  REGISTER: 'register',
  RESET: 'reset',
})

export const ShapeGridMotion = Object.freeze({
  SPEED: 0.08,
  CELL_SIZE: 56,
  BASE_FPS: 60,
  MAX_PIXEL_RATIO: 2,
  FRAME_SECONDS: 1 / 30,
  MAX_DELTA: 0.05,
})
