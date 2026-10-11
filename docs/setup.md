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
| `MENG_MYSQL_CONTENT_PASSWORD`、`MENG_MYSQL_GROWTH_PASSWORD` | 两个新域的独立应用账号密码 |
| `MENG_CONTENT_IMAGE_ROOT` | 配图存储目录，默认在用户 LocalAppData 下，不在仓库内 |
| `MYSQL_ROOT_PASSWORD` | 本地 MySQL 的 root，只给初始化脚本用，应用不用 root 连库 |
| `MENG_REDIS_PASSWORD` | Redis |
| `MENG_ES_ELASTIC_PASSWORD` | Elasticsearch 初始化管理员，仅部署脚本使用 |
| `MENG_ES_CONTENT_PASSWORD` | Elasticsearch 的 `meng_content_app` 账号，仅授权帖子索引与别名 |
| `MENG_RABBITMQ_USERNAME`、`MENG_RABBITMQ_PASSWORD` | RabbitMQ 的应用账号 |
| `MENG_NACOS_ADDR`、`MENG_NACOS_NAMESPACE`、`MENG_NACOS_USERNAME`、`MENG_NACOS_PASSWORD` | Nacos 地址、环境和凭据 |
| `MENG_NACOS_AUTH_TOKEN`、`MENG_NACOS_AUTH_IDENTITY_KEY`、`MENG_NACOS_AUTH_IDENTITY_VALUE` | Nacos 服务端自身的鉴权参数，只给容器用 |
| `MENG_ADMIN_USERNAME`、`MENG_ADMIN_PASSWORD` | 首个管理员账号，只在初始化时读取 |
| `MAIL_USERNAME`、`MAIL_PASSWORD` | SMTP 发信账号 |
| `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET` | 阿里云短信 |

宿主机端口不是密钥，集中写在 `deploy/ports.env`。

### 2.3 不是密钥，放 Nacos 配置

SMTP 主机和端口、短信签名、各短信模板编号、手机号地区名单和号码格式、各项限流阈值、允许的 `Origin` 名单、
当前生效的用户协议和隐私政策版本号。

## 3. 命名约定

| 项 | 约定 |
|---|---|
| Maven | groupId `com.pluchon.meng`；artifactId 用模块名；版本 `0.1.0-SNAPSHOT` |
| 基础包 | `com.pluchon.meng.common.core`、`com.pluchon.meng.common.web`、`com.pluchon.meng.api.user`、`com.pluchon.meng.gateway`、`com.pluchon.meng.user` |
| 服务名 | `meng-gateway`、`meng-user`、`meng-content`、`meng-growth` |
| 服务端口 | 网关 8080，user 8101，content 8102，growth 8103；均监听本机 |
| 前端端口 | `front_user` 5173，`front_admin` 5174 |
| 中间件端口 | 见 `deploy/ports.env`。宿主机端口被占用时只改这里的映射 |
| Nacos | namespace `meng-dev`；Group `MENG_GROUP`；Data ID `meng-common.yml`、`meng-gateway.yml`、`meng-user.yml`、`meng-content.yml`、`meng-growth.yml` |
| MySQL | 三个库 `meng_user`、`meng_content`、`meng_growth`；各自 `meng_<域>_app` 独立账号，只授权自己的库，字符集 `utf8mb4` |
| 数据库迁移 | Flyway，脚本放各服务自己的 `db/migration`，只前进、不改历史文件 |
| Redis | 键统一加前缀 `meng:` |
| RabbitMQ | vhost `meng`；交换机按 [contracts.md](contracts.md) 的规则命名，user 域是 `meng.user.topic` |
| Cookie | `meng_token` |

没用到的模块不提前建空壳，做到那个域时再建。

## 4. 等外部条件的事

这几项代码已经就绪，开关关着，等条件具备后改 Nacos 配置即可。

- **短信**：国内 `SendSms` 需要签名和验证码模板；国际 `SendMessageToGlobe` 需要 Sender ID 和各地区的通道开通。
  两条通道当前都是关闭的，发短信码的请求返回「短信通道尚未配置」。配置项在 `meng.user.sms` 和 `meng.user.international-sms`。
  向各地区实发和通知类短信的限制还没有实测，见 [auth.md](auth.md) 文末的待核实。
- **正式协议**：用户协议和隐私政策的正式条款、运营者名称、联系邮箱、适用地区还没有。
  `meng.user.legal.published` 为 `false` 时注册提交返回「正式协议尚未发布」。条款正文放在用户端的 `src/content/legal/`。
- **邮件**：SMTP 认证已验证，发信延迟正常；进垃圾箱的概率取决于发信账号的服务商。

## 5. 已知情况

- Flyway 对 MySQL 8.4 会提示「高于已验证的 8.1」，迁移和读写实测正常。
- 前端开发依赖 Stylelint 间接引入的 `braces` 有一条栈耗尽公告，上游没有修复版本；只在开发期使用，生产依赖扫描为零漏洞。
