import { ElMessage } from 'element-plus'

// 落不到具体输入框上的失败统一用顶部提示，不往表单里插一行把按钮和卡片顶开。
// 请求层已经提示过的（未登录、限流、服务异常、网络不通）和主动取消的不重复弹
export function notifyFailure(error) {
  if (!error.notified && !error.cancelled) ElMessage.error(error.message)
}
