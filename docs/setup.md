# 萌部落社区 · 环境、密钥与约定

> 配套文档：[架构设计](architecture.md)、[事件与内部接口](contracts.md)、[认证模块](auth.md)、[前端规范](frontend.md)。
> 本地中间件怎么启动见 [deploy/README.md](../deploy/README.md)，怎么验证见 [verification/README.md](../verification/README.md)。

## 1. 本机环境

- JDK 17。
- IDEA。编译用它自带的 Maven，本机 PATH 上没有 Maven，不用另装。
- Node 22 和 npm。
- Docker Desktop，能运行 `docker compose`。

## 2. 密钥与环境变量

### 2.1 规则

- 密钥的值只放在用户环境变量或仓库外的「配置清单」里。不进仓库、不进 Nacos、不进日志、不贴进聊天记录。
- 仓库里只出现变量名。`.env` 已加入 `.gitignore`。
- 配置文件里一律写成 `${变量名}` 引用，不给默认值。缺变量时让服务启动失败，而不是带着空值跑起来。
- 核对环境变量时只比对名字，不打印值。
- 下面三类变更按事故处理，不能顺手改：
  - 换 `MENG_JWT_SECRET`：所有人掉线。
  - 换或丢失 `MENG_PII_ENC_KEY`、换 `MENG_PII_HASH_KEY`：已加密的邮箱和手机号读不出来，或查重失效。
  - 改数据库、Redis、RabbitMQ 或 Nacos 的密码：服务起不来。不要用删数据卷的办法解决。

### 2.2 变量清单

项目自己的变量加 `MENG_` 前缀，第三方账号沿用本机已有的名字。
`MENG_` 开头的密钥由 `deploy/scripts/Initialize-LocalEnvironment.ps1` 在不存在时生成，已有的值不会被覆盖；
生成后把值备份到「配置清单」，尤其是两把 PII 密钥。

| 变量 | 用途 |
|---|---|
| `MENG_JWT_SECRET` | 令牌签名 |
| `MENG_PII_ENC_KEY` | 邮箱和手机号加密，AES-256 |
| `MENG_PII_HASH_KEY` | 邮箱和手机号的检索哈希，与加密密钥分开 |
| `MENG_MYSQL_USER_PASSWORD` | `meng_user` 库的应用账号密码 |
| `MENG_MYSQL_CONTENT_PASSWORD`、`MENG_MYSQL_GROWTH_PASSWORD`、`MENG_MYSQL_IM_PASSWORD` | 内容、成长、消息域的独立应用账号密码 |
| `MENG_MYSQL_COMPANION_PASSWORD` | 奶糖助手独立库 `meng_companion` 的应用账号密码 |
| `MENG_COMPANION_WORK_ROOT` | 奶糖助手的私人对话图片目录，默认位于用户 LocalAppData 下，不在仓库内 |
| `MENG_USER_AVATAR_ROOT` | 用户头像目录，默认在用户 LocalAppData 下，不在仓库内 |
| `MENG_IM_IMAGE_ROOT` | 私信配图存储目录，默认在用户 LocalAppData 下，不在仓库内 |
| `MENG_CONTENT_IMAGE_ROOT` | 配图存储目录，默认在用户 LocalAppData 下，不在仓库内 |
| `MYSQL_ROOT_PASSWORD` | 本地 MySQL 的 root，只给初始化脚本用，应用不用 root 连库 |
| `MENG_REDIS_PASSWORD` | Redis |
| `MENG_ES_ELASTIC_PASSWORD` | Elasticsearch 初始化管理员，仅部署脚本使用 |
| `MENG_ES_CONTENT_PASSWORD` | Elasticsearch 的 `meng_content_app` 账号，仅授权帖子关键词、向量索引与别名 |
| `MENG_MYSQL_XXLJOB_PASSWORD` | 调度中心独立库 `xxl_job` 的应用账号密码 |
| `MENG_XXL_JOB_ACCESS_TOKEN` | 执行器直接读取的环境变量；为空或官方默认值时拒绝启动 |
| `MENG_XXL_JOB_ADMIN_PASSWORD` | 调度中心首次 `admin` 账号密码，初始化脚本不覆盖已修改密码 |
| `MENG_RABBITMQ_USERNAME`、`MENG_RABBITMQ_PASSWORD` | RabbitMQ 的应用账号 |
| `MENG_NACOS_ADDR`、`MENG_NACOS_NAMESPACE`、`MENG_NACOS_USERNAME`、`MENG_NACOS_PASSWORD` | Nacos 地址、环境和凭据 |
| `MENG_NACOS_AUTH_TOKEN`、`MENG_NACOS_AUTH_IDENTITY_KEY`、`MENG_NACOS_AUTH_IDENTITY_VALUE` | Nacos 服务端自身的鉴权参数，只给容器用 |
| `MENG_ADMIN_USERNAME`、`MENG_ADMIN_PASSWORD` | 首个管理员账号，只在初始化时读取 |
| `MAIL_USERNAME`、`MAIL_PASSWORD` | SMTP 发信账号 |
| `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET` | 阿里云短信 |
| `DASHSCOPE_API_KEY` | 阿里云 DashScope（Qwen 文本、向量、重排和图像），只给 ai 服务用 |
| `DEEPSEEK_API_KEY` | DeepSeek 官方（V4.1 Flash 1017 与 V4 Pro 角色说话），只给 ai 服务用 |

