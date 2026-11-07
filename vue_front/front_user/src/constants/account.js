export const Account = Object.freeze({
  SIZE: 8,
  // 个人中心帖子列表：量出实际行高前先按这个算，一页最多 20 条（后端上限）
  ROW_HEIGHT: 76,
  ROW_GAP: 14,
  PAGE_MAX: 20,
  FOLDERS_MAX: 20,
  FOLLOW_SIZE: 6,
  PICKER_SIZE: 6,
  POST_FOLDERS_MAX: 10,
  AVATAR_BYTES: 2 * 1024 * 1024,
  // 头像裁剪框的边长和导出的边长
  AVATAR_FRAME: 300,
  AVATAR_OUTPUT: 512,
  NAME_MAX: 12,
  BIO_MAX: 50,
  // 收藏夹封面的宽高比，和收藏夹卡片、新建弹窗里的封面框一致
  COVER_ASPECT: 240 / 112,
})
// 收藏夹封面的二十张预设图，覆盖常见的收藏夹用途；图还没画好，src 为空时显示占位卡片。
// 画好后放进 assets 并从 assets/images.js 引入，填到对应的 src
export const FolderCoverPresets = Object.freeze(
  [
    'anime',
    'game',
    'guide',
    'illust',
    'wallpaper',
    'study',
    'tech',
    'food',
    'travel',
    'daily',
    'music',
    'cosplay',
    'figure',
    'inspiration',
    'meme',
    'writing',
    'photo',
    'pet',
    'later',
    'quotes',
  ].map((key) => Object.freeze({ key, src: null })),
)
// 头像的二十张预设图：角色、小动物和小物件；还没画好，src 为空时显示占位卡片
export const AvatarPresets = Object.freeze(
  [
    'cirnoSmile',
    'cirnoWink',
    'naitangSmile',
    'naitangSleepy',
    'whiteCat',
    'orangeCat',
    'bunny',
    'shiba',
    'penguin',
    'frog',
    'star',
    'cloud',
    'moon',
    'sakura',
    'iceCream',
    'strawberry',
    'controller',
    'headphones',
    'book',
    'crystal',
  ].map((key) => Object.freeze({ key, src: null })),
)
// 收藏夹可选的主题色，和后端 FolderColor 一一对应
export const FolderColors = Object.freeze(['GOLD', 'BLUE', 'MINT', 'PINK', 'VIOLET'])
export const AccountPane = Object.freeze({
  FOLDERS: 'folders',
  LIKES: 'likes',
  SETTINGS: 'settings',
  // 别人的主页：他发布的帖子
  POSTS: 'posts',
})
// 收藏夹对别人是否可见，和后端 FolderVisibility 一一对应
export const FolderVisibility = Object.freeze({ PUBLIC: 'PUBLIC', PRIVATE: 'PRIVATE' })
