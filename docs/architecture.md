# 萌部落社区 · 架构设计

> 配套文档：[事件与内部接口](contracts.md)、[认证模块](auth.md)、[内容域](content.md)、[成长域](growth.md)、[消息域](im.md)、[AI 域](ai.md)、[前端规范](frontend.md)、[环境、密钥与约定](setup.md)。

## 1. 定位

- 多用户社区：谁都能注册，但注册后进入待审核，管理员通过后才能登录。
- 风格：日漫萌系、水色主题。
- 功能：发帖、私信、成长、站内 AI（推荐、语义搜索、审核、发帖辅助）和主页的奶糖助手。
- 性质：兴趣项目，同时是多种技术的融合练习。首要目标是**易维护、易扩展**，
  避免旧论坛「同步调用连成网、服务间调用混乱」的问题。

## 2. 技术栈

| 项 | 选型 | 说明 |
|---|---|---|
| JDK | 17 | Spring Boot 4 的最低要求；本机 PATH 上是 17.0.9 |
| 后端框架 | Spring Boot 4.0.8 | 钉在 4.0.x；已与下列 Cloud/Alibaba 组合完成启动探针 |
| 微服务 | Spring Cloud 2025.1.0 + Spring Cloud Alibaba 2025.1.0.0 | Nacos 3.1.1 配置导入、服务注册、Gateway 鉴权及内部路径封锁实测通过 |
| 持久层 | MyBatis-Plus 3.5.17 | `mybatis-plus-spring-boot4-starter`；真实 MySQL 迁移与读写探针通过 |
| 工具库 | Hutool、Lombok | |
| 关系库 | MySQL 8 | 每个服务独占一个库 |
| 数据库迁移 | Flyway 11.14.1（Boot 4.0.8 BOM 管理） | `spring-boot-starter-flyway` + `flyway-mysql`；每个服务管自己的库，只前进、不改历史文件 |
| 行为验证码 | tianai 1.5.5 | Boot 4.0.8 独立探针生成滑块、轨迹校验及重放拒绝通过 |
| 缓存 | Redis | 会话、限流、缓存 |
| 消息 | RabbitMQ | 跨域事件、延迟任务 |
| 搜索与向量 | Elasticsearch | 关键词检索和向量检索共用，不上 Milvus |
| 定时任务 | xxl-job | 执行器嵌在各域服务里 |
| 短信 | 阿里云短信 | 境外号码走国际/港澳台短信；只在 user 服务里用 |
| 邮件 | Spring Boot 邮件 starter + SMTP | 只在 user 服务里用 |
| 图片存储 | 本机磁盘 | 帖子配图，只在 content 服务里用；读写集中在一个存储类，以后换对象存储只动它 |
| IP 属地 | ip2region 离线库 | content 记帖子和评论的属地，user 记登录设备的属地；查询封装放公共模块，进程内查询，不调外部接口 |
| AI 服务 | Python 3.12 + FastAPI + LangChain / LangGraph 1.x | 不使用 Spring AI；编排写法见 [ai.md](ai.md) 第 3 节 |
| 模型 | 全部经阿里云百炼：DeepSeek V4.1 Flash 1017 快照（奶糖说话、看图和联网搜索）、Qwen3.8 Flash / Max、Qwen3.7 Embedding Flash、Qwen3.7 Rerank、Qwen Image 3.0 Pro | 对话走 OpenAI 兼容接口，联网搜索走 Responses API，向量和重排走原生接口，千问同步生图；型号按用途靠配置切换，见 [ai-runtime.md](ai-runtime.md) |
| 前端 | Vue 3 + vue-router + Pinia + Axios + Element Plus + SCSS | 当前只保留用户端项目；管理端仅保留文档规格，见 [frontend.md](frontend.md) |
| 容器 | Docker Compose | Kibana 只在本地按需启动 |

## 3. 仓库结构

```
meng_center/
├── deploy/          本地中间件的 Compose 和初始化脚本
├── docs/            文档
├── java_backend/    Java 后端（Maven 多模块）
├── python_ai/       Python AI 服务 meng-ai
└── vue_front/       前端
    └── front_user/   用户端；管理端实现已移除
```

