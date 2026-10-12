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
- 事件消息体的类定义放在生产者的 `meng-api-*` 模块里。

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
| `post.published` | content | postId、authorId | growth：加经验；ai：建向量索引 |
| `post.updated` | content | postId | ai：重建该帖索引 |
| `post.deleted` | content | postId | ai：删索引 |
| `comment.created` | content | commentId、postId、postTitle、authorId、targetUserId、parentCommentId、excerpt、originalExcerpt | im：通知被评论或被回复的人；growth：加经验 |
| `post.liked` | content | postId、postTitle、actorId、authorId | im：通知作者 |
| `post.favorited` | content | postId、postTitle、actorId、authorId | im：通知作者 |
| `comment.liked` | content | commentId、postId、postTitle、excerpt、actorId、authorId | im：通知评论作者 |
| `report.handled` | content | reportId、reporterId、targetType、targetId、postId、postTitle、excerpt、targetAuthorId、result | im：通知举报人；成立时通知内容作者 |
| `growth.level-changed` | growth | userId、level | user：同步展示用等级；im：升级通知 |
| `lottery.won` | growth | userId、prizeName | im：中奖通知 |

说明：

- 没有「用户资料变更」事件。昵称和头像走批量内部接口现查，没有消费者就不留事件。
- 签到不是事件，它是 growth 自己的写接口。
- 取消喜欢、取消收藏不发事件，已经发出的通知不撤回。
- 审核被拒绝不发事件，由 user 自己发通知邮件或短信。
- growth 的成长账户按需创建，不靠 `user.approved` 初始化，见[成长域](growth.md)。
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
| `GET /content/internal/posts/{id}` | content | ai | 标题、正文、作者、状态 | MQ 重试，超限进死信 |
| `GET /content/internal/posts?cursor=&size=` | content | ai | 同上，游标分页 | 重建任务中断，可续跑 |

第三条用于向量索引的全量重建。

### 2.3 尚未覆盖

AI 萌萌的对话链路（前端经哪个 Java 服务进入、配额在哪扣）还没设计，等会话归属确定后补进本文。

## 3. 新增契约的流程

新增事件或内部接口前，先在本文登记：

1. 调用方、提供方。
2. 读还是写。写操作不允许做成内部接口，只能做成事件。
3. 失败语义。
4. 真实的调用证据，即哪个功能需要它。不做「以后可能用到」的预留。

同步调用还要先检查[架构设计](architecture.md)第 6 节的三条箭头。不在其中的，先考虑改成事件或冗余字段。
