# 验证

隔离联调、压测和浏览器脚本集中在这里。`test-output/` 放报告、日志和截图，由 Git 忽略。
各模块里的 ArchUnit、加密和安全不变量测试长期保留，随 Maven 一起跑。
创作中心脚本加 `-Production` 时验证 front_user 的现有构建产物，临时预览监听 5187；先运行用户端 `npm run build`。脚本结束后停止预览并删除它的日志，开发服务继续运行。
成长中心脚本也支持 `-Production`，使用临时预览 5188，验证图表分包和真实签到；结束后关闭预览，不修改业务跨站配置。
content 的字段转换测试覆盖 record 入参、全部映射字段和计数初值；重建批次测试验证 500 条分页不计总数且不受接口 50 条上限影响。

消息中心脚本需要 8080 网关、8101 user、8102 content、8103 growth、8104 im 和 5173 用户端；支持 `-Production` 使用临时预览 5189。报告和截图在 `test-output/im-center/`，结束时关闭浏览器及预览，清理随机测试账号、私信图片、数据库和搜索派生数据。

`functional/im-fixture-cleanup.mjs` 是主页、详情、编辑、搜索、创作与成长脚本共用的账号清理辅助模块，删除账号前清理其消息域数据；尚未初始化消息库时跳过。发布包预览只在验证进程的代理里使用开发 Origin，不修改业务允许来源。

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
| `./verification/Test-Middleware.ps1` | 六个中间件容器健康与内部转发器运行，用应用账号做真实请求及权限隔离验证 |
| `./verification/Test-Backend.ps1` | 全部模块的单元测试和架构测试 |
| `./verification/Test-Functional.ps1` | 隔离联调：注册、审核、登录、重新申请、改密、重置密码的完整流程 |
| `./verification/Test-Functional.ps1 -Check load` | 压测，见下文 |
| `./verification/Test-NacosFailure.ps1` | 关掉 Nacos 后两个服务必须启动失败 |
| `./verification/Test-HomeGrowth.ps1` | 运行中的主页和成长服务联调、事件幂等与浏览器交互，结束后清理随机数据 |
| `./verification/Test-GrowthCenter.ps1` | 成长中心四项只读接口、鉴权与账号隔离、严格参数校验、连续签到、任务累计天数、7/30 天补零与来源合计、明细筛选分页、真实签到与各页导航同步、月份缓存、四至六周月份固定高度、可视化弹窗与关闭焦点、图表按需加载、独立重试、桌面手机与隐藏滚动条、24 并发读取；结束后清理随机账号、成长记录与会话，截图留在忽略目录供对照 |
| `./verification/Test-PostEditor.ps1` | 运行中的发帖服务与浏览器联调：上传、草稿权限与配额、标签、字数、安全预览、保存恢复、发布及成长事件；结束后清理随机数据 |
| `./verification/Test-PostDetail.ps1` | 运行中的详情服务与浏览器联调：浏览判重、评论与回复分页、配图权限、表情与配图合计上限、已选附件预览、首屏并行请求与评论独立重试、输入区按需挂载、16 并发详情与评论混合读取、点赞、举报去重、删除计数、评论置顶与 @ 提到（候选范围、去重、通知）、改帖及成长事件；结束后清理随机账号和数据 |
| `./verification/Test-Creator.ps1` | 创作中心鉴权与账号隔离、累计数据、7/30 天补零、台北业务日期、浏览和互动幂等、历史互动取消、评论删除保留日统计、封面与草稿配图、全时期排行、独立重试、翻页删除回退、草稿恢复、图表按需加载、首页与创作中心导航宽度一致、时间切换保留图表、WebGPU 背景实际呈现与像素比、离页清理、桌面手机与减少动态效果、24 并发读取；结束后清理随机账号、内容与日统计 |
| `./verification/Test-SearchHot.ps1` | 搜索鉴权、参数边界、标题正文标签命中、安全高亮、分类翻页、编辑删除同步、收藏热度幂等、过期帖子排除、蓝绿重建、ES 权限隔离、限流、并发延迟、共享翻页条、导航搜索悬浮与聚焦保留、隐藏滚动条、极光渲染帧率与资源释放及桌面手机交互；仅清理随机测试数据 |
| `./verification/Test-ContentJobs.ps1` | 两项任务配置、执行器鉴权、注册及手动触发回调、非法令牌启动失败；可加 `-CheckSchedulerFailure` 验证调度中心停机不阻塞服务启动 |
| `./verification/Test-ImCenter.ps1` | 多账号真实私信、陌生人限制、图片权限与图集、并发幂等、已读回执、屏蔽、删除游标、撤回（5 分钟时限与临界、对方看不到内容、未读和摘要同步）、在线隐身、多标签同步；真实内容通知、聚合去重、分组已读、跨页评论定位、四个标签页、失败重试、桌面手机与减少动态效果、断线补拉及 24 并发读取；清理自己的账号、配图与数据 |
| `./verification/Test-SocialPoints.ps1` | 关注与粉丝（幂等、不能关注自己、计数精确、列表关系标记与翻页）、关注通知每对只一条、收藏夹默认私密与公开私密切换对别人主页的影响、别人主页的帖子和数量、积分的签到发帖评论获得、每件事只算一次、明细筛选与翻页、余额和规则；结束后清理随机账号和数据 |
| `./verification/Test-Secrets.ps1` | 扫描仓库里有没有环境变量里的密钥字面值 |
| `node verification/Test-CompanionStream.mjs` | 萌萌前端 SSE：中文按字节分块、CRLF、多行 data、心跳、业务错误、异常断流和异步气泡顺序；不调用模型 |
| `./verification/Test-Companion.ps1` | 萌萌 Java 好感、隐私及架构不变量，Python 危机、拦截、限次、输出重写、工具白名单和预算失败分支；加 `-EvaluateModels` 使用真实模型执行 30 条普通提问与 33 条对抗样本，`-CompareModel qwen-plus-character` 增加匿名台词评分，`-Baseline <JSON>` 比较新旧角色卡。真实评测消耗 DashScope 额度，虚构语料和结果只写入 `test-output/` |
| `./verification/Test-Ai.ps1` | Python 锁文件安装、Ruff、离线假模型图分支和 MQ 确认/重投/预算延迟测试；`-EvaluateModels` 调用真实模型跑审核与主题评测，`-Baseline <JSON文件>` 阻止违规召回率或误判率回退；`-Integration` 对运行中的网关、user/content/growth/im/ai 做真实辅助接口、SQL 数字与缓存、发帖到 MQ/向量索引、语义搜索、推荐分页与反馈、资料审核、旧结果与幂等联调，结束后清理随机账号和派生数据；加 `-Covers` 验证真实生图与上传。真实评测和联调会使用 DashScope 额度，结果只在本地终端 |

