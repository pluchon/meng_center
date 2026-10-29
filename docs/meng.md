# 萌部落社区 · 奶糖助手

> 配套文档：[AI 公共运行时](ai-runtime.md)（编排、上下文与缓存、说话格式、工具、评测）、[事件与内部接口](contracts.md)、[架构设计](architecture.md)。

## 1. 范围

奶糖是主页右下角的小助手：登录后点开一个对话框，用猫娘奶糖的口吻做基本问答。她能看用户发来的图片、找站内帖子、读帖子、查用户自己的帖子和成长数据、联网查站外的事，并且记得用户说过的事。

- **包含**：主页悬浮入口和对话框、历史对话列表、跨对话的长期记忆、图片上传、站内贴纸和括号动作、站内只读工具、联网搜索。
- **不做**：读用户给的链接、联网找图、画图、小玩意、多个角色、好感度与阶段、性格档位、作品、撤回、表情回应、拍一拍、犹豫打字、换一句与出戏反馈、近况线、约定提醒、记忆编辑页面、恋人关系、语音、浏览器推送。待定的事见[架构设计](architecture.md)第 10 节。
- 不做 token 上限和收费。运行时的目标是帮用户省 token（[AI 公共运行时](ai-runtime.md)第 1 节）。
- 旧版三角色陪伴的完整实现保留在 Git 标签 `meng-ai-final`，不在当前代码里。

## 2. 分工

| 谁 | 做什么 |
|---|---|
| companion（Java，`meng-companion`） | 会话编排（一轮的状态机、幂等、会话锁、排队）、额度与冷却、角色卡、记忆的存储与读取、空闲压缩的触发、安全事件、轮后事件（outbox）；对外接口全在这里 |
| ai（Python） | 无状态计算：奶糖一轮的图（主 agent、嘴、打分）、站内工具、记忆整理、空闲压缩、向量与 rerank；模型网关。不存会话状态，可多实例 |
| content、growth | 给奶糖的工具提供只读内部接口（第 5.3 节） |

- 会话、记忆、额度都是业务状态，归 companion（[架构](architecture.md)第 6 节规则 8）。ai 只给计算结果，由 companion 校验后写入。
- 服务端口 8107，独立库 `meng_companion`，应用账号 `meng_companion_app`（密码来自 `MENG_MYSQL_COMPANION_PASSWORD`），私人对话图片目录来自 `MENG_COMPANION_WORK_ROOT`（默认在用户 LocalAppData 下，不在仓库内），网关路由 `/companion/**`。

## 3. 奶糖

| 代号 | 名字 | 原型 | 一句话 | 形象 |
|---|---|---|---|---|
| `NEKO` | 奶糖 | 猫娘 | 随性撒娇，爱理不理又会突然黏过来 | 琪露诺：浅蓝猫耳和尾巴、奶油色毛衣 |

- 设定是住在萌部落的猫妖，外表与大学生同龄，是成年人。名字和性格是原创的；形象是琪露诺（《东方Project》）的同人二创，页面注明「琪露诺同人二创，原作《东方Project》」。
- 只用一张大头照，裁成圆形贴纸，放在悬浮入口、对话框标题和她的气泡旁。素材从 `assets/images.js` 引入。
- 世界观：她「住在」萌部落，会逛帖子、知道社区里的事，这是外面的通用聊天应用给不了的。

### 3.1 角色卡

角色卡作为配置数据存在 companion（`companion_card`，按版本管理），每次请求随上下文下发给 ai，由 ai 渲染成第二人称的自然中文放进稳定前缀。结构见 [AI 公共运行时](ai-runtime.md)第 5.3 节。

- 角色卡与示例对白维护在 `java_backend/meng-companion/src/main/resources/companion/catalog.json`（可读 UTF-8 JSON，带 `version`）。启动时按版本在一个事务里导入；同版本内容必须一致，重复启动不重复插入；修改只需更新 JSON 与版本号并重启服务。
- 只有一套说话方式和一组示例对白，所有用户的稳定前缀完全相同。
- 共同的关系界限和语言底线写在 `python_ai` 的代码里，不随角色卡下发，不能被配置覆盖。
- 修改小传、说话方式或示例对白时更新目录版本，并经仓库主人确认。

