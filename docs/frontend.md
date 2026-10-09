# 萌部落社区 · 前端规范

> 2026-10-08 首版。配套文档：[架构设计](architecture.md)、[认证模块](auth.md)。
> 原则：不硬编码、不犯低级错误，靠目录约定和检查工具自动拦，不靠自觉。

## 1. 项目划分

`vue_front/` 下两个独立项目，各自有 `package.json`，不设共享包。

| 项目 | 用途 | 样式 |
|---|---|---|
| `front_user` | 用户端 | 水色萌系主题，亮色和暗色都做 |
| `front_admin` | 管理端 | Element Plus 默认样式，不做主题 |

两个项目遵守同一套规范，差异见第 13 节。

## 2. 技术栈

| 项 | 选型 |
|---|---|
| 运行环境 | Node 22，包管理用 npm |
| 构建 | Vite |
| 框架 | Vue 3，JavaScript（不用 TypeScript） |
| 路由 | vue-router |
| 状态 | Pinia |
| 请求 | Axios |
| 组件库 | Element Plus，全量引入 |
| 文案 | vue-i18n，当前只有简体中文 |
| 样式 | SCSS |
| 图片 | vite-imagetools，构建时缩放并转 WebP（目前只有用户端用到） |
| 检查 | ESLint、Stylelint、Prettier |

具体版本在搭骨架时锁定。

## 3. 目录结构

```
src/
├── api/            每个后端域一个文件；全项目只有这里能发请求
│   ├── request.js  唯一的 Axios 实例和拦截器
│   └── user.js
├── router/
│   ├── index.js    创建路由、注册守卫
│   ├── routes.js   路由表
│   ├── names.js    路由名常量
│   └── guards.js   登录守卫、页面标题
├── stores/         Pinia，只放跨页面的状态
├── constants/      枚举、存储键、分页默认值、超时等
├── locales/        所有界面文案
│   ├── index.js
│   └── zh-CN/      按域分文件：common.js、auth.js、error.js …
├── styles/
│   ├── tokens.scss   设计变量（颜色、圆角、间距、阴影），唯一允许写色值的地方
│   ├── element.scss  Element Plus 变量覆盖和弹层样式
│   ├── _mixins.scss  断点等 SCSS 工具
│   └── global.scss   全局基础样式
├── components/     通用组件
├── composables/    可复用逻辑
├── layouts/        页面骨架
├── views/          页面，按域分目录
├── App.vue
└── main.js
```

## 4. 三文件规范

每个页面和组件占一个目录，三个文件同名：

```
views/auth/login/
├── LoginView.vue    只放模板
├── LoginView.js     逻辑
└── LoginView.scss   样式
```

`LoginView.vue`：

```vue
<template>
  <div class="login-view">
    <el-button type="primary" :loading="submitting" @click="submit">
      {{ t('auth.login.submit') }}
    </el-button>
  </div>
</template>

<script src="./LoginView.js"></script>
<style lang="scss" scoped src="./LoginView.scss"></style>
```

`LoginView.js`：

```js
import { defineComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

export default defineComponent({
  name: 'LoginView',
  setup() {
    const { t } = useI18n()
    const userStore = useUserStore()
    const submitting = ref(false)

    async function submit() {
      submitting.value = true
      try {
        await userStore.login()
      } finally {
        submitting.value = false
      }
    }

    return { t, submitting, submit }
  },
})
```

- `.vue` 里不写内联的 `<script>` 和 `<style>` 内容，也不用 `<script setup>`（它不支持外部文件）。
- 模板用到的变量和方法必须在 `setup()` 里返回。
- 命名：页面以 `View` 结尾，布局以 `Layout` 结尾，通用组件用两个以上单词的大驼峰。

## 5. 不硬编码

| 类别 | 放哪 | 页面里怎么用 |
|---|---|---|
| 接口地址 | `api/` | 调函数，不写 URL |
| 路由 | `router/names.js` | 按名字跳转，不拼路径 |
| 状态、角色等枚举 | `constants/` | 用常量比较，不写数字或字符串 |
| 界面文案 | `locales/` | 用键取文案 |
| 颜色、圆角、间距、阴影 | `styles/tokens.scss` | 只用 `var(--meng-*)` |
| 环境相关的值 | `.env.*` 文件 | 代码里不出现域名和端口 |
| 存储键、分页大小、超时 | `constants/` | 引用常量 |

