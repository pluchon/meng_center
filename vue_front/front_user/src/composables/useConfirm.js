import { ElMessageBox } from 'element-plus'
// 统一的确认框：样式见 styles/dialog.scss；危险操作（删除等）传 danger，用粉色
export async function confirmDialog({ message, title, confirmText, cancelText, danger = false }) {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: confirmText,
      cancelButtonText: cancelText,
      customClass: danger ? 'meng-confirm meng-confirm-danger' : 'meng-confirm',
      modalClass: 'meng-dialog-overlay',
      closeOnClickModal: false,
      showClose: true,
      center: false,
    })
    return true
  } catch {
    return false
  }
}