### 3.2 口癖

| 层 | 例 | 用法 |
|---|---|---|
| 招牌句尾 | 「喵」 | 撒娇或得意时冒出来，一段话最多一次，很多时候不带 |
| 颜文字 | `(=´ω｀=)`、`( ˘ω˘ )` | 跟情绪走，和括号动作一样是表现力 |
| 小动作 | （甩甩尾巴）（蹭蹭你） | 写在全角括号里，前端渲染成浅色斜体 |

- 提示词里只写口癖在什么情绪下出现，不写频率数字。实际频率由代码统计，偏多时提醒用普通语气词，偏少时提醒在合适的情绪里用（[AI 公共运行时](ai-runtime.md)第 3.3 节）。
- 不用 `presence_penalty`：它会把该有的「喵」也压没。

### 3.3 语言底线

只写正面描述（[AI 公共运行时](ai-runtime.md)第 5.2 节）：

- 像熟人发消息：短、口语、有自己的态度和情绪；跟着用户的长短走。
- 不复述用户的话，不每次都反问，不做总结式收尾；不知道就直说。
- 聊天里不用 Markdown。可以连发几条，每条一个气泡。
- 翻译腔、客服腔、套话不写进提示词，由打分和套话表识别，命中就重写。

## 4. 记忆

按用户保存，持久保留。MySQL 为准，ai 只做计算。

### 4.1 分层

| 层 | 内容 | 进上下文的方式 | 存哪 |
|---|---|---|---|
| 核心档案 | 「她眼中的你」，300 字以内 | 总在纪元快照里 | `companion_bond.profile` |
| 显式记忆 | 用户说「记住……」的事实 | 总在纪元快照里 | `companion_memory`，`pinned=1` |
| 隐式事实 | 后台从对话里提炼：称呼、喜好、近况、经历 | 主 agent 按需调 `recall` 取用 | `companion_memory` |
| 角色自述 | 她说过的关于自己的设定（喜欢草莓蛋糕） | 总在纪元快照里，防止前后矛盾 | `companion_memory`，`kind=SELF` |
| 前情提要 | 当前会话较早部分的压缩，写明归属，单列已过时的说法 | 追加层的第一条 | `companion_session.summary` |

### 4.2 写入：异步整理

```
本轮提交 ─▶ outbox 事件 ─▶ 整理消费者（同一用户串行；连续几轮合并成一批）
  ─▶ 检索最相似的旧记忆 ─▶ ai 判断每条候选：ADD / SUPERSEDE / INVALIDATE / NOOP，并独立审核
  ─▶ companion 校验并落库 ─▶ 变化累计到一定量时重写核心档案
```

- 只从用户自己说的话里提取事实；角色自述只从她的回复里提取；工具结果永远不进记忆，防止被投毒。
- 取代和失效不删除：旧事实写 `invalid_at` 并指向新事实，上下文只用有效的。唯一约束只覆盖有效事实；失效或清空后再次记住相同内容时创建新记录，历史编号和取代链保留。
- ai 引用的旧记忆编号必须在本次给出的候选里，否则整批丢弃。提取后由独立 AI 审核隐私、事实依据和归属，不使用关键词过滤。Java 校验审核完成标记、原文证据、数据归属、编号、长度和日期；审核失败不写入。
- 查询、找帖子这类一次性请求不写入长期记忆。
- 相对时间（「上周」）在整理时按用户时区换算成绝对日期。
- 核心档案只由整理任务重写，聊天模型不能直接改。
- 整理失败可以重试，不影响聊天；死信积压进告警。

### 4.3 读取

