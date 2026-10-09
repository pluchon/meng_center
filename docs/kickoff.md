# 萌部落社区 · 开工清单

> 2026-10-09 定稿。这是搭骨架的执行清单：先做完第 3 节的准备，再按第 7 节的顺序搭，每一步过了验收点再做下一步。
> 配套文档：[架构设计](architecture.md)、[事件与内部接口](contracts.md)、[认证模块](auth.md)、[前端规范](frontend.md)。
> 本文和上面几份冲突时，以那几份为准，并回来改本文。

## 1. 第一阶段的范围

目标只有一个：**「注册 → 管理员审核 → 登录」在浏览器里走通。**

| | 内容 |
|---|---|
| 要建 | 父工程、`meng-common-core`、`meng-common-web`、`meng-api-user`、`meng-gateway`、`meng-user`、`front_user`（登录页、注册页、协议页、重置密码页，后续用户授权追加）、`front_admin`（登录、注册申请列表） |
| 中间件 | MySQL、Redis、RabbitMQ、Nacos |
| 不建 | `meng-content`、`meng-im`、`meng-growth`、对应的 api 模块、Python AI 服务、Elasticsearch、xxl-job |

没用到的模块不提前建空壳，做到那个域时再建。

## 2. 已定的事

以下都在 2026-10-09 确认，细节在对应文档里。

| 项 | 结论 |
|---|---|
| Maven groupId 和 Java 基础包名 | `com.pluchon.meng` |
| 数据库迁移 | Flyway，脚本放各服务自己的 `db/migration`，只前进、不改历史文件 |
| Compose 和初始化脚本的位置 | 仓库根目录的 `deploy/` |
| 环境变量命名 | 第三方账号沿用本机已有的名字，项目自己的加 `MENG_` 前缀 |
| Token 存放位置 | HttpOnly Cookie |
| 注册方式 | 邮箱或手机号二选一；有确认密码；不填昵称；必须勾选同意用户协议和隐私政策 |
| 邮箱和手机号 | 都加密存储，不落明文 |
| 手机号地区 | 台湾、香港、澳门、中国大陆、日本、新加坡、马来西亚、美国和加拿大，默认台湾 |
| 短信 | 阿里云短信，账号在中国站 |
| 邮件 | Spring Boot 邮件 starter，走现有的 SMTP 账号 |
| 被拒绝之后 | 能重新申请，同一条记录改回待审核 |
| 申请理由和拒绝原因 | 两个字段都加 |
| 协议同意记录 | `user` 表加三列，记最近一次同意的两个版本号和时间，不另建表 |
| 主题 | 第一阶段的两个页面只做亮色，`tokens.scss` 里留好 `html.dark` 的位置 |

## 3. 开工前的准备

这一节的事只有仓库主人能做，做完再开始搭。

### 3.1 本机环境

- [ ] JDK 17。本机已有 17.0.9。
- [ ] IDEA。编译用它自带的 Maven，本机 PATH 上没有 Maven，不用另装。
- [ ] Node 22 和 npm。
- [ ] Docker Desktop，能运行 `docker compose`。
- [ ] Git。已有，仓库已关联 `pluchon/meng_center` 的 `main`。

### 3.2 阿里云短信

- [ ] 在中国站开通短信服务，国内短信和国际/港澳台短信都要开。
- [ ] 为国内 `SendSms` 申请签名和验证码模板；需要审核结果短信时另申请对应通知模板。
- [ ] 为国际 `SendMessageToGlobe` 确认 Sender ID、正文和各地区准入要求，不使用国内模板接口。
- [ ] 将这些非密钥配置填入 Nacos 的 `sms` 和 `international-sms`；实际完成前保持通道关闭。
- [ ] 确认账号的认证类型能不能申请通知类模板。不能的话只保留验证码模板，审核结果让用户登录时看。

### 3.3 邮件

- [ ] 在桌面的「配置清单」里确认 SMTP 的主机、端口和加密方式。
- [ ] 用这个账号手动发一封测试信，确认能收到，没有进垃圾箱。

### 3.4 密钥

下面的命令生成三把密钥并直接写进用户环境变量，不在屏幕上显示。已经存在的会跳过。