### 5.1 枚举

与后端枚举一一对应，取值以后端契约为准。显示文字不写在常量里，通过文案键取。

```js
// constants/user.js
export const UserStatus = Object.freeze({
  PENDING: 'PENDING',
  NORMAL: 'NORMAL',
  REJECTED: 'REJECTED',
})

export const UserStatusLabelKey = Object.freeze({
  [UserStatus.PENDING]: 'user.status.pending',
  [UserStatus.NORMAL]: 'user.status.normal',
  [UserStatus.REJECTED]: 'user.status.rejected',
})
```

上面的取值是示意，后端枚举确定后同步。

### 5.2 文案

- 所有用户能看到的文字都进 `locales/`，包括按钮、标题、占位符、提示、确认框、校验信息。
- 键名按「域.页面或模块.含义」，例如 `auth.login.submit`。
- 组件里用 `useI18n()` 的 `t`；组件之外（如请求拦截器）用 `locales/index.js` 导出的实例。
- Element Plus 自带文案通过它的语言包设置，与当前语言保持一致。
- 后端错误：优先按业务码取 `error.{业务码}` 的文案，没有则用后端返回的消息，再没有用通用提示。
- 标题用短名词短语，解释性的句子放描述文字或提示气泡。

## 6. 请求层

- 只有 `api/request.js` 创建 Axios 实例；页面和 store 只调 `api/` 里的函数。
- `baseURL` 来自环境变量 `VITE_API_BASE`，超时来自常量。
- Token 放 HttpOnly Cookie。请求带 Cookie（`withCredentials`），前端不读也不存 Token。
- 响应拦截器把后端统一响应解包，`api/` 里的函数直接返回数据本身。
- GET 的参数放 `params`；POST、PUT 的请求体结构与后端 DTO 完全一致，不多套一层对象。

按 HTTP 状态统一处理，页面里不重复写：

| 状态 | 处理 |
|---|---|
| 401 | 清空登录态，跳登录页并带上回跳地址；并发的多个 401 只处理一次 |
| 403 | 统一提示无权限；登录接口的「审核中」「已拒绝」由登录页按业务码处理 |
| 429 | 统一提示操作过于频繁 |
| 5xx、网络错误 | 统一提示服务异常 |
| 其他 4xx | 整理成 `{ status, code, message }` 抛给调用方，由页面决定怎么展示 |

## 7. 路由

- 路由表里的组件一律懒加载。
- 每条路由有 `name`，取自 `names.js`；跳转用名字。
- `meta.requiresAuth` 标记需要登录的页面；`meta.titleKey` 是页面标题的文案键。
- 全局守卫：需要登录而未登录时跳登录页；按 `titleKey` 设置浏览器标题。
- 需要登录的操作（点赞、评论、发私信），在点击入口处先检查登录态，未登录就引导登录，不等后端返回 401。

## 8. 状态管理

- 用选项式写法定义 store：`state`、`getters`、`actions`。
- 页面只读 `state` 和 `getters`，修改一律通过 `actions`。
- 只放跨页面的状态，页面自己的状态留在页面里。
- 不把用户信息存进浏览器本地存储。应用启动和刷新时调 `/user/me` 恢复登录态。

初始的 store：

| store | 内容 |
|---|---|
| `user` | 当前用户、是否登录、登录、登出、拉取当前用户 |
| `app` | 主题（亮 / 暗）、语言 |

## 9. 异步页面的四种状态

每个有异步数据的页面都要处理：加载中、出错、未登录、空数据。

- `composables/useAsyncState.js`：统一提供 `loading`、`error`、`data` 和执行函数。
- `components/` 下一组状态组件：加载中、出错（带重试）、空数据、未登录（带登录入口）。
- 破坏性操作（删除等）先用 `ElMessageBox.confirm` 确认。
- 提交中的按钮置为加载或禁用，防止重复提交。

## 10. 样式

- 一律 SCSS，`scoped`；用 `@use`，不用 `@import`。
- 颜色、圆角、间距、阴影全部定义成 CSS 变量，前缀 `--meng-`，写在 `styles/tokens.scss`。
  亮色定义在 `:root`，暗色定义在 `html.dark`。
