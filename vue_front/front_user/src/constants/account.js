export const Account = Object.freeze({
  SIZE: 8,
  FOLDERS_MAX: 20,
  PICKER_SIZE: 6,
  POST_FOLDERS_MAX: 10,
  AVATAR_BYTES: 2 * 1024 * 1024,
  NAME_MAX: 12,
  BIO_MAX: 50,
})
// 收藏夹可选的主题色，和后端 FolderColor 一一对应
export const FolderColors = Object.freeze(['GOLD', 'BLUE', 'MINT', 'PINK', 'VIOLET'])
export const AccountPane = Object.freeze({
  FOLDERS: 'folders',
  LIKES: 'likes',
  SETTINGS: 'settings',
})
