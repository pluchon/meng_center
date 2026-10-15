// 萌岛：存档节奏、外观配色和点位种类

// 位置每隔多久存一次
export const AUTOSAVE_MS = 15000

// 点位种类，和后端的枚举同名
export const SpotKind = Object.freeze({ COIN: 'COIN', CHEST: 'CHEST', STAR: 'STAR' })

// 商品种类
export const ItemKind = Object.freeze({ HAT: 'HAT', OUTFIT: 'OUTFIT' })

// 衣服颜色：团子围巾的颜色，商店色块也用它
export const OUTFIT_COLORS = Object.freeze({
  'outfit-mint': '#8FCFAE',
  'outfit-pink': '#F2B3C6',
  'outfit-violet': '#C3B4EC',
  'outfit-sky': '#9FD6EA',
  'outfit-sun': '#F6D77A',
})

// 游戏页面的几个阶段
export const GamePhase = Object.freeze({
  TITLE: 'title',
  LOADING: 'loading',
  ERROR: 'error',
  PLAYING: 'playing',
})

// 互动提示的种类
export const Prompt = Object.freeze({ SHOP: 'shop', CHEST: 'chest', SHRINE: 'shrine', SHRINE_LIT: 'shrineLit' })
