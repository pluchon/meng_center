import {
  stateBlockEmptyArt,
  stateBlockFailureArt,
  stateEmptyChartArt,
  stateEmptyChatArt,
  stateEmptyCollectionArt,
  stateEmptyCreateArt,
  stateEmptyGrowthArt,
  stateEmptyNotificationArt,
  stateEmptyRankingArt,
  stateEmptySearchArt,
  stateEmptySecurityArt,
  statePage401Art,
  statePage403Art,
  statePage404Art,
  statePage429Art,
  statePage500Art,
  statePageDraftFailedArt,
  statePagePostMissingArt,
  statePageMaintenanceArt,
  statePageNetworkArt,
} from '@/assets/images'

// 状态插画的名字到图片，页面只写名字
export const StateArt = Object.freeze({
  blockEmpty: stateBlockEmptyArt,
  blockFailure: stateBlockFailureArt,
  chart: stateEmptyChartArt,
  chat: stateEmptyChatArt,
  collection: stateEmptyCollectionArt,
  create: stateEmptyCreateArt,
  growth: stateEmptyGrowthArt,
  notification: stateEmptyNotificationArt,
  ranking: stateEmptyRankingArt,
  search: stateEmptySearchArt,
  security: stateEmptySecurityArt,
  unauthorized: statePage401Art,
  forbidden: statePage403Art,
  notFound: statePage404Art,
  postMissing: statePagePostMissingArt,
  draftFailed: statePageDraftFailedArt,
  tooMany: statePage429Art,
  serverError: statePage500Art,
  maintenance: statePageMaintenanceArt,
  network: statePageNetworkArt,
})

export const StateSize = Object.freeze({ SMALL: 'small', MEDIUM: 'medium', LARGE: 'large', TALL: 'tall', PAGE: 'page' })

// 按请求失败的类型挑插画：断网、无权限、请求太频繁、服务异常、维护，其余用通用失败图
export function failureArt(error) {
  const status = error?.status
  if (status === 0 || error?.code === 'NETWORK') return StateArt.network
  if (status === 403) return StateArt.forbidden
  if (status === 429) return StateArt.tooMany
  if (status === 503) return StateArt.maintenance
  if (status >= 500) return StateArt.serverError
  return StateArt.blockFailure
}
