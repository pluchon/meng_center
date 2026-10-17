# 萌部落社区 · 事件与内部接口

> 2026-10-08 首版。这是[架构设计](architecture.md)第 7 节依赖表的展开。
> 这里没有列出的跨服务交互都不允许存在。

## 1. 事件

### 1.1 通用约定

- 事务提交后才发送，不在事务内发。
- 统一信封：`eventId`、`eventType`、`occurredAt`、`traceId`，加上事件体。
- 消费者按 `eventId` 去重，重复投递不产生重复效果。
- 事件体带够消费者需要的字段，避免消费者再回头同步查生产者。
  例如评论事件带帖子标题快照，im 生成通知文案时不用去查 content。
- 事件消息体的类定义放在生产者的 `meng-api-*` 模块里。ai 是 Python 服务，它发出的事件体以 `python_ai` 的 pydantic 定义为准，content、user 在自己的包里写对应的类。
- `imageThumbs`、`avatarThumb` 是审核用的小图：长边 512 的 JPEG，Base64，只在新发或图片有变化时带。

### 1.2 命名

| 对象 | 规则 | 例 |
|---|---|---|
| 交换机 | 每个生产域一个 topic 交换机 | `meng.content.topic` |
| 路由键 | 等于事件名 | `post.published` |
| 队列 | 消费者加事件名，每个消费者独立队列 | `meng.growth.post.published` |

### 1.3 清单

| 事件 | 生产者 | 关键字段 | 消费者及用途 |
|---|---|---|---|
| `user.approved` | user | userId | im：欢迎通知 |
| `user.followed` | user | followerId、followeeId | im：通知被关注的人（系统频道的「关注」分类）；只在新增关注时发，取消不发 |
| `user.profile-updated` | user | userId、version、nickname、signature、avatarChanged、avatarThumb（更换或尚无审核结果时才有） | ai：资料审核 |
| `user.profile-moderated` | ai | userId、version、modelVersion、verdict、labels、reason、avatarChanged、imageReviews | user：记录结论，按开关处理；沿用未变化头像的逐图结论 |
| `user.profile-review-changed` | user | userId、clearedFields、labels | im：通知本人（系统频道的「审核」分类） |
| `post.published` | content | postId、authorId、version、category、title、plainText、tags、imageThumbs、imagesChanged | growth：加经验；ai：分析帖子 |
| `post.updated` | content | 同 `post.published` | ai：重新分析 |
| `post.analysis-requested` | content | 同 `post.published` | ai：全量重建或换模型时重新分析，由管理员触发 |
| `post.analyzed` | ai | postId、version、modelVersion、topics、keywords、quality、verdict、labels、reason、embedding、analyzeState、imagesChanged、imageReviews | content：写特征、逐图结论和向量索引，按审核开关处理 |
| `post.review-changed` | content | postId、postTitle、authorId、reviewState、labels | im：通知作者（系统频道的「审核」分类） |
| `comment.created` | content | commentId、postId、postTitle、authorId、targetUserId、parentCommentId、excerpt、originalExcerpt、mentionedUserIds、content、imageThumbs | im：通知被评论或被回复的人，以及被 @ 到的人；growth：加经验；ai：审核 |
| `comment.moderated` | ai | commentId、modelVersion、verdict、labels、reason | content：记录结论，按审核开关处理 |
| `comment.review-changed` | content | commentId、postId、postTitle、authorId、excerpt、reviewState、labels | im：通知评论作者 |
| `post.liked` | content | postId、postTitle、actorId、authorId | im：通知作者 |
| `post.favorited` | content | postId、postTitle、actorId、authorId | im：通知作者；某人对这篇帖子的第一行收藏记录插入成功时发一次，之后放进别的收藏夹不重复发 |
| `comment.liked` | content | commentId、postId、postTitle、excerpt、actorId、authorId | im：通知评论作者 |
| `report.handled` | content | reportId、reporterId、targetType、targetId、postId、postTitle、excerpt、targetAuthorId、result | im：通知举报人；成立时通知内容作者 |
| `growth.level-changed` | growth | userId、level | user：同步展示用等级；im：升级通知 |
| `lottery.won` | growth | userId、prizeName | im：中奖通知 |
| `companion.stage-reached` | companion | userId、characterCode、characterName、stage、stageName | im：通知本人（系统频道的「萌萌」类型） |

说明：

- 公开资料展示仍走批量内部接口现查；`user.profile-updated` 只供 AI 审核，不用它同步其他域的资料副本。
- 签到不是事件，它是 growth 自己的写接口。
- 取消喜欢、取消收藏不发事件，已经发出的通知不撤回。
- 审核被拒绝不发事件，由 user 自己发通知邮件或短信。
- growth 的成长账户按需创建，不靠 `user.approved` 初始化，见[成长域](growth.md)。
- AI 请求、审核通知使用 content / user 本域的事务 outbox，broker confirm 后标记已发送；AI 结果持久发布成功后才确认输入。结果重复投递由 eventId 或用户资料版本去重，迟到的旧内容版本丢弃。重试、死信与每日 token 预算延迟见 [ai.md](ai.md) 第 4.3 节。
- 评论、帖子喜欢、收藏、评论喜欢、审核通过和等级变更已有 im 消费者；内容事件快照包含生成通知需要的标题和摘要。举报处理和中奖事件仍等待对应业务落地。