```powershell
foreach ($name in 'MENG_JWT_SECRET', 'MENG_PII_ENC_KEY', 'MENG_PII_HASH_KEY') {
    if ([Environment]::GetEnvironmentVariable($name, 'User')) {
        "$name 已存在，跳过"
        continue
    }
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [Environment]::SetEnvironmentVariable($name, [Convert]::ToBase64String($bytes), 'User')
    "$name 已生成"
}
```

- [ ] 运行上面的命令。
- [ ] 把三把密钥的值备份到「配置清单」。丢了 `MENG_PII_ENC_KEY`，库里的邮箱和手机号永久读不出来。
- [ ] 新建第 5.3 节里其余的 `MENG_` 变量。
- [ ] 重新打开终端和 IDEA，新变量才会生效。

密钥只生成一次。之后任何「重新生成」都按事故处理，见第 5.1 节。

### 3.5 设计稿

- [ ] 从画布重新导出登录页和注册页到 `vue_front/front_user/pages_design/`。
  仓库里现有的导出早于地区下拉、申请理由、重新申请和同意勾选这几处改动，协议页参考已补充到 Legal.dc.html。
- [ ] 准备用户协议和隐私政策的正式条款，定下运营者名称、生效日期、联系邮箱和适用法律。设计稿里这些都是占位符。

## 4. 先验证再锁版本

以下依赖对 Spring Boot 4 的适配情况没有核实过。做法：建一个只引这一个依赖的最小工程，能启动、能完成一次核心调用才算通过。
通过后把锁定的版本号写回 [architecture.md](architecture.md) 第 2 节，并从第 10 节的「待核实」里划掉。

| 依赖 | 要确认的事 | 用在哪一步 |
|---|---|---|
| Spring Boot 4.0.x、Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.0.0 | 三者的版本组合，以及对应的 Nacos 服务端版本 | 第 2、3、4 步 |
| `mybatis-plus-spring-boot4-starter` | 3.5.13 及以上的可用版本 | 第 5 步 |
| Flyway | Boot 4 下的 starter 和 MySQL 支持模块 | 第 5 步 |
| tianai 行为验证码 | 能否在 Boot 4 下生成和校验滑块 | 第 7 步 |
| 阿里云短信 SDK | 用 `SendSms` 向台湾号码实发一条验证码；记下境外号码的格式和模板类型 | 第 7 步 |

第一阶段不用 xxl-job 和 Elasticsearch，它们的兼容性留到用的时候再验。
任何一项不通过，先停下来换方案，不要绕过去继续搭。

## 5. 密钥与环境变量

### 5.1 规则

- 密钥的值只放在用户环境变量或桌面的「配置清单」里。不进仓库、不进 Nacos、不进日志、不贴进聊天记录。
- 仓库里只出现变量名。`.env` 已加入 `.gitignore`。
- 配置文件里一律写成 `${变量名}` 引用，不给默认值。缺变量时让服务启动失败，而不是带着空值跑起来。
- 核对环境变量时只比对名字，不打印值。
- 下面三类变更按事故处理，不能顺手改：
  - 换 `MENG_JWT_SECRET`：所有人掉线。
  - 换或丢失 `MENG_PII_ENC_KEY`、换 `MENG_PII_HASH_KEY`：已加密的数据读不出来，或查重失效。
  - 改数据库或 RabbitMQ 的密码：服务起不来。

### 5.2 本机已有

2026-10-09 核对过用户环境变量，只看了名字，没有读值。

| 变量 | 用途 |
|---|---|
| `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET` | 阿里云短信 |
| `MAIL_USERNAME`、`MAIL_PASSWORD` | SMTP 发信账号 |
| `MYSQL_ROOT_PASSWORD` | 本地 MySQL 的 root，只给初始化脚本用，应用不用 root 连库 |
| `DASHSCOPE_API_KEY` | Qwen 模型，第一阶段不用 |

### 5.3 需要新建

| 变量 | 用途 |
|---|---|
| `MENG_JWT_SECRET` | JWT 签名。第 3.4 节的命令生成 |
| `MENG_PII_ENC_KEY` | 邮箱和手机号加密，AES-256。第 3.4 节的命令生成 |
| `MENG_PII_HASH_KEY` | 邮箱和手机号哈希，与加密密钥分开。第 3.4 节的命令生成 |
| `MENG_MYSQL_USER_PASSWORD` | `meng_user` 库的应用账号密码 |
| `MENG_REDIS_PASSWORD` | Redis |
| `MENG_RABBITMQ_USERNAME`、`MENG_RABBITMQ_PASSWORD` | RabbitMQ 的应用账号 |
| `MENG_NACOS_ADDR`、`MENG_NACOS_NAMESPACE`、`MENG_NACOS_USERNAME`、`MENG_NACOS_PASSWORD` | Nacos 地址、环境和凭据 |

