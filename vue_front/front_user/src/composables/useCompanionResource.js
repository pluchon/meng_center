import { toRefs } from 'vue'
import { useRequestResource } from './useRequestResource'

// 在萌萌的独立异步区块中复用请求取消和过期响应隔离。
export function useCompanionResource(action, initial = null) {
  let parameters = []
  const resource = useRequestResource((signal) => action(...parameters, signal), initial)
  return {
    ...toRefs(resource.state),
    async execute(...values) {
      parameters = values
      return (await resource.load()) ? resource.state.data : null
    },
    clear: resource.clear,
  }
}