隔离联调另起一套 user / gateway 实例（18101 / 18081），用临时的 RabbitMQ vhost 和监听 25250 的本地收信器，
不碰正在使用的 8080 / 8101。只对验证进程开启协议发布和替换 SMTP，业务进程的配置不变。
隔离 user 显式关闭 `meng.user.profile-moderation.enabled`，不注册业务的 9101 执行器，也不扫描共享资料审核发件箱；正常业务实例默认开启。
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

## 主页与成长

```powershell
./verification/Test-HomeGrowth.ps1
```

运行前打包并启动 8080 网关、8101 user、8102 content、8103 growth，以及 5173 用户端。脚本为 `functional/home-growth.mjs`，真实请求网关、数据库、Redis、RabbitMQ，并用 Edge 检查主页。

覆盖：未登录拒绝、老用户按需账户、并发签到、伪图片及超大图片拒绝、四种图片格式与缩略图、发帖参数及配图归属、真实分页分类、热门时间窗口、重复喜欢收藏与账号隔离、发帖和评论经验上限、重复事件、等级同步、桌面手机布局、筛选刷新和后退。固定时钟的零点、周一及跨多级奖励由 `GrowthServiceTest` 验证；不验收第二步的详情、浏览、评论接口与编辑页面。

脚本只建立随机前缀测试账号，使用现有管理员密码哈希作为测试登录凭据，登录消耗仅供验证的 Redis 票据；不发送邮件或短信。结束清理这些账号的帖子、配图、经验、签到、会话与限流键。报告和截图放 `test-output/home-growth/`，不作为仓库文档保留。

## 热榜与搜索

先按部署文档初始化 Elasticsearch，再启动四个业务服务和用户前端。运行 `./verification/Test-SearchHot.ps1`；使用随机管理员和普通账号，结束时清理自己创建的 MySQL 数据、搜索文档和 Redis 成员。测试会触发两项蓝绿重建。加 `-CheckElasticsearchFailure` 时会短暂停止项目自己的 ES 容器，验证健康检查、发帖与标题搜索降级，再恢复容器并重建索引；不停止业务服务。结果、接口 p50/p95 和浏览器滚动帧记录写在 `test-output/search/`，只作本机基线。

`Test-Middleware.ps1` 验证 ES 应用账号可以读帖子别名，访问其他索引得到 403。`SearchHighlightTest` 覆盖 Unicode、HTML 原文、不完整高亮标记、同分 ID 排序与分页边界。故障降级由停用 ES 的实测或模拟客户端异常的服务测试验证。

## 内容定时任务

运行 `./verification/Test-ContentJobs.ps1`，检查两项任务的配置、执行器注册地址、匿名调用拒绝、调度中心管理员登录、手动触发及成功回调。另启动隔离内容实例，分别确认空令牌和官方默认令牌导致启动失败；校验环境变量不会出现在报告中。添加 `-CheckSchedulerFailure` 会短暂停止调度中心，验证隔离内容服务仍能启动、现有内容服务保持健康，随后恢复调度中心及内部转发器。隔离端口为 18102/19102，用后停止，日志和结果仅保存在 `test-output/content-jobs/`。`ContentJobConfigurationTest` 同时覆盖令牌校验边界。
