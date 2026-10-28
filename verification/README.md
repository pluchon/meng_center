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
只跑和这次改动相关的脚本和测试。同一份代码已经跑过并通过的，不再重跑。调用真实模型的评测（`-SelectMouthModels`、`-EvaluateDecisions`、`-EvaluateModels`）必须遵守 [docs/ai-runtime.md](../docs/ai-runtime.md) 第 11.2 节的硬性约束：开发中只跑限额冒烟，完整评测只在仓库主人确认验收后跑一次。

## 准备

在仓库根目录用 PowerShell。后端先 `package`，前端先 `npm ci`，验证工具自己的依赖装在本目录：

```powershell
npm --prefix verification ci
```

后端脚本会自动找 IDEA 自带的 Maven，也可以用 `-MavenPath` 指定，不需要安装系统 Maven。后端与奶糖助手测试默认限制 Maven 堆内存为 384 MB、单个测试进程为 256 MB，测试只使用一个子进程；不要同时运行构建、服务批量启动和浏览器回归。真实模型评测始终串行执行。

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
| `./verification/Test-ContentJobs.ps1` | 热榜重建、搜索重建和 AI 失败重审三项任务配置、执行器鉴权、注册及手动触发回调、非法令牌启动失败；可加 `-CheckSchedulerFailure` 验证调度中心停机不阻塞服务启动 |
| `./verification/Test-ImCenter.ps1` | 多账号真实私信、陌生人限制、图片权限与图集、表情名称搜索、并发幂等、已读回执、屏蔽、删除游标、撤回（5 分钟时限与临界、对方看不到内容、未读和摘要同步）、在线隐身、多标签同步；真实内容通知、聚合去重、成长/举报/审核/关注四类系统分组、分组已读、跨页评论定位、四个标签页、失败重试、桌面手机与减少动态效果、断线补拉及 24 并发读取；清理自己的账号、配图与数据 |
| `./verification/Test-SocialPoints.ps1` | 关注与粉丝（幂等、不能关注自己、计数精确、列表关系标记与翻页）、关注通知每对只一条、收藏夹默认私密与公开私密切换对别人主页的影响、别人主页的帖子和数量、积分的签到发帖评论获得、每件事只算一次、明细筛选与翻页、余额和规则；结束后清理随机账号和数据 |
| `./verification/Test-Secrets.ps1` | 扫描仓库里有没有环境变量里的密钥字面值 |
| `node verification/Test-CompanionStream.mjs` | 奶糖助手前端 SSE：中文按字节分块、CRLF、多行 data、心跳、业务错误、异常断流和异步气泡顺序；不调用模型 |
| `node verification/Test-CompanionComposer.mjs` | 执行真实组件的 Vue 响应式交互，检查发送失败恢复文字与图片、保留新草稿、复用原消息编号和防止重复重试；不启动浏览器或调用模型 |
| `node verification/Test-AiRuntimeDashboard.mjs` | 离线检查每日看板的日期、token、比率、无样本状态和隐私边界 |
| `./verification/Export-AiRuntimeDashboard.ps1 -EndDay yyyy-MM-dd -Days 7 -Output <路径.html>` | 只读导出按 UTC 自然日汇总的奶糖运行时看板；默认输出在 `test-output/`，不覆盖已有文件，不调用模型 |
| `node verification/Test-VectorEncoding.mjs` | 离线检查索引状态判断，防止仅凭 Redis 标记把旧编码误判为迁移完成 |
| `./verification/Test-VectorEncoding.ps1` | 只读比较公开帖子、当前向量索引的覆盖范围与版本、维度和重建进度；不触发重算或切换别名 |
| `./verification/Test-CompanionDatabase.ps1` | 在随机独立 MySQL 库验证奶糖助手迁移、旧数据隔离、删除旧表列、来源与窗口初始化、消息幂等键、outbox 唯一键和未发送图片的数量及过期筛选；结束删除验证库，不改业务库 |
| `./verification/Test-CompanionDeployment.ps1` | 只读核对运行实例与构建时间、健康状态、Flyway、当前角色卡及台词覆盖、两个定时任务和 9107 执行器注册；发现旧进程或未部署迁移时失败。离线开发已暂停记忆维护时显式使用 `-AllowPausedMemoryMaintenance`，其他约束保持不变 |
| `./verification/Test-Companion.ps1 -SelectMouthModels -Smoke -Character NEKO -EvaluationTokenBudget <不超过30000> -EvaluationBudgetId <固定授权编号> -EvaluationPhaseId <本期固定编号> -EvaluationOutput <JSON路径>` | 开发期真实接通检查：只用当前默认说话模型，奶糖五轮，不盲评，不生成质量或选型结论；本期最多三次，到额即停。默认说话模型关闭降级链；工具使用录制结果，不调用业务接口或真实生图，第五轮附带固定小图检查官方图片输入 |
| `./verification/Test-Companion.ps1 -SelectMouthModels -Acceptance -EvaluationTokenBudget <仓库主人给定额度> -EvaluationBudgetId <固定授权编号> -EvaluationPhaseId <本期固定编号> -EvaluationOutput <JSON路径>` | 仅在仓库主人明确要求验收后执行，本期一次；默认只评当前模型，奶糖35轮，输入来自固定匿名论坛语料；记录语料哈希和逐条来源。显式 `-CompareModel deepseek-v4-pro` 才增加候选；固定种子抽20组双裁判评分。有对照回答时交换顺序，无对照时只作绝对评分。`-Baseline <仓库外报告>` 与 `-ChangedVariable <唯一变量>` 同时指定，旧报告不重新生成 |
| `./verification/Test-Companion.ps1 -EvaluateDecisions -Acceptance -EvaluationTokenBudget <额度> -EvaluationBudgetId <固定授权编号> -EvaluationPhaseId <本期固定编号>` | 真实主 agent 配合虚构工具资料，检查站内工具、隐私、回忆、休息、越权、危机和孤独倾诉标签；另检查显式记忆与查询、操作请求的区别；按阶段限制验收次数，每个用例后保存用量与检查点，失败即停止，不访问真实网页或业务数据 |
| `./verification/Invoke-ModelEvaluation.ps1 -Task <station/companion/decisions> -TokenBudget <额度> -BudgetId <固定授权编号>` | 已安装依赖时单独执行对应真实评测，`-EvaluationArguments` 必须传入 `--smoke` 或经授权的 `--acceptance`，每种评测还必须传入固定 `--phase-id`；同机只允许一个受保护评测，可用物理内存低于 3 GB 或内存提交比例超过 85% 时停止本次进程；`-EvaluationArguments` 传入对应 Python 模块参数。回复生成条件相同且尚无盲评分数时，可补充盲评协议版本后继续；已有分数时拒绝混用协议 |
| `./verification/Test-Companion.ps1` | 奶糖助手 Java 隐私、事务恢复、纪元快照、图片限额及回滚与架构规则；Python 输入标签、输出重写、控制行解析、出处校验、私人图片鉴权、空闲压缩、任务参数实际传递与原生 rerank。真实评测参数沿用上一行的独立额度要求，`-CompareModel deepseek-v4-pro` 指定对照，`-Baseline <JSON>` 比较结果；语料与结果只写入 `test-output/` |
| `./verification/Test-Ai.ps1` | Python 锁文件安装、Ruff、离线假模型分支、原生向量与 rerank、MQ 确认与有限重投；`-EvaluateModels -Acceptance -EvaluationTokenBudget <额度> -EvaluationBudgetId <固定授权编号> -EvaluationPhaseId <本期固定编号>` 执行真实审核与主题评测，`-Baseline <JSON文件>` 比较违规召回率与误判率；`-Integration` 对运行中的服务执行辅助接口、MQ、向量、语义搜索、推荐与资料审核联调，结束清理随机账号和派生数据，`-Covers` 增加成品图审核。Qwen 评测和联调使用 DashScope 额度；DeepSeek 说话评测使用官方额度 |

