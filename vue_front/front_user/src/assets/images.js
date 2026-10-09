// 页面用到的图片统一从这里引入：构建时按这里写的宽度缩小并转成 WebP，源文件保持原图。
// 换图只要替换同名的 PNG，不用改引用它的页面。宽度按显示尺寸的两到三倍取，够高分屏用。
export { default as loginPoster } from './login-poster.png?format=webp&quality=82'
export { default as registerPoster } from './register-poster.png?format=webp&quality=82'
export { default as legalArtwork } from './legal-cirno-garden.png?format=webp&quality=80'
export { default as resetAvatar } from './forget_password_avatar.png?w=324&format=webp&quality=85'
export { default as brandIcon } from './meng_center_ico.png?w=84&format=webp&quality=90'
// 标签页图标要 PNG，由入口脚本写进页面
export { default as favicon } from './meng_center_ico.png?w=64&format=png'