- 组件样式里不出现十六进制色值、`rgb()`、`hsl()` 和颜色名，只用变量。
- `_mixins.scss` 通过 Vite 配置注入到每个组件样式，放断点等工具。
- 亮色的色值、圆角和投影已定，见 [kickoff.md](kickoff.md) 第 8 节；暗色未定。定下后只改 `tokens.scss`。

Element Plus 相关：

- 主色要把整组衍生色一起覆盖（`--el-color-primary` 及其 `light-3 / 5 / 7 / 8 / 9`、`dark-2`），
  只改基准色的话悬停和聚焦时会闪出默认的天蓝色。
- 下拉框、气泡等弹层渲染在 `body` 下，组件内的 `scoped` 样式够不着。
  要改就给组件指定 `popper-class`，样式写在 `styles/element.scss`。
- 文本域的字数统计有写死的白色背景，在全局样式里改成透明。

暗色模式验收矩阵：页面、弹窗、悬停态、禁用控件、双方消息气泡、媒体加载失败态、分页器、他人视角。
媒体加载失败要有兜底图标，前景色和背景色跟随主题。

图片：

- 页面用到的位图统一从 `src/assets/images.js` 引入，不在组件里直接写图片路径。
  该文件在引用地址上写明宽度、格式和质量，`vite-imagetools` 在构建和开发时按它缩小并转成 WebP。
- 源文件保留原图。换图只替换同名的 PNG，页面不用改。
- 宽度按显示尺寸的两到三倍取。原图动辄一两 MB，不经转换不能直接进首屏。
- `index.html` 里的地址不经过图片转换，标签页图标由入口脚本写入。

## 11. 检查工具

| 工具 | 关键规则 |
|---|---|
| ESLint | Vue 官方规则；`no-console`、`no-debugger` 报错；国际化插件的「模板里不许出现写死的文字」报错 |
| Stylelint | SCSS 标准规则；禁止十六进制色值、颜色名和颜色函数，仅 `styles/tokens.scss` 例外 |

模板排版归 ESLint 管，Prettier 只管脚本和样式：`npm run format` 先用 Prettier 处理 `.js` 和 `.scss`，再跑 `eslint --fix`。
两者对 `.vue` 模板的排法不一样，让 Prettier 碰模板会把 ESLint 的警告改回来。
| Prettier | 统一格式 |

`package.json` 脚本：`dev`、`build`、`lint`、`lint:style`。

完成的标准：`npm run lint`、`npm run lint:style`、`npm run build` 全部零错误零警告，然后在浏览器里验证渲染、交互、路由和主题。

## 12. 开发代理与部署

- 前端所有请求走同一个前缀 `/api`。
- 开发时 Vite 代理把 `/api` 去掉前缀后转发到网关，网关地址来自 `.env.development`；WebSocket 代理一并开启。
- 上线时前端和网关同域名，由反向代理做同样的转发，不跨域。
- 改代理规则前，对照后端 Controller 前缀、网关路由、前端 `api/` 里的路径逐项核对。

## 13. 管理端的差异

- 不做主题，不做暗色模式，用 Element Plus 默认样式。
- 所有页面都需要登录，且必须是管理员角色；登录复用 `POST /user/sessions`。
- 第一批页面：登录、注册申请列表（通过 / 拒绝）。
- 其余规范与用户端完全相同，包括文案进 `locales/` 和三文件拆分。

## 14. 禁止清单

- 提交含 `console.log` 的代码。
- 页面或组件直接用 Axios，或在 `api/` 之外写接口地址。
- 页面直接改 store 的 `state`。
- 模板或脚本里写死界面文字。
- 组件样式里写色值。
- `.vue` 里内联脚本或样式，或使用 `<script setup>`。
- 用 `v-html` 渲染未经过滤的内容。
- SCSS 里用 `@import`。
- 用前端切片冒充分页。分页、排序、总数由后端负责。

## 15. 待定

| 项 | 说明 |
|---|---|
| 暗色主题色值 | 未定；第一阶段的登录页、注册页只做亮色 |
| 帖子编辑器和内容过滤方案 | 聊内容域时确定；用户内容必须先过滤再显示 |
| 繁体中文语言包 | 暂缓；结构已支持，加一个语言目录即可 |