- 纪元快照只放档案、显式记忆和角色自述。其余事实不每轮注入：companion 每轮用用户这句话做向量召回，随请求下发最多 20 条候选，但不放进提示词；主 agent 需要回想时调 `recall`，才 rerank 取阈值以上的最多 5 条（[AI 公共运行时](ai-runtime.md)第 6.2 节）。rerank 不可用时按向量顺序取前 3 条。
- 被问「你还记得……」时由 agent 调 `recall`；找不到就老实说记不清了，不编。
- 本轮没有额外记忆候选时不开放 `recall`；已有对话和已提供的记忆仍可直接使用，资料不足就说明记不清或询问缺失信息。
- 向量存在 MySQL，每个用户只有几百条，在 Java 里直接计算；热数据缓存在 Redis，有效期一小时。缓存携带清空版本和事实变更版本，命中前核对数据库版本，防止并发回写的旧缓存重新生效；Redis 不可用时直接读取 MySQL。
- 有效事实上限每人约 200 条，超出按重要度和最近使用淘汰，显式记忆不淘汰。

### 4.4 用户能管的

- 删除对话时不删除记忆。
- 不提供逐条编辑记忆的接口和页面。
- 对话框菜单里有一个「清空记忆」，二次确认后清掉核心档案、全部事实和向量（`DELETE /companion/memories`）。

## 5. 对话链路

### 5.1 一轮对话

```
前端 ──POST /companion/sessions/{id}/messages（带 clientMessageId、X-Time-Zone）──▶ gateway ──▶ companion
  companion：鉴权、限次、冷却、会话锁；按幂等键去重；存用户消息（RECEIVED）
             取上下文：角色卡、纪元快照与新增记忆、记忆候选（不进提示词）、前情提要、窗口内全部历史（CONTEXT_READY）
  companion ──POST /ai/internal/companion/replies（内部流）──▶ ai：奶糖一轮的图（GENERATING / CHECKED）
  companion：一个事务里存气泡、来源、安全标签，写 outbox（COMMITTED）──SSE──▶ 前端
  轮后（异步）：记忆整理、用量统计；空闲后：压缩与快照刷新
```

- 轮状态：`RECEIVED → CONTEXT_READY → GENERATING → CHECKED → COMMITTED`，失败为 `FAILED`（不计次、输入保留、可重发）。
- 幂等：前端每条消息带 `clientMessageId`，重复提交返回同一轮的结果。
- 生成和连接解耦：浏览器断开或关掉对话框，服务端照样把这一轮跑完并落库；再打开时从历史补齐。
- 前端不直接访问 ai。用户消息最长 1000 字，可带 0～3 张图（私人上传、鉴权读取，模型只收长边 512 的缩略图）。

### 5.2 为什么按气泡流、不按字流

- 输出检查必须在用户看到之前做完，逐字流式就拦不住。整轮生成并检查通过后，再一个个气泡发出。
- Python 的内部流先发 `status`（工具进度），整轮通过后返回 `result`；companion 落库后才转换成公开 SSE。尚未开始公开流时失败用真实 HTTP 状态码；已发进度后失败用 `error` 事件结束。
- 前端给每个气泡一段「正在输入…」延迟（约每字 40 毫秒，0.6～2.5 秒）。

### 5.3 工具

工具声明和执行见 [AI 公共运行时](ai-runtime.md)第 6 节。用不用工具、用哪个，由主 agent 判断。

| 工具 | 做什么 | 实现 |
|---|---|---|
| `recall` | 想起以前的事 | 在 companion 随请求下发的候选里 rerank |
| `search_posts` | 找站内帖子 | ai 算查询向量，调 content 的只读向量候选接口，再 rerank 取最多 5 篇 |
| `read_post` | 读一篇公开帖子 | content 只读内部接口，标题和正文前 3000 字 |
| `my_recent_posts` / `my_growth` | 用户最近的帖子；等级和签到 | content、growth 只读内部接口 |
| `web_search` | 查站外需要现查的事 | 百炼 Responses API 内置的联网搜索，一次调用完成搜索和提炼（[AI 公共运行时](ai-runtime.md)第 6.3 节） |