AI 服务由 `deploy/scripts/Start-Ai.ps1` 启动，使用 uv 和 Python 3.12；从用户环境继承凭据。非密钥运行配置以 `MENG_AI_` 为前缀，默认值在 `python_ai/app/config/settings.py`。模型按用途配置：`AGENT_MODEL`、`SPEECH_MODEL`（默认 `deepseek-flash`，对应官方 DeepSeek V4.1 Flash 1017；DeepSeek 使用 `DEEPSEEK_API_KEY`，Qwen 降级使用 `DASHSCOPE_API_KEY`）、`JUDGE_MODEL`、`REVIEW_MODEL`、`WRITER_MODEL`、`ASSIST_MODEL`、`MEMORY_MODEL`、`VISION_MODEL`、`EMBEDDING_MODEL`、`RERANK_MODEL`、`COVER_MODEL`，每个用途的温度、超时和降级链见 [AI 公共运行时](ai-runtime.md)第 2 节；旧的 `CLASSIFIER_MODEL`、`BRAIN_MODEL`、`COMPANION_MODEL`、`COMPANION_TEMPERATURE`、`SPEECH_STRUCTURED_METHOD`、`DAILY_TOKEN_LIMIT` 删除。其余：`BASE_URL`、`EMBEDDING_URL`、`RERANK_URL`（都是 DashScope 原生接口）、`COVER_URL`、`STRUCTURED_METHOD`（判断类的结构化输出方式）、`CONCURRENCY`、`TIMEZONE`。嘴输出纯文本，不依赖 JSON 和 Function Calling。当前向量维度固定 1024，不通过环境变量改变契约。禁止开启 LangSmith；应用启动强制关闭 tracing。Java 不读取 DashScope 密钥。

AI 启动时读取 `MENG_GROUP/meng-ai.yml`，文件采用 JSON 兼容的 YAML 格式。`model_overrides` 按用途覆盖温度、超时、降级链和 `context_tokens`；上下文限制取配置值与 600K 的较小值，配置值应符合实际模型窗口。`speculative`（环境变量 `MENG_AI_SPECULATIVE`，默认 `true`）控制嘴是否和主 agent 并行预生成；关闭后嘴等待主 agent。`cover_model`（`MENG_AI_COVER_MODEL`）默认 `qwen-image-3.0-pro`，用于 AI 封面。修改后重启 AI 服务。

网关限流配置为 `REQUESTS_PER_MINUTE`、`TOKENS_PER_MINUTE`、`MODEL_QUEUE_SECONDS`、`CIRCUIT_SECONDS`，只保护模型账号的每分钟额度，本机并发限制单独保留。不设 token 上限：`ROUND_TOKEN_LIMIT`、`USER_DAILY_TOKEN_LIMIT`、`DAILY_TOKEN_LIMIT` 及其预算预留与结算都删除（[AI 公共运行时](ai-runtime.md)第 2.1 节）；用量照常按用户、功能记录。`INPUT_PRICE_PER_MILLION`、`OUTPUT_PRICE_PER_MILLION` 和 `CACHED_PRICE_RATIO` 用于估算费用；未配置单价时默认零，不能据此认定调用免费。

