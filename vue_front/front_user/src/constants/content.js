export const PostCategory = Object.freeze({
  ANIME: 'ANIME',
  CREATION: 'CREATION',
  GAME: 'GAME',
  TECH: 'TECH',
  DAILY: 'DAILY',
})
export const Categories = Object.freeze(Object.values(PostCategory))
export const PostSort = Object.freeze({ LATEST: 'LATEST', HOT: 'HOT' })
export const Feed = Object.freeze({ SIZE: 10, PAGE_SLOTS: 7 })
export const PostEditor = Object.freeze({
  TITLE_MAX: 50,
  BODY_MAX: 10000,
  IMAGE_MAX: 10,
  TAG_MAX: 5,
  TAG_LENGTH: 12,
  IMAGE_BYTES: 5 * 1024 * 1024,
  IMAGE_EDGE: 2560,
  UPLOAD_CONCURRENCY: 3,
  TAG_DEBOUNCE: 300,
})
export const MarkdownFormats = Object.freeze([
  { key: 'bold', before: '**', after: '**' },
  { key: 'italic', before: '*', after: '*' },
  { key: 'heading', before: '## ', line: true },
  { key: 'quote', before: '> ', line: true },
  { key: 'list', before: '- ', line: true },
  { key: 'code', before: '`', after: '`' },
  { key: 'link', before: '[', after: '](https://)' },
])
export const Growth = Object.freeze({ REFRESH_MS: 60000 })
export const RelativeTime = Object.freeze({ MINUTE_MS: 60000, HOUR_MINUTES: 60, DAY_HOURS: 24 })