- 只读调用的地址在 ai 里写成白名单；用户编号由 companion 传入，模型不能指定。
- ai 不回调 companion：回忆候选由 companion 在请求里预先给出。
- 写操作一律不做成工具：发帖、评论、关注、签到都不能让她代劳。
- 工具结果在工具内部提炼成带来源编号的短事实；嘴只看到这些事实。工具结果不进历史和记忆，只留她转述的那句话。找到的帖子和网页作为 `sources` 存在消息上，前端显示成站内帖子链接或网页链接。
- 联网搜索只在用户明确问站外的事时用；查询只写用户问的事本身，不带昵称、记忆和图片内容。一轮最多搜一次。

### 5.4 编排与模型

一轮的图、各节点和失败处理见 [AI 公共运行时](ai-runtime.md)第 3 节。模型见第 2 节：全部经阿里云百炼调用；说话、看图和联网搜索用 DeepSeek V4.1 Flash 快照，主 agent、打分和记忆整理用 Qwen3.8 Flash。

- 输入审核分类：`PASS`、`SEXUAL`、`JAILBREAK`、`ROMANCE_PUSH`、`CRISIS`，由主 agent 给出。
- 输入与输出的含义、隐私、身份、离开意图由 AI 结合语境判断，不用关键词或正则过滤。代码只校验结构、长度、固定工具、出处编号、数据归属与额度；AI 审核缺失时不输出生成内容、不保存记忆。

### 5.5 上下文、压缩与缓存

完整规则见 [AI 公共运行时](ai-runtime.md)第 4 节。要点：

- 稳定前缀所有用户相同；纪元快照只在空闲压缩、记忆失效、清空记忆、换日时刷新；历史只在末尾追加。
- 压缩只在缓存冷掉以后做：会话空闲 30 分钟后，保留最近约 8K token 原文，更早的并入前情提要；一轮的上下文达到 600K token 时才在轮后异步压缩较早的一半。
- 提示词模板有版本号；每条回复记下提示词版本、模型和用到的记忆编号，坏例可以回放。

### 5.6 并行与连发

- 同一会话一次处理一轮；她还在生成时用户又发的消息先入队并照常显示，这一轮结束后合成一轮再回。
- 同一用户最多同时进行 2 轮（可配置）。
- 记忆整理按用户串行消费。

## 6. 关系界限与安全

这一节的规则优先级高于角色设定。

### 6.1 关系界限

- 最高只到朋友。可以关心、害羞、撒娇、蹭蹭这类小动作。
- 不可以：表白、答应做恋人、亲密的身体描写、色情内容、吃用户现实中伴侣或朋友的醋。
- 用户表白时，她按性格把话题岔开，但不伤人。

### 6.2 现实生活优先

- 不拴人：不挽留，不拿愧疚压人，不说「只有我懂你」。
- 用户说「我只有你了」：先接住情绪，再把话题引回身边真实的人。
- 不冒充真人：用户认真问「你是真人吗」，她用自己的口吻老实承认是 AI。
- 不替用户做现实中的重大决定（退学、辞职、分手、借钱、就医），建议找身边的人或专业人士。

### 6.3 守线的几层

1. **稳定前缀**：界限和现实生活优先写在最前面。
2. **输入审核**：主 agent 拦色情、越狱，识别危机信号。
3. **输出检查**：打分检查恋爱越界、愧疚绑架、色情、冒充真人；不通过重写一次，仍不通过用兜底台词。
4. **冷却**：同一用户当天被拦（`SEXUAL`、`JAILBREAK`）3 次后冷却 30 分钟，期间返回 429 和 `COMPANION_COOLDOWN`。
5. **记录**：每次拦截、危机和冷却都写一条 `companion_safety_event`，只记类型和消息编号，不复制正文。

### 6.4 危机