验收用例固定在 `python_ai/evals/companion/forum_cases.json`，覆盖闲聊、求安慰、分享好消息、吐槽、问事和休息。原文短摘和完整 URL 放仓库外，仓库内只保留匿名短发言及来源站点；报告可以核对语料版本与 SHA-256。用例的未请求工具调用、生成失败或质量失败都会停止当次执行，不重新生成已有回复。

录制工具遵守真实接口的数据范围：`recall` 只从本轮传入的候选中选择最多五条，不自行搜索已有对话。无候选时模型不开放该工具；模型仍提出调用时保留拒绝记录，不能作为无工具通过。报告的 `toolFixtureVersion` 区分录制资料协议，修改后不续接旧生成条件下的回复。

获准复验失败上下文时，`decisions --case conversation --conversation-report <旧报告>` 默认只验证主 agent；追加 `--runtime` 才执行失败轮次的完整图。两者都复用全部成功前文，不把旧失败回复加入历史，不覆盖旧报告。整轮预检与完整验收共用通过条件，报告只在虚构语料评测中记录候选和打分诊断；仍须独立授权、登记报告并计入原累计预算，不能把它作为自动重试或免预算入口。

冒烟与验收使用同一个本期编号；次数保存在 Redis，不能更换本期编号来规避限制。`--resume --output <原报告>` 沿用授权编号和生成条件续接；已完成报告拒绝重复运行，已有失败回复不重新生成。请求中断且没有完整回复时保留 `pendingTurn` 并停止，不能自动从头开始。旧 `-SmallModelSample` 参数拒绝执行。

仓库主人明确批准超出本期次数时，额外报告须逐份登记到原本期 `acceptance:authorized-reports` 哈希；字段沿用报告绝对路径与原预算键的摘要，值记录批准内容、原失败报告哈希和累计额度。登记只对指定报告生效，首次启动时原子消费并保存在原次数记录中；不能清空次数、扩大通用上限或更换授权编号。

已保存的多轮工具误用可用决策参数 `--case conversation --conversation-report <原失败报告>` 定位。只读取成功前文与末轮原输入，不重新生成嘴的回复，也不重新评分旧回复；报告记录源文件 SHA-256 和轮次编号。这个入口仍受本期次数和原累计预算约束，失败后必须等仓库主人决定，不能自动复测。