companion 执行器端口为 `MENG_COMPANION_JOB_PORT`（默认 9107），只允许调度中心内部转发器访问。任务日志位于图片目录同级的 `companion-job-logs/`。服务本机的 `/actuator/metrics` 提供 `companion.*` 指标，包括首个气泡 P50/P95、降级、重写、token、缓存 token、估算费用和发件箱积压；指标不包含聊天正文。

宿主机端口不是密钥，集中写在 `deploy/ports.env`。

### 2.3 不是密钥，放 Nacos 配置

SMTP 主机和端口、短信签名、各短信模板编号、手机号地区名单和号码格式、各项限流阈值、允许的 `Origin` 名单、
当前生效的用户协议和隐私政策版本号。

## 3. 命名约定

| 项 | 约定 |
|---|---|
| Maven | groupId `com.pluchon.meng`；artifactId 用模块名；版本 `0.1.0-SNAPSHOT` |
| 基础包 | `com.pluchon.meng.common.core`、`com.pluchon.meng.common.web`、`com.pluchon.meng.api.user`、`com.pluchon.meng.gateway`、`com.pluchon.meng.user` |
| 服务名 | `meng-gateway`、`meng-user`、`meng-content`、`meng-growth`、`meng-im`、`meng-companion` |
| 服务端口 | 网关 8080，user 8101，content 8102，growth 8103，im 8104，ai 8106，companion 8107；均监听本机 |
| 前端端口 | 当前仅 `front_user` 5173；管理端前端已移除 |
| 中间件端口 | 见 `deploy/ports.env`。宿主机端口被占用时只改这里的映射 |
| Nacos | namespace `meng-dev`；Group `MENG_GROUP`；Data ID `meng-common.yml`、`meng-gateway.yml`、`meng-user.yml`、`meng-content.yml`、`meng-growth.yml`、`meng-im.yml`、`meng-companion.yml`、`meng-ai.yml` |
| MySQL | 五个库 `meng_user`、`meng_content`、`meng_growth`、`meng_im`、`meng_companion`；各自 `meng_<域>_app` 独立账号，只授权自己的库，字符集 `utf8mb4` |
| 数据库迁移 | Flyway，脚本放各服务自己的 `db/migration`，只前进、不改历史文件 |
| Redis | 键统一加前缀 `meng:` |
| RabbitMQ | vhost `meng`；交换机按 [contracts.md](contracts.md) 的规则命名，user 域是 `meng.user.topic` |
| Cookie | `meng_token` |

没用到的模块不提前建空壳，做到那个域时再建。

## 4. 等外部条件的事

这几项代码已经就绪，开关关着，等条件具备后改 Nacos 配置即可。

- **短信**：国内 `SendSms` 需要签名和验证码模板；国际 `SendMessageToGlobe` 需要 Sender ID 和各地区的通道开通。
  两条通道当前都是关闭的，发短信码的请求返回「短信通道尚未配置」。配置项在 `meng.user.sms` 和 `meng.user.international-sms`。
  向各地区实发和通知类短信的限制还没有实测，见[架构设计](architecture.md)第 10 节的待核实。
- **正式协议**：用户协议和隐私政策的正式条款、运营者名称、联系邮箱、适用地区还没有。
  `meng.user.legal.published` 为 `false` 时注册提交返回「正式协议尚未发布」。条款正文放在用户端的 `src/content/legal/`。
- **邮件**：SMTP 认证已验证，发信延迟正常；进垃圾箱的概率取决于发信账号的服务商。

## 5. 已知情况

- Flyway 对 MySQL 8.4 会提示「高于已验证的 8.1」，迁移和读写实测正常。
- 前端开发依赖 Stylelint 间接引入的 `braces` 有一条栈耗尽公告，上游没有修复版本；只在开发期使用，生产依赖扫描为零漏洞。
