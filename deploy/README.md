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

## 业务服务与前端

先执行 `./deploy/scripts/Publish-NacosConfiguration.ps1` 发布三个配置，再用 IDEA 自带 Maven 对 `java_backend/pom.xml` 执行 `package`。启动脚本默认使用打包后的可执行 JAR。

```powershell
./deploy/scripts/Initialize-Administrator.ps1
./deploy/scripts/Start-Backend.ps1 -Service meng-user
./deploy/scripts/Start-Backend.ps1 -Service meng-gateway
```

三条命令分别执行：初始化脚本完成后退出；两个服务各占一个终端。首次管理员只在账号不存在时创建，使用 `MENG_ADMIN_USERNAME` 和 `MENG_ADMIN_PASSWORD` 用户环境变量；已有账号不重设密码。初始化前停止同端口的 user 实例。

Windows Java 17 启动必须带 `-Dfile.encoding=UTF-8`，脚本已设置。IDEA 的 VM options 也要设置该项，并加载用户环境变量及 `ports.env` 中的端口变量。Nacos 不可用时应用启动失败，不以本地快照绕过检查。

在 `vue_front/front_user` 与 `vue_front/front_admin` 两个目录分别执行 `npm ci`、`npm run dev`。用户端是 `http://127.0.0.1:5173`，管理端是 `http://127.0.0.1:5174`；请求经各自 Vite 代理到 8080 网关，user 仅监听本机 8101。

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

## 内容与成长服务

已有数据卷新增数据库时执行下面两条命令，不删除或重建卷：

```powershell
./deploy/scripts/Initialize-LocalEnvironment.ps1
./deploy/scripts/Initialize-DomainDatabases.ps1
./deploy/scripts/Publish-NacosConfiguration.ps1
```

初始化只生成缺失的 `MENG_MYSQL_CONTENT_PASSWORD`、`MENG_MYSQL_GROWTH_PASSWORD` 与 `MENG_CONTENT_IMAGE_ROOT` 用户环境变量；数据库脚本建立 `meng_content` / `meng_growth` 和各自应用账号，已有密码不覆盖。新 MySQL 卷通过入口脚本自动创建三个域库。新生成的密码也应备份到仓库外配置清单。

打包后启动 `Start-Backend.ps1 -Service meng-content` 与 `-Service meng-growth`，分别监听本机 8102、8103；网关继续用 8080，增加 `/content/**`、`/growth/**` 的 Nacos 路由。发布配置后需重启受影响业务服务，配置导入不动态刷新。

`meng-content.yml` 配置上传目录、发帖间隔、每日上限和喜欢热度权重；`meng-growth.yml` 配置时区、奖励、每日上限及等级门槛。三个域各自管理 Flyway 迁移。成长事件交换机 `meng.growth.topic`，内容事件交换机 `meng.content.topic`；growth 消费发帖与评论事件，user 消费等级变更。

## 搜索引擎

Elasticsearch 单节点固定为 9.2.8，镜像按摘要锁定，构建时安装同版本 IK。堆 512 MB、容器上限 1 GB，数据卷为 `elasticsearch-data`，仅映射本机 9200。Java 客户端由 Boot 管理，版本为 9.2.9。

`Initialize-Elasticsearch.ps1` 只生成缺失的两个 ES 密码，启动容器后创建 `meng_content_app` 和角色 `meng_content`。权限限定为 `meng_post_*` 及别名 `meng_post`，不赋予集群管理权限。应用只读取自己的密码，管理员密码不发布到 Nacos。首次配置后执行 `Publish-NacosConfiguration.ps1` 并重启内容服务。

内容服务启动时异步检查索引；缺失时从 MySQL 重建。管理员可调用 `POST /content/admin/hot/rebuild`、`POST /content/admin/search/rebuild` 提前执行，接口返回表示已提交任务。索引别名和 Redis 生效色标用于观察切换结果。

ES 故障不阻塞发帖和服务健康检查，搜索降级为标题包含查询。索引同步使用单线程、有界队列和三次重试；失败只记录帖子 ID，下次重建修复。批量重建与实时写入在进程内串行写同一索引，搜索读取不持有写锁。
