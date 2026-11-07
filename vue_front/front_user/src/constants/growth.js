export const ExpSource = Object.freeze({
  CHECK_IN: 'CHECK_IN',
  POST: 'POST',
  COMMENT: 'COMMENT',
  LIKE: 'LIKE',
})
export const ExpSources = Object.freeze(Object.values(ExpSource))
// 明细每页条数、图表区间，以及今日任务每页几项
export const GrowthCenter = Object.freeze({ SIZE: 12, DAYS: [7, 30], TASK_SIZE: 4 })
export const Points = Object.freeze({ SIZE: 6, DAYS: [7, 30] })
export const GrowthTones = Object.freeze(['blue', 'mint', 'gold', 'pink', 'violet'])
export const WeekTones = Object.freeze(['blue', 'mint', 'gold', 'pink', 'violet', 'mint', 'pink'])
export const SourceTones = Object.freeze({
  CHECK_IN: 'blue',
  POST: 'gold',
  COMMENT: 'violet',
  LIKE: 'pink',
})
