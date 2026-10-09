# 验证

隔离联调、压测和浏览器脚本集中在这里。`test-output/` 放报告、日志和截图，由 Git 忽略。
各模块里的 ArchUnit、加密和安全不变量测试长期保留，随 Maven 一起跑。

## 什么时候跑什么

普通页面微调只跑受影响文件的静态检查、前端构建和相关的浏览器脚本。
认证流程、数据库、接口、架构这类改动，或者重构之后，再跑完整的后端测试和隔离联调。

## 准备

在仓库根目录用 PowerShell。后端先 `package`，前端先 `npm ci`，验证工具自己的依赖装在本目录：

```powershell
npm --prefix verification ci
```

后端脚本会自动找 IDEA 自带的 Maven，也可以用 `-MavenPath` 指定，不需要安装系统 Maven。

## 后端

| 命令 | 内容 |
|---|---|
| `./verification/Test-Middleware.ps1` | 四个中间件容器健康，用应用账号做真实读写 |
| `./verification/Test-Backend.ps1` | 全部模块的单元测试和架构测试 |
| `./verification/Test-Functional.ps1` | 隔离联调：注册、审核、登录、重新申请、改密、重置密码的完整流程 |
| `./verification/Test-Functional.ps1 -Check load` | 压测，见下文 |
| `./verification/Test-NacosFailure.ps1` | 关掉 Nacos 后两个服务必须启动失败 |
| `./verification/Test-Secrets.ps1` | 扫描仓库里有没有环境变量里的密钥字面值 |

隔离联调另起一套 user / gateway 实例（18101 / 18081），用临时的 RabbitMQ vhost 和监听 25250 的本地收信器，
不碰正在使用的 8080 / 8101。只对验证进程开启协议发布和替换 SMTP，业务进程的配置不变。
脚本只删自己造的随机账号、会话、票据和限流键；来源地址的注册计数在测试前保存、结束后恢复。
真实的 SMTP 只验证连接和认证，不发信。

Nacos 故障脚本会短暂停掉项目自己的 Nacos 容器，结束时恢复。它会影响正在用这个 Nacos 的业务进程，放在其他验证之后跑。

## 压测

```powershell
./verification/Test-Functional.ps1 -Check load
```

与隔离联调共用同一套实例。脚本先走一遍真实注册拿到服务端算出的密码哈希，再克隆出一批压测账号，结束后清理。
七个场景各自预热后按固定并发跑一段时间，报告每秒完成数和 50 / 95 / 99 分位耗时，写到 `test-output/load-*.json`。

可用环境变量调整：`MENG_LOAD_SECONDS`（每个场景的时长，默认 8）、`MENG_LOAD_USERS`（压测账号数，默认 40）、
`MENG_LOAD_LABEL`（报告文件名后缀）、`MENG_LOAD_ONLY`（只跑指定场景，逗号分隔场景名）。

2026-10-09 在开发机（16 线程，压测端与服务同机）上的结果，作为以后对比的基线：

| 场景 | 并发 | 每秒完成 | 中位耗时 | 95 分位 |
|---|---|---|---|---|
| 生成滑块 | 16 | 333 | 45 ms | 74 ms |
| 校验滑块 | 16 | 292 | 8 ms | 14 ms |
| 登录 | 16 | 154 | 98 ms | 125 ms |
| 当前用户 | 32 | 1835 | 16 ms | 30 ms |
| 审核列表 | 16 | 1099 | 13 ms | 23 ms |
| 登录全流程 | 8 | 69 | 114 ms | 135 ms |
| 发邮箱验证码 | 8 | 31 | 245 ms | 300 ms |

压测端和服务抢同一台机器的处理器，数字只用来做前后对比和找明显的慢点，不代表线上容量。
登录的瓶颈是密码哈希。「校验滑块」的每秒数受不计时的出题准备限制，看耗时即可。
发信场景对着本地收信器，测不出真实邮件服务商的耗时。

## 浏览器

先启动业务用的 user / gateway 和两个前端的开发服务，再执行：

```powershell
./verification/Test-Browser.ps1
node ./verification/browser/captcha-flow.mjs
node ./verification/browser/ui-adjustments.mjs
node ./verification/browser/reset-password.mjs
node ./verification/browser/visual-polish.mjs
node ./verification/browser/card-swap.mjs
node ./verification/browser/comet.mjs
```

直接用 `node` 运行时，先在同一个 PowerShell 里执行 `. ./deploy/scripts/Environment.ps1; Import-MengEnvironment` 载入凭据。
脚本用系统自带的 Microsoft Edge 无头模式，不另装浏览器。

| 脚本 | 内容 |
|---|---|
| `Test-Browser.ps1`（`browser/check.mjs`） | 回归：登录与注册页渲染、表单校验、真实拖动滑块、协议阅读、鉴权路由、管理员登录与分页、手机宽度不溢出 |
| `captcha-flow.mjs` | 滑块：每次登录先过滑块、拖错停留抖动再换图、票据随登录提交、再次登录重新验证 |
| `ui-adjustments.mjs` | 登录与注册页的交互细节：同意勾选的请求门槛、联系方式切换、申请理由编辑、密码规则、三种视口的布局 |
| `reset-password.mjs` | 重置密码页：布局、发码倒计时、密码不一致、失败重试、成功返回 |
| `visual-polish.mjs` | 重置页标题与头像、光标拖尾、粒子连线、减少动态效果 |
| `card-swap.mjs` | 登录与注册之间的卡片切换动画、焦点恢复 |
| `comet.mjs` | 彗星光标的形状、消散和不拦截点击 |

几点约定：

- 滑块由 `browser/captcha.mjs` 求解：答案只在验证脚本里从 Redis 读取，产品页面和接口从不暴露；拖动先快后慢，总时长超过轨迹检测的下限。
- 回归会在注册页用一个固定的日本号码真的请求一次短信码（通道未开通，返回 503），服务端照样记限流。
  结束时 `browser/limits.mjs` 把这一次记下的计数还回去，所以环境里需要有 `MENG_PII_HASH_KEY`。
- 未登录时恢复会话的 401 是预期响应，其他控制台错误和运行时错误必须为零。
- 只覆盖 Edge 的桌面和手机视口，没有做真机和其他浏览器的验收；帧率采样只代表本机。