Maven groupId 和 Java 基础包名都是 `com.pluchon.meng`。

## 4. Java 模块结构

每个业务域就是一个 server 模块，直接放在顶层；所有域的对外契约集中放在 `meng-api` 下，
**不采用**「每个域目录下成对放 api + server」的布局。

```
java_backend/
├── pom.xml                  父工程，统一管理版本
├── meng-common/
│   ├── meng-common-core/    统一响应、异常与业务码、JWT 工具、分页、事件信封
│   └── meng-common-web/     身份拦截器、用户上下文、全局异常处理
├── meng-api/                所有域的对外契约
│   ├── meng-api-user/
│   ├── meng-api-content/
│   └── meng-api-growth/
├── meng-gateway/            网关
├── meng-user/               用户域
├── meng-content/            内容域
├── meng-im/                 私信与通知域
├── meng-growth/             成长域
└── meng-companion/          奶糖助手的会话与记忆域
```

`meng-im` 通过网关向前端提供消息接口，没有被其他服务调用的内部接口，也不生产跨服务事件，因此没有 `meng-api-im`。

### 4.1 api 模块放什么

- **放**：内部 HTTP 接口定义（含路由和校验注解）、内部调用用的 DTO / VO、事件消息体、契约枚举、错误语义。
- **不放**：`@FeignClient`、Entity、Mapper、Service 实现、业务 Converter、中间件配置。
- Feign 客户端及其降级由**调用方**在自己的 `client` 包里实现。

### 4.2 模块依赖规则