### 5.4 不是密钥，放 Nacos 配置

SMTP 主机和端口、短信签名、各短信模板编号、手机号地区名单和号码格式、各项限流阈值、允许的 `Origin` 名单、当前生效的用户协议和隐私政策版本号。

写本文时没有打开桌面的「配置清单」。SMTP 主机这类值是否已经有了，以那份清单为准。

## 6. 命名约定

搭的时候没有特别说明的，都按这张表来。

| 项 | 约定 |
|---|---|
| Maven | groupId `com.pluchon.meng`；artifactId 用模块名；版本 `0.1.0-SNAPSHOT` |
| 基础包 | `com.pluchon.meng.common.core`、`com.pluchon.meng.common.web`、`com.pluchon.meng.api.user`、`com.pluchon.meng.gateway`、`com.pluchon.meng.user` |
| 服务名 | `meng-gateway`、`meng-user` |
| 服务端口 | 网关 8080，user 8101，其余域做的时候顺着往后排 |
| 前端端口 | `front_user` 5173，`front_admin` 5174 |
| 中间件端口 | MySQL 3306，Redis 6379，RabbitMQ 5672 和 15672，Nacos 8848 和 9848。宿主机端口被占用时只改 Compose 的映射 |
| Nacos | namespace `meng-dev`；Group `MENG_GROUP`；Data ID `meng-common.yml`、`meng-gateway.yml`、`meng-user.yml` |
| MySQL | 库 `meng_user`，字符集 `utf8mb4`；应用账号 `meng_user_app`，只有这个库的权限 |
| Redis | 键统一加前缀 `meng:` |
| RabbitMQ | vhost `meng`；交换机按 [contracts.md](contracts.md) 的规则命名，user 域是 `meng.user.topic` |
| Cookie | `meng_token` |

第一阶段搭完后的目录：

```
meng_center/
├── deploy/                  Compose 和初始化脚本
├── docs/
├── java_backend/
│   ├── pom.xml
│   ├── meng-common/
│   │   ├── meng-common-core/
│   │   └── meng-common-web/
│   ├── meng-api/
│   │   └── meng-api-user/
│   ├── meng-gateway/
│   └── meng-user/
└── vue_front/
    ├── front_user/
    └── front_admin/
```

## 7. 搭建顺序

### 第 1 步：仓库规范

- 根目录 `.gitignore`（已有）、`.editorconfig`（统一 UTF-8 和 LF，避免脚本在容器里因换行符报错）。

验收：`git status` 里没有 IDE 文件、构建产物和 `.env`。

### 第 2 步：本地中间件

- 在 `deploy/` 里用 Compose 起 MySQL 8、Redis、RabbitMQ（带管理界面）、Nacos，每个都有健康检查和数据卷。
- 初始化顺序固定：先建 `meng_user` 库，再建应用账号并只授这一个库的权限。别的库现在不建。
- RabbitMQ 固定主机名，换主机名会丢节点数据。

验收：

- `docker compose ps` 全部 healthy。只看到「已启动」不算。
- 用应用账号执行一条真实查询。`ping` 通不代表密码对。
- 用 RabbitMQ 的应用账号能登录管理界面。
- Nacos 里能建出 `meng-dev` 这个 namespace。

### 第 3 步：父工程与 common

- 父 `pom.xml` 统一管理版本，模块只声明依赖不写版本号。
- `meng-common-core`：统一响应、业务码与异常、JWT 工具、分页、事件信封。无项目内依赖。
- `meng-common-web`：身份拦截器、用户上下文、全局异常处理。只依赖 core。
- ArchUnit 测试先写一条：common 不依赖任何 api 或 server 模块。

验收：用 IDEA 自带的 Maven 编译通过，ArchUnit 测试通过。

### 第 4 步：Nacos 配置

