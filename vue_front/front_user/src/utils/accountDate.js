const clockFormat = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function accountDate(value, t, includeTime = false) {
  if (!value) return t('account.never')
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return t('account.never')
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  const recent =
    date.toDateString() === today.toDateString()
      ? 'account.today'
      : date.toDateString() === yesterday.toDateString()
        ? 'account.yesterday'
        : null
  if (!recent)
    return (
      String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
    )
  return t(recent) + (includeTime ? ' ' + clockFormat.format(date) : '')
}