| 模块 | 允许依赖 |
|---|---|
| `meng-common-core` | 无项目内依赖 |
| `meng-common-web` | `meng-common-core` |
| `meng-api-*` | 只能依赖 `meng-common-core`，彼此之间不许依赖 |
| `meng-gateway` | 只能依赖 `meng-common-core` |
| 业务 server | `meng-common-*`、自己的 api、[依赖表](#7-依赖表)允许的别域 api |

- 业务 server 之间不许互相依赖，Maven 上直接编译不过。
- 网关是响应式的，不能引入 `meng-common-web`，也不能被带进数据库相关依赖。
- common 不承载任何业务归属，不依赖任何 api 或 server。

### 4.3 server 内部分层

`Controller → Service → Mapper`，不跨层。

- Controller 只做参数绑定、调用 Service、返回统一响应；入参用 DTO，出参用 VO，不直接返回 Entity。
- Service 是授权、事务、状态流转的最终裁决者。
- 对象转换放 `converter` 包；跨服务调用放 `client` 包。
- content、growth 的 Spring 依赖使用 `@Autowired` 字段注入；事务显式声明 `rollbackFor = Exception.class`。类、字段和 Service 方法用中文单行注释，Controller 接口用一句话 Javadoc；不使用通配符导入，每行只写一条语句或一个依赖。
- content 的新实体计数在字段处初始化为零。DTO 与实体同名同类型字段由 converter 使用 Hutool 5.8.47 `BeanUtil.copyProperties` 拷贝，record 来源已经实测；归一化、摘要、属地和当前用户仍显式赋值，每个转换方法用单元测试覆盖字段。

## 5. 服务划分

按业务域拆，不按「B 端 / C 端 / 定时任务端」拆。

| 服务 | 负责 |
|---|---|
| gateway | 路由、统一鉴权、身份头注入、内部路径拦截 |
| user | 账号、登录、资料、注册审核、关注关系 |
| content | 帖子、评论、点赞、收藏、搜索索引同步 |
| im | 私信、通知（WebSocket） |
| growth | 经验、等级、签到、积分 |
| companion | 奶糖助手：角色卡、会话编排、长期记忆与整理任务、限次，见 [meng.md](meng.md) |
| ai（Python） | 奶糖一轮的图；帖子理解、审核子图、向量计算、标签与灵感、封面、成长与创作总结等纯计算，见 [ai.md](ai.md) 和 [ai-runtime.md](ai-runtime.md) |

- **后台管理接口**：各域服务里单独一组 Controller，路径如 `/content/admin/**`，
  网关统一校验管理员角色，Service 再校验一次。管理端前端已移除，管理员接口仍在，规格保留在文档里。
- **定时任务**：xxl-job。调度中心是一个独立的容器，执行器嵌在有任务的域服务里，任务写在拥有那份数据的域。不设独立的定时任务服务，也不再用 Spring 的 `@Scheduled`。
  - 调度中心用官方镜像 3.5.0，按摘要固定。低于 3.4.0 的版本有已公开的安全漏洞，不用。
    它只监听本机；容器内端口是 8080，和网关相同，宿主机端口另取并写在 `deploy/ports.env`。容器时区设为 Asia/Taipei，任务的 cron 按这个时区理解。
  - 调度中心用同一个 MySQL 里的独立库 `xxl_job` 和独立账号 `meng_xxl_job_app`，密码来自 `MENG_MYSQL_XXLJOB_PASSWORD`。表结构用该版本官方的建表脚本，不走 Flyway。
  - 调度中心和执行器之间的令牌来自 `MENG_XXL_JOB_ACCESS_TOKEN`。初始化时把控制台默认管理员的口令换成 `MENG_XXL_JOB_ADMIN_PASSWORD`，不留默认口令。
  - 只有真的有任务的服务才引执行器（`xxl-job-core`，和调度中心同一个版本）。执行器名用服务名，例如 `meng-content`；端口取服务端口加 1000，content 是 9102。官方 3.5.0 执行器绑定所有网卡，`setIp` 只影响注册地址；靠访问令牌和宿主机防火墙保护，禁止向外开放执行器端口。令牌必须直接来自环境变量 `MENG_XXL_JOB_ACCESS_TOKEN`，为空或等于官方默认值 `default_token` 时服务启动失败并明确提示。注册地址保持 `http://127.0.0.1:9102/`，Compose 不映射执行器端口；调度中心通过同网络空间、仅绑定容器回环地址的转发器访问宿主机执行器。
  - 任务处理器放各服务的 `job` 包，只调对应的 Service 方法，不写业务逻辑。处理器名是「域加动作」，例如 `contentHotRebuild`。
  - 任务定义（cron、路由、阻塞策略、超时、重试）用初始化脚本写进调度中心的库，脚本进仓库，不靠人在控制台里点出来。
  - 默认策略：上一次没跑完时丢弃后续调度，错过的不补跑，失败重试 1 次。任务本身仍要幂等并带互斥锁，因为同一个动作还可能被启动流程或管理接口触发。
  - 调度中心不可用时，服务照常启动、照常对外，只是定时任务不触发。
- **不再细分**：不单拆评论、搜索、通知、认证服务。拆得越细同步调用越多。

### 边界的理由

- user：所有域都依赖它，必须独立且不依赖任何业务域。
- content：帖子、评论、点赞在同一个事务里变化。
- im：WebSocket 长连接的运行特征不同，挂了不该拖累发帖。
- growth：靠消费事件加经验，与其他域天然解耦。

## 6. 防耦合规则

1. **依赖单向分层，不成环，同层不互调。**
   user 在底层，不调任何项目内服务；content、im、companion 同步调 user，content、growth、companion 同步调 ai 做计算；除奶糖助手的对话外，ai 不被写路径同步调用。
2. **同步调用全项目只有这几条箭头**：content → user、im → user、companion → user、content → ai、growth → ai、companion → ai；以及奶糖站内工具专用的 ai → content、ai → growth（只读）。
3. **同步调用只读、深度为 1。** 内部接口只做查询；处理内部调用时不许再向外发起同步调用。跨服务的写只能通过事件。
   唯一的例外是奶糖助手：companion → ai → content / growth 深度为 2。只有奶糖一轮的图里的站内工具能调，接口在 ai 里写成白名单，content、growth 处理这些调用时不再外调，也永远不调 companion，所以不成环。见 [meng.md](meng.md) 第 5.3 节。
4. **「发生了一件事，别人要跟着反应」一律走 MQ 事件。** 事务提交后发送；生产者不知道谁在消费。
5. **每个服务独占一个 MySQL 库和独立账号。** 物理上杜绝跨服务联表和共享 Entity。
6. **一次写操作只落在一个服务，不做分布式事务。** 必须同事务的放同一个域；跨域最终一致，消费者幂等。
7. **展示用的别域数据靠批量内部接口或事件同步的冗余字段。** 不在循环里逐条调。
   帖子列表的作者昵称、头像、等级统一问 user；等级由 growth 算出后发事件同步进 user。
8. **AI 只做计算。** 权限、配额、会话等权威状态归 Java，Python 不绕过业务域改状态。

### 用机制守住

- Maven 模块依赖：见 4.2。
- 架构测试：用 ArchUnit 断言包依赖规则，作为不变量测试长期保留。
- 分库分账号：见规则 5。
- 新增同步调用前先改[依赖表](#7-依赖表)。加不进去，说明边界划错了或该走事件。

## 7. 依赖表

| 服务 | 可同步调 | 发出的事件 | 消费的事件 |
|---|---|---|---|
| user | 无 | 审核通过、关注、资料变更、资料审核状态变更 | 等级变更、资料审核结果 |
| content | user、ai | 发帖、改帖、删帖、评论、点赞、收藏、评论点赞、举报处理、请求分析、审核状态变更 | 帖子分析结果、评论审核结果 |
| im | user | 无 | 审核通过、关注、评论、点赞、收藏、评论点赞、举报处理、等级变更、内容审核状态变更、资料审核状态变更 |
| growth | ai | 等级变更 | 发帖、评论 |
| companion | user、ai | 无 | 无 |
| ai | content、growth（只限奶糖的站内工具，只读） | 帖子分析结果、评论审核结果、资料审核结果 | 发帖、改帖、请求分析、评论、资料变更 |

事件和内部接口的明细见 [contracts.md](contracts.md)。

## 8. 数据约定

- 库名：`meng_user`、`meng_content`、`meng_im`、`meng_growth`、`meng_companion`，同一个 MySQL 实例。
- 主键 BIGINT 自增，不用雪花 ID 和 UUID。
- 业务表都带 `create_time`、`update_time`、`delete_state`，默认逻辑删除。
- 全局唯一的字段加唯一索引，入库前归一化（去空格、邮箱转小写、手机号转成带区号的统一格式）。
- 邮箱和手机号加密存储，不落明文，唯一性靠哈希列保证（见 [auth.md](auth.md) 4.1 节）。
- 业务状态用枚举表示，只能通过 Service 方法变更。
- 帖子的关键词索引和向量索引都是可重建的派生数据，都归 content；向量由 ai 计算后经事件交给 content。

## 9. 接口约定

- RESTful：路径是资源名词，动作用 HTTP 方法表达。
- 失败用真实的 HTTP 状态码（400 / 401 / 403 / 409 / 429 等），不统一返回 200。
- 内部接口路径一律 `/{domain}/internal/**`，网关拦截所有外部流量。
- 身份只来自网关注入的请求头，不信任前端传的用户 ID 和角色。
- 项目自己的环境变量统一加 `MENG_` 前缀，避免和本机其他项目互相覆盖；第三方账号（阿里云、SMTP 等）沿用本机已有的变量名。
- 日志不打印密码、验证码、JWT、票据；邮箱和手机号脱敏。

## 10. 暂不做、待定与待核实

各域的暂不做和悬而未决的事都集中在这里。群聊、抽奖、用积分送礼已经放弃，以后要做再重新设计。

### 暂不做

| 域 | 项 |
|---|---|
| 全局 | 上线服务器：按当前组件量 8G 才宽裕，4G 需要砍 xxl-job 并压缩业务服务数 |
| 全局 | 链路追踪（要上就用 Zipkin）、Sentinel（有证据再上，不部署控制台） |
| 全局 | 管理端前端（注册审核、举报处理、帖子管理、AI 审核复核的页面）；AI 审核的结论和管理员接口见 [ai.md](ai.md) 第 12 节 |
| 认证 | Cloudflare、浏览器指纹、封禁、第三方登录、注销账号 |
| 内容 | 举报处理、帖子管理 |
| 成长 | 连续签到奖励、补签（连续天数只显示）、用户排行榜 |
| 消息 | 「对方正在输入」、私信举报（防骚扰靠陌生人限制和屏蔽）、举报处理通知 |

### 待定

| 域 | 项 | 当前倾向 |
|---|---|---|
| 全局 | LangGraph 图执行态是否持久化 | 不接；奶糖的会话状态由 companion 保存，每次请求带上下文 |
| 内容 | 没挂到帖子上的图片 | 本期不清理。以后加一个 xxl-job 任务来清，要放过被草稿引用的图片 |
| 内容 | 表情包 | 目录由 `emotion` 表和 `catalog.json` 维护（[content.md](content.md) 第 5.11 节）；表情包管理页面和用户上传暂未开放 |
| 内容 | 评论按热度排序 | 本期只按时间。有需要时用评论的赞数排 |
| 内容 | 热度的时间衰减 | 本期没有，靠「近 7 天」这条线。发现热门总是那几条老帖时，在重算里给分数乘一个随发布时间下降的系数 |
| 内容 | 分词的自定义词库 | 本期不建。发现番名、角色名被拆散搜不到时，给 IK 加词库文件并重建索引 |
| 内容 | 搜评论、搜用户、搜索联想和搜索历史 | 暂缓。帖子的语义搜索见 [ai.md](ai.md) 第 9 节 |
| 内容 | 大陆 IP 的属地要不要细到省 | 现在统一到国家或地区一级 |
| 内容 | 创作中心以后可以加的 | 按时间段筛选的帖子表现。AI 复盘见 [ai.md](ai.md) 第 11.2 节 |
| 内容 | 视频 | 暂缓，每帖最多一个。本机磁盘和带宽撑不住，做的时候先定存储、大小和时长上限、要不要转码 |
| 内容 | 配图拖动排序 | 本期不做，顺序就是上传的先后 |
| 成长 | 经验数值和等级门槛 | [growth.md](growth.md) 第 4 节是起步取值，改配置即可，不影响表结构；满级后经验继续累加，等级停在最高一级 |
| 成长 | 被喜欢要不要给作者加经验 | 本期不加，容易被互刷 |
| 消息 | 通知和未挂载图片的清理 | 本期不清理。以后加 xxl-job 任务 |
| 消息 | 多实例 | 方向见 [im.md](im.md) 第 2 节，本期不写代码 |
| 前端 | 繁体中文语言包 | 暂缓；结构已支持，加一个语言目录即可 |
| 奶糖助手 | 用户自己的 API key | 用户在设置里填自己的百炼 key，网关按用户取凭据；页面和存储另做 |
| 奶糖助手 | 更多表情 | 需要美术素材；加到全站表情目录里，评论、私信和奶糖共用 |
| 奶糖助手 | 记忆向量迁到 Elasticsearch | 单个用户的记忆超出 Java 内计算的范围后再迁 |
| 奶糖助手 | 真实用户盲测 | 上线后用按用户分桶灰度，两组只差一个变量；看平均每次对话的轮数、次日回访；判定阈值和最少样本数开测前写定；只统计计数，不读对话正文 |
| 奶糖助手 | 公开基准对标 | 人设用 [CharacterEval](https://preview.aclanthology.org/add_acl24_videos/2024.acl-long.638) 的公开数据，记忆用 LongMemEval 的五类问题和 LoCoMo 的长对话问题，在自己的评测框架里跑；各厂商自报的分数测法不同，不直接比较 |
| 奶糖助手 | 大模型观测平台（如 Langfuse） | 对话量上来、服务器扩容后再加；现在用结构化日志和本地每日看板，见 [AI 公共运行时](ai-runtime.md)第 11 节 |

### 待核实

- MySQL 8.4 高于 Flyway 11.14.1 已验证的 8.1，启动时有提示。
- 百炼上的 `deepseek-v4.1-flash-1017`：`enable_thinking=false` 的实际效果（当前响应未返回推理 token 指标）、联网搜索到最终回复的完整链路；文字、图片输入及缓存用量记录已确认。隐式缓存命中率目标在下一次授权验收时重新确认（[AI 公共运行时](ai-runtime.md)第 4.5、14 节）。
- 阿里云国际短信向八个地区的实际送达、Sender ID 要求和账户通道开通情况。
- 阿里云对通知类短信（审核结果）的限制，尤其是个人认证的账号和境外号码。发不了的情况下只发验证码，审核结果让用户登录时看。