- 环境用 namespace 隔离，Group 固定。
- Data ID 分层：`meng-common.yml` 放默认值，`meng-user.yml`、`meng-gateway.yml` 覆盖。
- 各服务的 `application.yml` 只留应用名、Nacos 地址和凭据的环境变量引用、`spring.config.import`。
- 用 `spring.config.import`，不用 `bootstrap.yml`。关键配置按非 optional 导入。不开动态刷新。

验收：

- 服务注册到正确的 namespace。
- 同一个键在 common 和服务配置里都有时，服务配置生效。
- 环境变量里的值没有被 Nacos 里的同名项盖掉。
- 关掉 Nacos 再启动，服务明确报错退出，而不是带着空配置跑起来。

### 第 5 步：meng-api-user 与 meng-user

- 包结构：`controller`、`service`、`mapper`、`entity`、`converter`、`client`、`config`、`mq`。
- Flyway 第一个脚本建 `user` 表，字段照抄 [auth.md](auth.md) 第 4 节，包括四个加密和哈希列、申请理由、拒绝原因和三个协议同意列。
- 首个管理员用单独的初始化脚本写入，不留邮箱和手机号，密码明文不进仓库。
- 邮箱和手机号的归一化、加密、哈希、脱敏做成一个组件，先放在 `meng-user` 里。第二个域要用时再下沉到 common。
- `meng-api-user` 现在只放 `GET /user/internal/users` 的接口定义和 `user.approved` 的事件体。

验收：

- 迁移跑完后查 `flyway_schema_history`，再用应用账号对 `user` 表读写各一次。
- 加密组件的单元测试：同一个邮箱或号码的不同写法哈希相同；同一个值两次加密的密文不同；密文能解回原文；脱敏结果符合 [auth.md](auth.md) 4.1 节。
- 服务启动后在 Nacos 里可见。

### 第 6 步：meng-gateway

按 [auth.md](auth.md) 第 8 节实现：清身份头、拦内部路径、白名单、公开读、从 Cookie 读 Token 并验签查会话续期、管理路径校验角色、写请求校验 `Origin`。
网关是响应式的，不能引 `meng-common-web`，也不能带进数据库依赖。

验收：[auth.md](auth.md) 第 11 节「会话与网关」里的前三条。

### 第 7 步：认证模块

按这个顺序做，前一项是后一项的前提：

1. 滑块与一次性票据。
2. 邮箱验证码。
3. 短信验证码，含地区名单校验。
4. 注册。
5. 管理员审核：通过、带原因的拒绝、通知、`user.approved` 事件。
6. 登录、登出、当前用户。
7. 被拒绝后重新申请。
8. 改密码。

每做完一项，对一遍 [auth.md](auth.md) 第 11 节里相关的验收点，并把接口加进根目录的 Postman 集合。

### 第 8 步：front_user

- 用 Vite 建项目，目录按 [frontend.md](frontend.md) 第 3 节，检查工具按第 11 节。
- `styles/tokens.scss` 写入第 8 节的设计变量；`styles/element.scss` 把 Element Plus 的主色整组覆盖掉。
- 请求层、路由守卫、`user` 和 `app` 两个 store、四种状态组件。
- 按设计稿实现登录页、注册页和协议页（用户协议与隐私政策共用一个页面）。

设计稿以画布为准：<https://claude.ai/artifact/WWGMMTJwWZKuX5FF5U46sE>（私有，需要账号本人打开）。
仓库的 `vue_front/front_user/pages_design/` 是导出的快照和三张海报图。

画布上每个页面的 Tweaks 里能切出全部状态：

- 登录页：默认、密码错误、审核中、已拒绝（含原因和重新申请）。
- 注册页：填写、滑块验证、验证码已发送、用户名已占用、两次密码不一致、未勾选同意、提交成功；邮箱和手机号两种方式；地区列表展开。
- 协议页：用户协议、隐私政策两份文件。

实现时要注意的：