## 2. 内部接口

### 2.1 通用约定

- 全部只读。跨服务的写只能通过事件。
- 路径 `/{domain}/internal/**`，网关拦截所有外部流量（包括叠前缀的变体）。
- 被调方处理内部调用时，不许再向外发起同步调用。
- 接口定义放在提供方的 `meng-api-*` 模块；Feign 客户端和降级由调用方在自己的 `client` 包实现。
- 调用方必须设置超时，并对失败给出明确语义，不把失败伪装成成功。

### 2.2 清单

| 接口 | 提供方 | 调用方 | 返回 | 失败语义 |
|---|---|---|---|---|
| `GET /user/internal/users?ids=` | user | content、im | 正常账号的 id、昵称（没填昵称时是用户名）、头像、等级 | 降级成占位昵称和默认头像，列表照常返回；im 发送前需确认对方存在且正常，查询失败时拒绝发送，不伪装成不存在 |
| `POST /ai/internal/creator-reviews` | ai | content | 创作总结和建议 | 返回 503 `AI_UNAVAILABLE` |
| `POST /ai/internal/ranker-trainings` | ai | content | 排序模型的权重和 AUC | 本周不换模型，下周再试 |
| `GET /user/internal/users/{id}/followees` | user | content | 某人关注的人的 id，按关注时间倒序，最多 500 个 | 推荐流跳过「关注」这一路，其余照常 |
| `POST /ai/internal/embeddings` | ai | content | 每段文本的向量 | 语义搜索退回关键词 |
| `POST /ai/internal/tag-suggestions` | ai | content | 3 个标签 | 返回 503 `AI_UNAVAILABLE` |
| `POST /ai/internal/inspirations` | ai | content | 3 个切入角度和开头 | 同上 |
| `POST /ai/internal/covers` | ai | content | 一张图片 | 同上 |
| `POST /ai/internal/growth-summaries` | ai | growth | 总结和建议 | 同上 |

ai 的七个接口是纯计算，不改任何状态，按只读接口对待；因为请求体大，用 POST。输入输出和超时见 [ai.md](ai.md) 第 3.2 节。这些功能里 ai 不调用任何项目内服务，帖子的全文通过事件拿到。

### 2.3 萌萌

前端只经 companion 进入；限次、好感、记忆都在 companion。设计见 [meng.md](meng.md)。

| 接口 | 提供方 | 调用方 | 返回 | 失败语义 |
|---|---|---|---|---|
| `POST /ai/internal/companion/replies` | ai | companion | 内部 SSE：`status` 工具进度；`result` 整轮气泡、作品、好感建议、记忆候选、审核分类；失败为 `error`。Java 原子保存后才转换为公开 SSE，数值建议不返回浏览器 | 超时或 5xx：这一轮不计次，未开始公开流时返回 503 `AI_UNAVAILABLE`，已有进度时通过公开 `error` 事件结束 |
| `POST /ai/internal/companion/summaries` | ai | companion | 一段会话摘要 | 保留旧摘要，下一轮再试 |
| `GET /content/internal/posts/search?q=&size=` | content | ai（萌萌工具） | 公开帖子的 id、标题、摘要、作者昵称，最多 5 篇 | 工具返回「没找到」，角色照常回复 |
| `GET /content/internal/users/{id}/recent-posts?size=` | content | ai（萌萌工具） | 这个用户最近的公开帖子，最多 5 篇 | 同上 |
| `GET /growth/internal/users/{id}/summary` | growth | ai（萌萌工具） | 等级、连续签到天数、今天是否签到 | 同上 |
| `GET /user/internal/users?ids=` | user | companion | 已有接口，取当前用户昵称 | 使用中性称呼「你」，不为此读取登录账号或联系方式 |

- ai 对 content、growth 的调用只发生在萌萌工具里，地址是白名单；用户 id 由 companion 传入，不由模型决定。
- 这是全项目唯一深度为 2 的同步调用，见[架构设计](architecture.md)第 6 节规则 3。

## 3. 新增契约的流程

新增事件或内部接口前，先在本文登记：

1. 调用方、提供方。
2. 读还是写。写操作不允许做成内部接口，只能做成事件。
3. 失败语义。
4. 真实的调用证据，即哪个功能需要它。不做「以后可能用到」的预留。

同步调用还要先检查[架构设计](architecture.md)第 6 节的四条箭头。不在其中的，先考虑改成事件或冗余字段。
