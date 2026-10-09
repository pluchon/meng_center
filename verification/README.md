# 独立验证目录

版本探针、隔离联调、浏览器脚本和输出集中在这里。test-output/ 由 Git 忽略；生产模块中的 ArchUnit、加密及安全不变量测试长期保留。

## 验证范围

按仓库主人 2026-10-09 的要求，普通页面微调只运行受影响文件的静态检查、必要的前端构建及相关页面检查，不默认执行完整后端、版本探针或隔离联调。认证流程、数据库、接口、架构等重大变动再做完整验证。当前标题、头像、粒子与光标微调使用 `node ./verification/browser/visual-polish.mjs`，只读页面、不发送业务消息。

## 执行

在仓库根目录使用 PowerShell；后端先 package，前端先 npm ci。验证工具依赖在本目录安装：

```powershell
npm --prefix verification ci
./verification/Test-Middleware.ps1
./verification/Test-Backend.ps1
./verification/Test-Compatibility.ps1
./verification/Test-Functional.ps1
./verification/Test-NacosFailure.ps1
```

后端脚本自动查找 IDEA 自带 Maven，可用 -MavenPath 指定，不安装系统 Maven。兼容性探针使用 verification-* 配置和 verification_* 表，并在 finally 清理。

功能联调启动独立 user / gateway 实例于 18101 / 18081，使用独立临时 RabbitMQ vhost，本地 SMTP 接收器监听 25250。只对验证进程启用协议及覆盖 SMTP；普通业务进程保持原配置。测试只删除自己生成的随机用户名、会话、票据和已知键；IP 注册计数在测试前保存、结束后恢复。真实 QQ SMTP 只验证连接和认证，不发送真实消息。

Nacos 故障脚本短暂停止项目自己的 Nacos 容器，验证两个应用启动失败，finally 恢复中间件。它会影响正在使用该本地 Nacos 的业务进程，因此放在完整联调完成后执行。

## 浏览器验证

先启动普通 user / gateway，以及两个前端 dev server，再执行：

```powershell
./verification/Test-Browser.ps1
node ./verification/browser/ui-adjustments.mjs
node ./verification/browser/reset-password.mjs
node ./verification/browser/captcha-flow.mjs
```

captcha-flow.mjs 是滑块专项：用户端登录每次先弹滑块、拖错会换图重来、票据随登录请求提交、再次登录要重新过滑块。登录接口用模拟响应，不产生真实的失败计数；滑块生成与校验走真实服务。浏览器脚本共用 browser/captcha.mjs 求解滑块，拖动先快后慢、总时长超过轨迹检测的下限。直接用 node 运行时，先在同一个 PowerShell 里执行 `. ./deploy/scripts/Environment.ps1; Import-MengEnvironment` 载入 Redis 凭据。

使用系统 Microsoft Edge 的无头浏览器，不另装浏览器。Browser 插件当前不可用，采用 Playwright 回退。脚本拍摄参考稿与实现截图，检查本地字体/海报、地区选择、表单校验、真实滑块拖动、协议阅读、鉴权路由、管理员登录、后端分页和移动端溢出。自动化只在验证脚本里读取挑战答案，不把答案暴露给产品页面。

ui-adjustments.mjs 专门验证登录勾选的请求门槛、合并联系方式、100 字编辑器的粘贴与中文输入、8～20 位字母数字注册密码、勾选对齐、纯图标切换动画、动态连线与减少动态效果、鼠标排斥和断线、标题居中、半透明插画与长进度条，以及 1366×768、390×844、375×667 三种视口。登录勾选测试使用模拟待审核响应，只检查是否发出请求；其他页面连接真实本地服务。未登录恢复会话时的 401 和该模拟 403 是预期响应，其他控制台错误和运行时错误必须为零。

后续微调同时验证输入框内密码提示、邮箱占位样式、内嵌验证码按钮、找回密码入口跳转、按钮和协议标签动画。重连检查通过浏览器导入实际 particle-network.js，确认断线点靠近新的可达伙伴，同时保留阻断路径与静止模式约束；粒子画布采样仅在验证浏览器注入。

## 结果

当前证据与未完成项见 [STATUS.md](STATUS.md)。详细 JSON、日志和截图位于 test-output/；源码、工具依赖锁文件和复现脚本保留。SMTP 认证、本地邮件接收以及真实短信送达分别记账，不以本地接收器冒充外部实发。

重置专项脚本验证 640px 卡片、参考头像、唯一返回登录入口、三种视口、注册控件复用，以及模拟接口下的滑块、发码倒计时、密码不一致、失败重试和成功返回。算法检查使用真实模块，85 点重叠时每点至多 3 条连线，连通域至多 8 点；单独构造 8 点连通域验证停止重连并向外散开。

完整后端联调另外使用真实 MySQL / Redis / RabbitMQ 和本地 SMTP 收信，验证注册码不能重置密码、重置码消耗一次、两台设备旧会话失效、新密码可登录。测试账号、验证码、限流键和临时 vhost 均在结束时清理。

visual-polish.mjs 只检查重置页标题与新头像、发光渐细拖尾与空闲淡出、按钮可点击、粒子连线滞回与散开缓冲、减少动态效果、桌面/移动布局和切页后光标不重复。帧间隔采样属于本机浏览器观测，不能替代真实设备体验。

`node ./verification/browser/card-swap.mjs` 仅验证邮箱输入与普通输入的文字左侧留白、登录/注册上抽与退后动画、快照 inert/清理、共享粒子背景、快速浏览器导航取消、焦点恢复、减少动态效果和移动布局。