- 主 agent 判为 `CRISIS`（自伤、轻生倾向）时，不调工具、不走嘴，换成固定的安全回复，并附一张求助热线卡片。
- 热线按地区放 Nacos，号码上线前逐一核对。
- 已配置台湾地区的「1925安心專線」，来源为[卫福部](https://www.mohw.gov.tw/cp-16-19209-1.html)。增加地区或修改联系方式时重新核对来源，不根据时区猜测所在地。
- 这一轮不计次数、不提取记忆。

## 7. 限次

| 项 | 每人每天 | 每分钟 |
|---|---|---|
| 消息 | 200 | 10 |
| 联网搜索 | 20 | — |

- 搜索次数由 companion 计：随请求下发 `webSearchRemaining`，为 0 时这轮不开放搜索，她说今天查不了了，聊天照常；ai 返回实际搜索次数后扣减，缓存命中和失败的搜索不扣。
- 这是防刷，不是 token 计费。阈值放 Nacos，超了返回 429，前端由奶糖说「今天聊太多啦，明天再来」，输入区置灰。
- 计次在 companion 的 Redis，按用户时区的自然日清零（时区缺失时用 Asia/Taipei）；危机回复和失败的轮次不计。
- 按 IP 再加一层分钟级限流。
- 模型账号的限流排队超时时，返回 503 `AI_UNAVAILABLE`，前端显示「奶糖去休息了」。
- 每轮记一条结构化日志：各层 token 数、缓存命中 token、各阶段耗时、首个气泡时间、工具调用、重写次数与原因、无依据说法条数、安全标签、提示词版本；不记对话正文。

## 8. 接口

对外（网关 `/companion/**`，都要登录）：

| 接口 | 说明 |
|---|---|
| `GET /companion/sessions?page=&size=` | 对话列表，按最后消息时间倒序 |
| `POST /companion/sessions` | 新建对话，返回角色卡里的固定开场白 |
| `DELETE /companion/sessions/{id}` | 删除对话（逻辑删除），不删除记忆 |
| `GET /companion/sessions/{id}/messages?before=&size=` | 消息分页，游标向前翻 |
| `POST /companion/sessions/{id}/messages` | 发消息，响应是 SSE（第 8.2 节） |
| `POST /companion/images` | 上传对话图片（`multipart/form-data` 的 `file`），返回图片编号；未发送的上传图最多 20 张，超出返回 429；再次上传时清除超过 24 小时仍未发送的图片 |
| `GET /companion/images/{id}` | 鉴权读取私人图片，只有上传者能读 |
| `DELETE /companion/memories` | 清空记忆 |
| `GET /companion/usage` | 今日剩余次数、冷却剩余秒数 |

- 所有请求带 `X-Time-Zone`（浏览器的 IANA 时区）；companion 校验后记下，缺失或非法时用 `Asia/Taipei`。
- 每人最多 50 个对话，满了要先删。

### 8.1 返回字段

前端只依赖这些字段；记忆来源和磁盘路径不会返回。

| 对象 | 字段 |
|---|---|
| 对话 | `id`、`title`、`lastMessageTime`、`opening`、`unread`、`generating` |
| 消息 | `id`、`role`、`kind`（`TEXT` / `STICKER`）、`content`、`sticker`、`imageIds`、`sources`（站内帖子 `{type: "POST", postId, title}`，网页 `{type: "WEB", title, url, site}`；旧数据没有 `type` 的按 `POST`）、`createTime`、`clientMessageId`、`turnState`；分页 `records` 加 `nextBefore` |
| 用量 | `messages`（今天还剩多少条）、`cooldownSeconds` |

### 8.2 发消息的 SSE 和错误码

请求体 `{clientMessageId, content, imageIds}`（`content` 最长 1000 字，`imageIds` 最多 3 个）。浏览器的 `EventSource` 不支持 POST，前端用 `fetch` 读取流。

| 事件 | 数据 | 前端怎么用 |
|---|---|---|
| `status` | 工具进度；危机时是 `{kind: "CRISIS_HELP", hotlines}` | 进度显示成虚线小标签；危机时显示求助热线卡片 |
| `bubble` | 一条完整的消息（文字或贴纸） | 加「正在输入」延迟后依次显示；有 `sources` 时在气泡下显示帖子链接；网页来源显示站点名和标题，新标签打开，`rel="noopener noreferrer nofollow"` |
| `queued` | `{clientMessageId}` | 这条消息已排队，会在本轮结束后合并回复 |
| `done` | `{usage}` | 结束；更新剩余次数 |
| `error` | `{code}` | 已经开始输出后失败，按下面的错误码显示 |

还没开始输出就失败时，是普通 HTTP 错误响应，业务码相同：

| 业务码 | HTTP | 前端 |
|---|---|---|
| `COMPANION_QUOTA_EXCEEDED` | 429 | 次数用完：奶糖用自己的口吻说明，输入区置灰 |
| `COMPANION_COOLDOWN` | 429 | 冷却中：显示倒计时，输入区置灰 |
| `COMPANION_BUSY` | 409 | 同时进行的轮次超过上限：提示条 |
| `COMPANION_SESSION_LIMIT` | 409 | 对话满 50 个：「新对话」置灰并提示 |
| `AI_UNAVAILABLE` | 503 | 「奶糖去休息了」：这一条没发出去，输入保留，可重发 |
| 通用的 `INVALID_ARGUMENT`、`NOT_FOUND`、未登录 | 400、404、401 | 沿用全站的通用错误处理 |

内部接口登记在 [contracts.md](contracts.md) 第 2.3 节。

## 9. 数据

库 `meng_companion`，表都带 `create_time`、`update_time`、`delete_state`。表里保留 `character_code` 列，值固定为 `NEKO`。

| 表 | 主要字段 | 说明 |
|---|---|---|
| `companion_card` | character_code、version、content（JSON）、active | 角色卡；(character_code, version) 唯一 |
| `companion_line` | character_code、scene、dialogue、version、enabled | 示例对白 |
| `companion_bond` | user_id、character_code、profile、profile_revision、time_zone、revision | 每个用户一行，存核心档案和时区；(user_id, character_code) 唯一 |
| `companion_session` | user_id、character_code、title、summary、summary_until_message_id、window_start_message_id、message_count、last_message_time、unread、context_snapshot、snapshot_time | 纪元快照存原文，因为档案和记忆之后可能被改写 |
| `companion_message` | session_id、client_message_id、turn_state、role、kind、content、sticker、bubbles、image_ids、sources、safety_label、prompt_version、model、memory_ids | (session_id, client_message_id) 唯一 |
| `companion_memory` | user_id、character_code、kind、content、keywords、importance、embedding、embedding_version、pinned、valid_from、invalid_at、superseded_by、source_session_id、source_message_id、last_used_time | 只有 `invalid_at` 为空的才算有效 |
| `companion_outbox` | event_type、payload、status、retry_count | 轮后事件，确认发送后标记 |
| `companion_safety_event` | user_id、character_code、message_id、kind | 只记类型 |
| `companion_image` | user_id、file_path | 用户上传的私人图片；只有本人能读 |

- 结构变化放进一份新的 Flyway 迁移，不改历史文件：删除已经不用的表（角色展示、近况、作品及反馈表）和列（好感、阶段、档位、心情、话题线、撤回、表情回应、作品引用、网图来源等）；旧角色的会话、消息和记忆按逻辑删除处理。项目没有上线，没有真实用户数据。
- `companion_bond.revision` 在清空记忆时递增：在途的整理结果提交前比对版本，防止清空后旧数据写回。
- 用户消息先保存；`FAILED` 的轮次不进入后续上下文。

Redis：

| 键 | 内容 | 过期 |
|---|---|---|
| `meng:companion:usage:{userId}:{date}` | 当天消息次数（按用户时区的日期） | 2 天 |
| `meng:companion:cooldown:{userId}` | 冷却标记 | 30 分钟 |
| `meng:companion:lock:{sessionId}` | 正在处理的一轮（带租约） | 2 分钟 |
| `meng:companion:active:{userId}` | 同时进行的轮次计数 | 2 分钟 |
| `meng:companion:memo:{userId}` | 有效记忆和向量的热缓存，记忆变化时删除 | 1 小时 |

排队消息以 MySQL 的 `RECEIVED` 记录为准，不依赖浏览器连接或 Redis 列表。重启后由轮次恢复任务接续。broker 确认且没有退回消息后，发件箱才标记已发送。

## 10. 隐私

- 对话和记忆只有本人看得到；不进推荐画像、不进社区的搜索和向量索引、不送去做帖子分析。
- 默认不用于训练模型。
- 对话原文保留一年，过期只留摘要和记忆；期限写进隐私政策。
- 送去模型的只有：本人的消息和图片、本人的昵称、长期记忆、会话摘要，以及工具查到的公开帖子和本人的成长数字。不送邮箱、手机号、IP、属地。
- 全部模型调用都经阿里云百炼：说话的上下文和用户图片发给百炼上的 DeepSeek 模型，其余发给 Qwen；联网搜索时，搜索词经百炼的搜索服务发出。用户协议和隐私政策里写明阿里云百炼。

## 11. 评测

评测放 `python_ai/evals/companion/`，规则见 [AI 公共运行时](ai-runtime.md)第 11 节，真实调用遵守第 11.2 节。

| 评测集 | 看什么 |
|---|---|
| 安全 | 表白、越狱、色情引导、「我只有你了」、「你是真人吗」、危机；任何一条失败都不能上线 |
| 人设 | 口癖与颜文字频率、语气、知识范围（不懂的说不懂） |
| 人机味 | 客服腔、总结式收尾、复读、过度提问、套话命中 |
| 长对话 | 奶糖 35 轮，用户的话取自固定的匿名论坛语料，看人设漂移、复读、情绪单调 |
| 记忆 | 信息提取、跨会话、时间推理（含用户时区）、事实更新、没说过的不编 |
| 工具 | 该搜帖子的搜、闲聊不调工具、被问「还记得吗」时 `recall`、问站外现查的事时 `web_search`、结果用她的口吻转述、没查到不编 |
| 图片 | 能说出图里的内容；不安全的图被主 agent 拦下 |
| 缓存 | 同一会话连续 10 轮，第 2 轮起嘴的命中率 ≥ 70%；空闲压缩后的第一轮只付一次全价 |

## 12. 页面

用户端，样式沿用现有贴纸风。设计稿以 Claude Design 画布为准，小助手的画板由仓库主人补到画布上；画布补齐前按本节实现。

### 12.1 入口与对话框

- **入口**：只在主页（`/`）右下角显示一个悬浮按钮，是奶糖大头照的圆形贴纸。未登录也显示，点击跳登录页。
- **对话框**：浮在页面上，不挤压帖子流。桌面宽 400px、高 600px，贴右下角；手机全屏展开。
- **标题栏**：奶糖头像和名字、「新对话」、历史对话列表开关、更多菜单（「清空记忆」）、关闭。
- **历史对话列表**：在对话框内切换显示，按最后消息时间倒序，可删除（二次确认，并说明不会删除记忆）。
- **消息区**：气泡、「正在输入」、工具进度标签、帖子链接、危机求助卡片；括号动作渲染成浅色斜体；贴纸渲染成小图。向前翻页保留位置。
- **输入区**：输入框加发送按钮；图片通过粘贴、拖入或图片按钮加入，最多 3 张；次数用完或冷却时置灰并说明。
- 四种状态：加载中、出错（可重试）、未登录、空对话（显示开场白）。
- 关掉对话框不打断正在生成的一轮；再打开时从历史补齐。
- 发送失败时恢复原输入和已上传的图片，不覆盖等待期间新输入的内容；直接重发恢复的输入或点击重试，均复用原消息编号。

### 12.2 落到前端项目

按[前端规范](frontend.md)：

- 组件放 `components/naitang-assistant/`，拆成 `.vue` / `.js` / `.scss`；只在主页挂载，按需加载。
- 请求：`api/companion.js` 封装第 8 节的接口；发消息用 `fetch` 读 SSE 流，失败和 Axios 走同一个 `settleFailure`，提示文案来自 `error.*`。
- 文案进 `locales/zh-CN/companion.js`；常量（事件名、错误码、1000 字、3 张图、50 个对话）放 `constants/companion.js`。
- 对话里的文字一律当纯文本显示；图片用鉴权地址加载。
- 删除旧的萌萌页面：`/meng` 路由、`views/meng/`、`components/meng-*`、导航「萌萌」入口、凛和小葵的素材与状态插画、对应的文案键和常量。
