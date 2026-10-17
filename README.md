# 萌部落社区

日漫萌系、水色主题的多用户社区：发帖、私信、成长、AI 萌萌、轻度抽奖。

## 目录

```
meng_center/
├── docs/            文档
├── deploy/          Docker 中间件、Nacos 配置及启动脚本
├── java_backend/    Java 后端（Spring Boot 4 微服务）
├── python_ai/       Python AI 计算服务（FastAPI、LangChain / LangGraph）
├── vue_front/       两个独立 Vue 3 前端
└── verification/    隔离联调、压测和浏览器验证
```

## 文档

- [架构设计](docs/architecture.md)：定位、技术栈、模块结构、服务划分、防耦合规则、暂不做与待定
- [事件与内部接口](docs/contracts.md)：跨服务交互的完整清单和新增流程
- [认证模块](docs/auth.md)：注册、登录、找回密码、审核的接口、数据、状态流转，以及个人中心（资料、换绑、设备管理和页面）
- [内容域](docs/content.md)：帖子、评论、配图的接口、数据、规则，主页、详情、发帖页的拆分
- [成长域](docs/growth.md)：签到、经验、等级的接口、数据、规则
- [消息域](docs/im.md)：私信、通知、实时连接的接口、数据、规则，消息中心页面
- [AI 域](docs/ai.md)：萌萌以外的 AI：帖子理解、推荐、相关帖子、语义搜索、发帖页 AI 入口、成长总结、审核
- [萌萌](docs/meng.md)：三个角色的陪伴聊天：好感度、记忆、对话链路、作品、关系界限与安全、页面
- [前端规范](docs/frontend.md)：项目划分、目录、三文件拆分、不硬编码的做法、检查工具
- [环境、密钥与约定](docs/setup.md)：本机环境、环境变量清单、命名约定、等外部条件的事
- [本地中间件](deploy/README.md)：`meng_center` 启动、端口与凭据约定
- [验证](verification/README.md)：后端测试、隔离联调、压测和浏览器脚本
- [仓库卫生规则](AGENTS.md)：什么能进仓库、干完活要收拾什么
