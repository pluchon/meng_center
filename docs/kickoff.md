# 萌部落社区 · 开工清单

> 2026-10-09 首版。这是搭骨架的执行清单：按顺序做，每一步过了验收点再做下一步。
> 配套文档：[架构设计](architecture.md)、[事件与内部接口](contracts.md)、[认证模块](auth.md)、[前端规范](frontend.md)。
> 本文和上面几份冲突时，以那几份为准，并回来改本文。

## 1. 第一阶段的范围

目标只有一个：**「注册 → 管理员审核 → 登录」在浏览器里走通。**

| | 内容 |
|---|---|
| 要建 | 父工程、`meng-common-core`、`meng-common-web`、`meng-api-user`、`meng-gateway`、`meng-user`、`front_user`（登录页、注册页）、`front_admin`（登录、注册申请列表） |
| 中间件 | MySQL、Redis、RabbitMQ、Nacos |
| 不建 | `meng-content`、`meng-im`、`meng-growth`、对应的 api 模块、Python AI 服务、Elasticsearch、xxl-job |

没用到的模块不提前建空壳，做到那个域时再建。

## 2. 开工前要定的事

下面每一项都会卡住某一步，定了再动手。「建议」是当前倾向，不是结论。

| 项 | 建议 | 卡住哪一步 |
|---|---|---|
| Maven groupId 和 Java 基础包名 | `com.pluchon.meng`，各服务在其下分包，如 `com.pluchon.meng.user` | 第 3 步 |
| Compose 和初始化脚本放哪 | 仓库根目录新建 `deploy/` | 第 2 步 |
| 环境变量命名 | 第三方账号沿用本机已有的名字，项目自己的加 `MENG_` 前缀（见第 4 节） | 第 2 步 |
| 数据库迁移方式 | Flyway，脚本放各服务自己的 `db/migration`，只前进、不改历史文件 | 第 5 步 |
| 邮箱是否加密存储 | 和手机号用同一套方案 | 第 5 步，建表前 |
| 被拒绝的人能否重新申请 | 能，把同一条记录改回待审核 | 第 5 步，建表前 |
| 是否加「申请理由」「拒绝原因」字段 | 加拒绝原因，通知里带上；申请理由先不加 | 第 5 步，建表前 |
| Token 存放位置 | HttpOnly Cookie | 第 6 步、第 8 步 |
| 手机号地区名单 | [auth.md](auth.md) 4.1 节的八个地区，默认台湾 | 第 7 步 |
| 暗色主题色值 | 第一阶段两个页面只做亮色，`tokens.scss` 里留好 `html.dark` 的位置 | 第 8 步 |

## 3. 先验证再锁版本

以下依赖对 Spring Boot 4 的适配情况没有核实过。做法：建一个只引这一个依赖的最小工程，能启动、能完成一次核心调用才算通过。
通过后把锁定的版本号写回 [architecture.md](architecture.md) 第 2 节和第 10 节。

| 依赖 | 要确认的事 | 用在哪一步 |
|---|---|---|
| Spring Boot 4.0.x、Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.0.0 | 三者的版本组合，以及对应的 Nacos 服务端版本 | 第 2、3、4 步 |
| `mybatis-plus-spring-boot4-starter` | 3.5.13 及以上的可用版本 | 第 5 步 |
| Flyway | Boot 4 下的 starter 和 MySQL 支持模块 | 第 5 步 |
| tianai 行为验证码 | 能否在 Boot 4 下生成和校验滑块 | 第 7 步 |
| 阿里云短信 SDK | 账号是中国站还是国际站；向台湾号码发验证码用哪个接口；通知类短信能否发 | 第 7 步 |

第一阶段不用 xxl-job 和 Elasticsearch，它们的兼容性留到用的时候再验。
任何一项不通过，先停下来换方案，不要绕过去继续搭。

## 4. 密钥与环境变量

### 4.1 规则

