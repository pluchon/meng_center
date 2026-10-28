# 本地中间件

Docker Compose 项目名固定为 `meng_center`。本地开发每种中间件各一个实例，Nacos 用 standalone 和持久化的内嵌存储，不额外建 Nacos MySQL 库。这里不是生产高可用集群。

## 启动

在仓库根目录用 PowerShell 执行：

```powershell
./deploy/scripts/Initialize-LocalEnvironment.ps1
./deploy/scripts/Start-Middleware.ps1
./deploy/scripts/Initialize-Elasticsearch.ps1
./deploy/scripts/Initialize-XxlJob.ps1
./deploy/scripts/Initialize-Nacos.ps1
./verification/Test-Middleware.ps1
```

第一条命令只在变量不存在时生成，写入用户环境变量；已有的值保持不变。启动脚本会加载用户环境变量，不用重开终端。IDEA 需要重新打开以加载新变量。

所有密钥只在用户环境变量里，不在 `ports.env`、仓库和 Nacos 中保存。请将生成的密钥备份到仓库外的配置清单，尤其是 `MENG_PII_ENC_KEY` 和 `MENG_PII_HASH_KEY`。初始化脚本不显示密钥，不负责覆盖已有密码。

## 端口

映射集中在 `ports.env`，绑定 `127.0.0.1`。启动前检查端口，不可用时明确失败。

| 中间件 | 宿主机端口 | 容器端口 |
|---|---:|---:|
| MySQL | 13306 | 3306 |
| Redis | 16379 | 6379 |
| Elasticsearch | 9200 | 9200 |
| xxl-job 调度中心 | 18082 | 8080 |
| RabbitMQ AMQP | 25672 | 5672 |
| RabbitMQ 管理界面 | 25673 | 15672 |
| Nacos 服务 API | 18848 | 8848 |
| Nacos gRPC | 19848 | 9848 |
| Nacos 控制台 | 18080 | 8080 |

Nacos 宿主机 gRPC 端口必须是服务端口加 1000。改映射后也需要更新用户环境变量 `MENG_NACOS_ADDR`，初始化脚本不会覆盖已有地址。

头像写入 `MENG_USER_AVATAR_ROOT` 指定的仓库外目录。新增环境变量后先重新执行初始化脚本，再发布 Nacos 配置；已存在的变量保持不变。

## 业务服务与前端

先执行 `./deploy/scripts/Publish-NacosConfiguration.ps1` 发布各服务配置，再用 IDEA 自带 Maven 对 `java_backend/pom.xml` 执行 `package`。启动脚本默认使用打包后的可执行 JAR。

```powershell
./deploy/scripts/Initialize-Administrator.ps1
./deploy/scripts/Start-Backend.ps1 -Service meng-user
./deploy/scripts/Start-Backend.ps1 -Service meng-gateway
```

三条命令分别执行：初始化脚本完成后退出；两个服务各占一个终端。首次管理员只在账号不存在时创建，使用 `MENG_ADMIN_USERNAME` 和 `MENG_ADMIN_PASSWORD` 用户环境变量；已有账号不重设密码。初始化前停止同端口的 user 实例。

Windows Java 17 启动必须带 `-Dfile.encoding=UTF-8`，脚本已设置。IDEA 的 VM options 也要设置该项，并加载用户环境变量及 `ports.env` 中的端口变量。Nacos 不可用时应用启动失败，不以本地快照绕过检查。

`Start-Backend.ps1` 默认使用 64MB 初始堆、384MB 最大堆、256MB 元空间上限和两个可用处理器，避免每个本地服务都按整机资源自动扩张。`MENG_JAVA_MAX_HEAP_MB` 可覆盖最大堆，范围 128～4096MB。构建、测试和服务启动依次执行；新增服务前先检查系统剩余资源，再确认上一个服务健康。

在 `vue_front/front_user` 执行 `npm ci`、`npm run dev`。用户端是 `http://127.0.0.1:5173`；管理端前端已移除，仅保留文档规格和后端管理员接口。请求经各自 Vite 代理到 8080 网关，user 仅监听本机 8101。

QQ SMTP 使用现有环境凭据，主机 `smtp.qq.com`、SSL 465；实际认证已验证。国内短信在 `meng-user.yml` 的 `sms` 配置，国际短信在 `international-sms`，补齐对应签名、模板或 Sender ID 后再启用。

正式协议正文位于用户端 `src/content/legal/`。当前只显示待发布说明；运营者提供正式条款后同步前端版本常量、内容版本及 Nacos `legal` 的两个版本号，再将 `published` 改为 true。当前默认 false，后端明确拒绝注册提交。

RabbitMQ 管理界面：<http://127.0.0.1:25673>，用户名与密码读取 `MENG_RABBITMQ_USERNAME`、`MENG_RABBITMQ_PASSWORD`。

Nacos 控制台：<http://127.0.0.1:18080>，用户名与密码读取 `MENG_NACOS_USERNAME`、`MENG_NACOS_PASSWORD`；namespace 为 `meng-dev`。初始化采用官方管理员 API，已经初始化的密码不会重置。