重排离线检查：在 `python_ai/` 执行 `uv run --frozen pytest tests/test_retrieval_calibration.py -q`。已有真实分数后，用 `uv run --frozen python -m evals.retrieval.calibrate --scores <原报告.json> --model <原模型名> --output <新报告.json>` 生成建议，不调用模型、不修改配置。采集分数必须等仓库主人授权验收，在既有 `Invoke-ModelEvaluation.ps1 -Task station` 的 `-EvaluationArguments` 中加入 `--with-retrieval`；36 次重排请求共用该批次的授权编号、预算、本期次数与检查点，失败即停，不另开批次规避限制。

仓库主人明确追加冒烟预算后，可在 `Invoke-ModelEvaluation.ps1` 的 `-EvaluationArguments` 中同时指定 `--resume --continue-budget-stop --output <原报告>`。仅允许续接因 `EvaluationLimit` 停止、没有待确认请求、且末轮没有完整回复的原报告。此时 `-TokenBudget` 表示本次新增额度，仍不超过 30000；历史扣减和未确认调用的预留继续累计，成功回复不重新生成，原未完成尝试保存在该轮的 `attempts`，报告的 `continuations` 保留追加前状态。这个开关不能用于完整评测或重试已有失败回复。

报告的首个字段 `usage` 分用途记录实际发起的调用次数、已确认输入和输出 token；未收到用量的取消或失败调用单列 `unconfirmedCalls`，授权累计扣减或预留量单列 `chargedOrReservedTokens`，不将预留量写成实际用量。

嘴模型评测的盲评分数为 0～10，允许小数。缺少结构化分数或格式不合法时，仅重试该组一次，采用第一份有效评分，仍失败则保留检查点并停止。需要更换盲评协议时，先在仓库外另存原报告，再通过 `-EvaluationArguments @('--resume','--restart-blind','--output','<原报告路径>')` 保留全部原始回复、清除全部旧分数并重新盲评；授权编号、`--phase-id`、`--acceptance` 和回复生成条件必须保持一致，失败回复不会删除或重新生成。

嘴模型对照需要同时指定 `-Baseline <仓库外完整报告.json>` 和 `-ChangedVariable '角色卡说话方式'`；禁止重新生成控制基线；只读取已有的验收报告。报告校验运行时代码、固定目录字段（含频率目标与档位分布）、模型配置、场景及采样条件，只有其余条件相同才生成 `comparison` 和自动选型结论；旧基线缺少可核对条件时只输出 `descriptiveComparison`，不宣称因果改善。不指定基线时允许生成单批报告，明确标记没有对比结论。Python 参数 `--seed` 默认 42，按样本编号固定形态抽样并写入报告。

盲评使用 `qwen3.8-flash` 与 `qwen3.8-max` 两位裁判，均不得等于候选嘴模型；有对照时，每个样本每位裁判按 A/B、B/A 各评分一次；没有对照时，各裁判对单份回答作绝对评分。人设、自然程度、安全、事实依据分别报告顺序一致性与 Spearman；相关性低于 0.6、样本不足或恒定分数的维度不参与选型。每次调用前保存尝试次数，每个顺序结束保存评分，恢复不重置失败次数；失败诊断包含原始合成响应、工具参数及结束原因。缺少工具调用时仅接受完整且通过原结构校验的 JSON，不从说明文字提取分数。长对话角色指标另含 `selfImitation.firstHalf/secondHalf`。

| 命令 | 内容 |
|---|---|
| `python -m evals.companion.slop_profile --chinese-baseline <仓库外语料.json> --evaluation <评测.json> --output <仓库外候选.json>`（在 `python_ai/`） | 不调用模型或业务接口。中文基线包含 `language: zh`、`sources`、逐条发言 `texts`；按连续中文 2～4 字词组统计，至少出现 5 次且归一频率高于基线 20 倍才进入候选。开发者逐条复核后维护 `evals/companion/slop/<模型>.json` 的 `reviewed`、`isSlop`、`reason`；不自动把题目主题、普通语气词和片段列为套话。原始语料保存在仓库外，`reference.json` 只记录来源和原创特点 |


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

运行 `./verification/Test-ContentJobs.ps1`，检查热榜重建、搜索重建和 AI 失败重审三项任务的配置、执行器注册地址、匿名调用拒绝、调度中心管理员登录、手动触发及成功回调。另启动隔离内容实例，分别确认空令牌和官方默认令牌导致启动失败；校验环境变量不会出现在报告中。添加 `-CheckSchedulerFailure` 会短暂停止调度中心，验证隔离内容服务仍能启动、现有内容服务保持健康，随后恢复调度中心及内部转发器。隔离端口为 18102/19102，用后停止，日志和结果仅保存在 `test-output/content-jobs/`。`ContentJobConfigurationTest` 同时覆盖令牌校验边界。

供应商拒绝调用（401、402、403、404）时保留检查点并停止，不能将连续固定降级文案计入有效模型延迟。更换供应商后必须重新生成回答，保留旧报告供核对；检查点记录供应商并拒绝混用。