- 密钥的值只放在用户环境变量或桌面的「配置清单」里。不进仓库、不进 Nacos、不进日志、不贴进聊天记录。
- 仓库里只出现变量名。`.env` 已加入 `.gitignore`。
- 核对环境变量时只比对名字，不打印值。
- 下面三类变更按事故处理，不能顺手改：
  - 换 `MENG_JWT_SECRET`：所有人掉线。
  - 换或丢失 `MENG_PII_ENC_KEY`：已加密的手机号永久读不出来。
  - 改数据库或 RabbitMQ 的密码：服务起不来。

### 4.2 本机已有

2026-10-09 核对过用户环境变量，只看了名字，没有读值。

| 变量 | 用途 |
|---|---|
| `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET` | 阿里云短信 |
| `MAIL_USERNAME`、`MAIL_PASSWORD` | SMTP 发信账号 |
| `MYSQL_ROOT_PASSWORD` | 本地 MySQL 的 root，只给初始化脚本用，应用不用 root 连库 |
| `DASHSCOPE_API_KEY` | Qwen 模型，第一阶段不用 |

### 4.3 需要新建

| 变量 | 用途 |
|---|---|
| `MENG_JWT_SECRET` | JWT 签名 |
| `MENG_PII_ENC_KEY` | 手机号加密，AES-256，随机 32 字节 |
| `MENG_PII_HASH_KEY` | 手机号哈希，与加密密钥分开生成 |
| `MENG_MYSQL_USER_PASSWORD` | `meng_user` 库的应用账号密码 |
| `MENG_REDIS_PASSWORD` | Redis |
| `MENG_RABBITMQ_USERNAME`、`MENG_RABBITMQ_PASSWORD` | RabbitMQ 的应用账号 |
| `MENG_NACOS_ADDR`、`MENG_NACOS_NAMESPACE`、`MENG_NACOS_USERNAME`、`MENG_NACOS_PASSWORD` | Nacos 地址、环境和凭据 |

### 4.4 不是密钥，放 Nacos 配置

SMTP 主机和端口、短信签名、各短信模板编号、手机号地区名单和号码格式、各项限流阈值。

写本文时没有打开桌面的「配置清单」。SMTP 主机这类值是否已经有了，以那份清单为准。

## 5. 搭建顺序

### 第 1 步：仓库规范

- 根目录 `.gitignore`（已有）、`.editorconfig`（统一 UTF-8 和 LF，避免脚本在容器里因换行符报错）。

验收：`git status` 里没有 IDE 文件、构建产物和 `.env`。

### 第 2 步：本地中间件

- Compose 起 MySQL 8、Redis、RabbitMQ（带管理界面）、Nacos，每个都有健康检查和数据卷。
- 初始化顺序固定：先建 `meng_user` 库，再建应用账号并只授这一个库的权限。别的库现在不建。
- RabbitMQ 固定主机名，换主机名会丢节点数据。

验收：

- `docker compose ps` 全部 healthy。只看到「已启动」不算。
- 用应用账号执行一条真实查询。`ping` 通不代表密码对。
- 用 RabbitMQ 的应用账号能登录管理界面。
- Nacos 里能建出本环境的 namespace。

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
- Flyway 第一个脚本建 `user` 表，字段见 [auth.md](auth.md) 第 4 节。逻辑删除后唯一索引怎么处理在这里定下来并写回文档。
- 首个管理员用单独的初始化脚本写入。
- 手机号的归一化、加密、哈希、脱敏做成一个组件，先放在 `meng-user` 里。第二个域要用时再下沉到 common。
- `meng-api-user` 现在只放 `GET /user/internal/users` 的接口定义和 `user.approved` 的事件体。

验收：

- 迁移跑完后查 `flyway_schema_history`，再用应用账号对 `user` 表读写各一次。
- 加密组件的单元测试：同一个号码的不同写法哈希相同；同一个号码两次加密的密文不同；密文能解回原文。
- 服务启动后在 Nacos 里可见。

