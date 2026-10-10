import { Feed } from '@/constants/content'
export function feedPages(current, total) {
  if (total <= Feed.PAGE_SLOTS) return Array.from({ length: total }, (_, i) => i + 1)
  if (current <= 4) return [1, 2, 3, 4, 5, null, total]
  if (current >= total - 3) return [1, null, total - 4, total - 3, total - 2, total - 1, total]
  return [1, null, current - 1, current, current + 1, null, total]
}