- 设计稿里的色值、圆角、间距照抄数值，但要落到 `tokens.scss` 的变量上，组件样式里不写色值。
- 设计稿里的文字全部进 `locales/`。
- 前端不读也不存 Token，请求带 Cookie 即可。
- 点「发送验证码」时过一次滑块，点「提交申请」时再过一次，两处复用同一个弹窗。
- 地区下拉用 Element Plus 的选择器，指定 `popper-class`，弹层样式写在 `element.scss`。地区名单放 `constants/`。
- 申请理由选填，最多 100 字。
- 注册必须勾选同意才能提交。提交时带上两份文件的版本号，版本号放 `constants/`，与后端配置保持一致；后端返回版本已更新时提示重新阅读。
- 协议页整页不滚动，只有卡片里的正文区滚动；目录随滚动高亮。两份文件的正文放独立的内容文件，不写在组件里。
- 登录时账号已拒绝，显示后端返回的拒绝原因和重新申请的入口，用刚输入的用户名和密码提交。
- 动效在系统开启「减少动态效果」时关闭。
- 海报图加载失败要有兜底：浅水蓝底加图标。
- 窄屏时左右两栏上下堆叠，密码和确认密码由并排变成上下两行。

验收：`npm run lint`、`npm run lint:style`、`npm run build` 零错误零警告；在浏览器里用邮箱和手机号各注册一次、登录一次、被拒绝后重新申请一次；手机宽度下不出现横向滚动。

### 第 9 步：front_admin

- 登录页。
- 注册申请列表：用户名、脱敏后的联系方式、申请理由、申请时间、状态，可按状态筛选，后端分页。
- 通过前弹确认框；拒绝时弹窗里必须填原因。
- 不做主题。

验收：普通用户登录管理端被拒；管理员通过一条申请后，该用户立即能在用户端登录；拒绝时不填原因提交不了。

### 第 10 步：整体验收

- [auth.md](auth.md) 第 11 节逐条过一遍。
- 多设备登录用另一个独立的浏览器会话验证。
- 验收完删掉模块里的临时测试，接口留在 Postman 集合里。

## 8. 设计变量

来自登录页和注册页的设计稿，写入 `tokens.scss` 时加 `--meng-` 前缀。

| 名称 | 色值 | 用法 |
|---|---|---|
| 云雾白 | `#F4FAFC` | 页面大背景、输入框底色 |
| 云朵白 | `#FFFFFF` | 卡片、弹窗 |
| 浅水蓝 | `#DDF1F7` | 选中背景、图标底、聚焦光圈、次要按钮 |
| 晴海蓝 | `#367F9A` | 主按钮、链接、图标 |
| 薄荷绿 | `#DDF2E8` | 成功提示、签到和成长区域 |
| 樱花粉 | `#F5DDE5` | 错误提示的底色、欢迎区域的装饰 |
| 奶油黄 | `#F8EDCF` | 等待类提示、积分和奖励 |
| 深海墨 | `#294653` | 标题和正文 |

设计稿里另外派生了几个色，还没有逐个确认，写进 `tokens.scss` 前过一遍：

| 用途 | 色值 |
|---|---|
| 次要文字 | `#4F6B78` |
| 提示条里的说明文字 | `#3D5966` |
| 输入框占位符 | `#56717D` |
| 输入框描边 | `#BCD9E3` |
| 主按钮的硬投影 | `#24586C` |
| 错误文字和图标 | `#A8456A`，输入框报错描边 `#C4587C` |
| 等待图标 | `#8A6A1F` |
| 成功图标 | `#3E8E6A` |
| 装饰用的深樱花、深奶油 | `#E9A9BE`、`#F1C3D1`、`#F0CF7A` |

其他数值：

- 字体：标题用 ZCOOL KuaiLe，正文用 Nunito 加 Noto Sans SC。上线前把字体文件放到自己的静态资源里，不依赖外部字体服务。
- 圆角：大卡片 40，海报 28，弹窗 28，提示条和下拉列表 20，输入框和按钮是高度的一半（胶囊形）。
- 高度：输入框和主按钮 52，小按钮 44。
- 投影：大卡片 `0 20px 48px` 的晴海蓝 16% 透明度；主按钮是 `0 5px 0` 的硬投影，悬停上浮 2，按下下沉 4。

## 9. 第一阶段不做

- Sentinel、Seata、链路追踪。
- 内容、私信、成长、AI 四个域，以及 Elasticsearch、xxl-job。
- 封禁、第三方登录、注销账号。
- 繁体中文语言包、暗色主题。

## 10. 每一步交付时要说清

- 改了什么。
- 验证了什么，怎么验的。
- 哪些没验证，风险在哪。
- 留下了哪些还在运行的进程或容器。

改动留在工作区，由仓库主人决定何时提交。提交信息一行，只说改了什么。