### 第 6 步：meng-gateway

按 [auth.md](auth.md) 第 8 节的六条实现：清身份头、拦内部路径、白名单、公开读、验签查会话并续期、管理路径校验角色。
网关是响应式的，不能引 `meng-common-web`，也不能带进数据库依赖。

验收：[auth.md](auth.md) 第 11 节里与网关有关的三条，即伪造身份头、访问内部路径、非管理员访问管理接口。

### 第 7 步：认证模块

按这个顺序做，前一项是后一项的前提：

1. 滑块与一次性票据。
2. 邮箱验证码。
3. 短信验证码，含地区名单校验。
4. 注册。
5. 管理员审核，含通知和 `user.approved` 事件。
6. 登录、登出、当前用户。
7. 改密码。

每做完一项，对一遍 [auth.md](auth.md) 第 11 节里相关的验收点，并把接口加进根目录的 Postman 集合。

### 第 8 步：front_user

- 用 Vite 建项目，目录按 [frontend.md](frontend.md) 第 3 节，检查工具按第 11 节。
- `styles/tokens.scss` 写入第 6 节的设计变量；`styles/element.scss` 把 Element Plus 的主色整组覆盖掉。
- 请求层、路由守卫、`user` 和 `app` 两个 store、四种状态组件。
- 按设计稿实现登录页和注册页。

设计稿在两处，内容一致：

- 画布：<https://claude.ai/artifact/WWGMMTJwWZKuX5FF5U46sE>（私有，需要账号本人打开）
- 仓库：`vue_front/front_user/pages_design/`，含导出的页面和三张海报图

实现时要注意的：

- 设计稿里的色值、圆角、间距照抄数值，但要落到 `tokens.scss` 的变量上，组件样式里不写色值。
- 设计稿里的文字全部进 `locales/`。
- 地区下拉用 Element Plus 的选择器，指定 `popper-class`，弹层样式写在 `element.scss`。
- 动效在系统开启「减少动态效果」时关闭。
- 海报图加载失败要有兜底：浅水蓝底加图标。
- 窄屏时左右两栏上下堆叠，密码和确认密码由并排变成上下两行。

验收：`npm run lint`、`npm run lint:style`、`npm run build` 零错误零警告；在浏览器里用邮箱和手机号各注册一次、登录一次；手机宽度下不出现横向滚动。

### 第 9 步：front_admin

登录页和注册申请列表，列表上有通过和拒绝，拒绝前弹确认框。不做主题。

验收：普通用户登录管理端被拒；管理员通过一条申请后，该用户立即能在用户端登录。

### 第 10 步：整体验收

- [auth.md](auth.md) 第 11 节逐条过一遍。
- 多设备登录用另一个独立的浏览器会话验证。
- 验收完删掉模块里的临时测试，接口留在 Postman 集合里。

## 6. 设计变量

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

设计稿里另外派生了几个色，还没有逐个确认，实现前过一遍：

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
- 圆角：大卡片 40，海报 28，弹窗 28，提示条 20，输入框和按钮是高度的一半（胶囊形）。
- 高度：输入框和主按钮 52。
- 投影：大卡片 `0 20px 48px` 的晴海蓝 16% 透明度；主按钮是 `0 5px 0` 的硬投影，悬停上浮 2，按下下沉 4。

## 7. 第一阶段不做

- Sentinel、Seata、链路追踪。
- 内容、私信、成长、AI 四个域，以及 Elasticsearch、xxl-job。
- 找回密码、封禁、第三方登录。
- 繁体中文语言包、暗色主题。

## 8. 每一步交付时要说清

- 改了什么。
- 验证了什么，怎么验的。
- 哪些没验证，风险在哪。
- 留下了哪些还在运行的进程或容器。

改动留在工作区，由仓库主人决定何时提交。提交信息一行，只说改了什么。