## 持久化与账号

- 镜像标签和摘要均锁定，避免重新启动时版本漂移。
- MySQL 初始化先建 `meng_user`，再建 `meng_user_app`，只授权此库。root 仅用于初始化。
- Redis 用 ACL 密码哈希认证和 AOF；密码不出现在启动命令参数中。
- RabbitMQ 固定主机名 `meng-rabbitmq`，vhost 为 `meng`。
- 每个中间件有独立的数据卷与健康检查。
- Nacos 补充三个官方镜像必需的环境变量：`MENG_NACOS_AUTH_TOKEN`、`MENG_NACOS_AUTH_IDENTITY_KEY`、`MENG_NACOS_AUTH_IDENTITY_VALUE`。它们和业务 JWT 密钥分开。
- Nacos 启动包装脚本关闭官方脚本的 shell 跟踪，避免将认证环境变量回显到容器日志。

已有数据卷不会再次执行 MySQL 初始化或 RabbitMQ 默认账号初始化。修改凭据必须按 `docs/setup.md` 第 2.1 节的事故规则处理，不删卷解决问题。

## 版本依据

- [Spring Cloud Alibaba 官方配套版本表](https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/)
- [Nacos 3.1 官方 Docker 启动说明](https://nacos.io/en/docs/v3.1/quickstart/quick-start-docker/)
- [Nacos 鉴权与首次管理员密码初始化](https://nacos.io/en/docs/v3.1/manual/admin/auth/)

应用版本能否一起运行，以 `verification/` 中的实测为准，版本表只提供选型依据。

## 内容、成长与消息服务

已有数据卷新增数据库时执行下面两条命令，不删除或重建卷：

```powershell
./deploy/scripts/Initialize-LocalEnvironment.ps1
./deploy/scripts/Initialize-DomainDatabases.ps1
./deploy/scripts/Publish-NacosConfiguration.ps1
```

初始化只生成缺失的 `MENG_MYSQL_CONTENT_PASSWORD`、`MENG_MYSQL_GROWTH_PASSWORD`、`MENG_MYSQL_IM_PASSWORD`、`MENG_CONTENT_IMAGE_ROOT` 与 `MENG_IM_IMAGE_ROOT` 用户环境变量；数据库脚本建立 `meng_content` / `meng_growth` / `meng_im` 和各自应用账号，已有密码不覆盖。新 MySQL 卷通过入口脚本自动创建这些域库。新生成的密码也应备份到仓库外配置清单。

打包后启动 `Start-Backend.ps1 -Service meng-content` 、`-Service meng-growth` 及 `-Service meng-im`，分别监听本机 8102、8103、8104；网关继续用 8080，增加 `/content/**`、`/growth/**`、`/im/**` 的 Nacos 路由及单独的 `/im/ws` WebSocket 路由。content 开启 Nacos 刷新，推荐手动权重与 `meng.content.rec.ranker` 每次请求读取最新配置；其他启动配置变更仍需重启相应服务。

`meng-content.yml` 配置上传目录、发帖间隔、每日上限和喜欢热度权重；`meng-growth.yml` 配置时区、奖励、每日上限及等级门槛。四个域各自管理 Flyway 迁移。消息服务使用 `V20261012001__create_im_tables` 建表，`meng-im.yml` 配置连接 Origin 和用户查询超时；私信存储目录只从 `MENG_IM_IMAGE_ROOT` 读取。成长事件交换机 `meng.growth.topic`，内容事件交换机 `meng.content.topic`；growth 消费发帖与评论事件，user 消费等级变更。

## 奶糖助手服务

`meng-companion` 监听 `127.0.0.1:8107`，网关入口为 `/companion/**`。首次使用依次执行 `Initialize-LocalEnvironment.ps1`、`Initialize-DomainDatabases.ps1`、`Publish-NacosConfiguration.ps1`，再打包并执行 `Start-Backend.ps1 -Service meng-companion`。已有数据卷原地新增 `meng_companion` 库及 `meng_companion_app`，凭据来自 `MENG_MYSQL_COMPANION_PASSWORD`。

主页的奶糖助手只提供会话、长期记忆、私人图片和站内只读工具。新对话直接使用角色卡的固定开场白。角色卡与示例对白只有奶糖一套，由 `resources/companion/catalog.json` 按版本原子导入。私人图片目录来自 `MENG_COMPANION_WORK_ROOT`，保存在仓库外。

companion 使用新增迁移 `V20261027001__naitang_assistant` 删除旧能力的表与列，并逻辑删除旧角色数据；IM 使用自己的迁移 `V20261027001__remove_companion_notice_targets` 清理旧陪伴通知及跳转字段。历史迁移保持原样。奶糖的一轮使用 LangGraph 状态图，主 agent、草稿与预审并行；站内审核共用同一个审核子图。接口与运行时规则见 [奶糖助手](../docs/meng.md) 和 [AI 公共运行时](../docs/ai-runtime.md)。

`V20261027002__version_companion_memory_facts` 将记忆去重限定为当前有效事实，保留失效、取代和清空记录；`V20261027003__track_memory_embedding_model` 扩展向量版本字段，记录模型、维度和编码版本。模型或编码变化后，维护任务补齐向量，召回不混用不同版本。

`V20261027004__record_runtime_compactions` 保存 UTC 自然日的成功压缩次数，与会话窗口更新同事务提交。运行 `verification/Export-AiRuntimeDashboard.ps1 -EndDay yyyy-MM-dd -Days 7 -Output <仓库外路径.html>` 导出只读每日看板；输出已存在时拒绝覆盖。导出不调用模型，私有数据不进入看板。

## 搜索引擎

Elasticsearch 单节点固定为 9.2.8，镜像按摘要锁定，构建时安装同版本 IK。堆 512 MB、容器上限 1 GB，数据卷为 `elasticsearch-data`，仅映射本机 9200。Java 客户端由 Boot 管理，版本为 9.2.9。

`Initialize-Elasticsearch.ps1` 只生成缺失的两个 ES 密码，启动容器后创建 `meng_content_app` 和角色 `meng_content`。权限限定为 `meng_post_*`、别名 `meng_post` 及 `meng_vector_post*`，不赋予集群管理权限。应用只读取自己的密码，管理员密码不发布到 Nacos。首次配置后执行 `Publish-NacosConfiguration.ps1` 并重启内容服务。

内容服务启动时异步检查索引；缺失时从 MySQL 重建。xxl-job 每天 04:00 调用 `contentHotRebuild` 重算热榜，04:10 调用 `contentSearchRebuild` 重建搜索索引（Asia/Taipei），没有 Spring `@Scheduled`。管理员可调用 `POST /content/admin/hot/rebuild`、`POST /content/admin/search/rebuild` 提前执行，接口返回表示已提交任务。索引别名和 Redis 生效色标用于观察切换结果。

## 定时任务

`Initialize-XxlJob.ps1` 创建独立库、应用账号、官方 3.5.0 表结构及下列任务，再启动调度中心和内部转发器。初始化可重复执行，不插入官方演示任务或默认密码。登录 `http://127.0.0.1:18082`，账号 `admin`，密码从用户环境变量 `MENG_XXL_JOB_ADMIN_PASSWORD` 读取。执行器组 `meng-content`、`meng-user` 和 `meng-companion` 自动注册，使用 FIRST 路由、DISCARD_LATER、忽略过期触发、失败重试一次。

| 处理器 | Asia/Taipei 调度 | 超时（秒） |
|---|---|---:|
| `contentHotRebuild` | 每天 04:00 | 600 |
| `contentSearchRebuild` | 每天 04:10 | 1800 |
| `contentAiReanalyzeFailed` | 每小时，重试三天内分析失败的帖子，每篇最多三次 | 180 |
| `contentAiOutboxDispatch` | 每分钟 | 55 |
| `contentRecProfileRebuild` | 每天 04:20 | 1800 |
| `contentRecLogClean` | 每天 04:30 | 600 |
| `contentRecRankerTrain` | 周一 05:00 | 180 |
| `userAiOutboxDispatch` | 每分钟 | 55 |
| `companionOutboxDispatch` | 每分钟，重发未确认事件并恢复遗留轮次 | 55 |
| `companionMemoryMaintain` | 每 10 分钟，补记忆向量、空闲压缩、快照刷新和过期原文清理 | 500 |

奶糖更新需要发布 `meng-companion.yml` 和 `meng-ai.yml`，并重新执行任务初始化，保留发件箱与记忆维护两个任务，停用其他旧任务。记忆向量按 `document` 编码，召回查询按 `query` 编码；版本不匹配的记忆向量由维护任务重算。content 的帖子向量通过管理员接口蓝绿重建。

Windows 会锁定运行中的 JAR。更新时先停止对应实例，再打包并重新启动 companion、content、IM 和 Python AI；前端重新启动开发服务。`./verification/Test-CompanionDeployment.ps1` 只读核对进程和构建时间、迁移、表情目录、奶糖角色卡与示例、两个任务及执行器注册。开发验证不调用真实模型；真实冒烟和验收遵守运行时文档第 11.2 节。

尚未授权真实模型调用时，在调度中心暂停 `companionMemoryMaintain`，防止补向量、摘要和记忆维护自动调用模型；发件箱任务保持启用。此时使用 `Test-CompanionDeployment.ps1 -AllowPausedMemoryMaintenance` 检查部署，脚本只允许维护任务暂停，不修改任务状态；默认检查仍要求两个任务启用。重新初始化任务会恢复启用，必须等模型验证阶段再执行。

真实模型验证因错误或质量问题停止时，继续暂停记忆维护，保留评测报告、原本期编号和累计预算；服务健康不代表模型验收通过。修改 Python 提示词或运行时代码后重新启动 AI 服务，再执行部署检查。
