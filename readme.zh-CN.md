# De

[![English](https://img.shields.io/badge/English-555555?style=for-the-badge)](readme.md)
[![中文](https://img.shields.io/badge/%E4%B8%AD%E6%96%87-4285F4?style=for-the-badge)](readme.zh-CN.md)

De 是一个校园讨论社区 Android 应用 Demo，由 404 Sleep Not Found 团队开发。

它把校园论坛、审核机制和学校热度代币玩法结合在一起。用户可以在不同学校频道中浏览帖子、发布带图片的内容、查看多层评论；管理员可以处理举报内容；同时还可以参与基于社区活跃度生成的 Heat Market 和排行榜。

在线举报已经接到独立的 [De-Moderation](https://github.com/Mingjie-Mao/De-Moderation) Spring Boot 服务。后端配置模型引擎后，从本 App 提交的举报会真实进入 LLM 分析，并在 App 的管理员队列里展示引擎建议、置信度和理由。

## 项目亮点

- 多学校频道切换，不同频道有不同视觉风格
- 支持发帖、图片上传和评论互动
- 评论区支持展开与折叠，移动端阅读更清晰
- 提供成员模式和管理员模式
- 管理员可在举报队列中快速查看和处理被举报内容
- 引入学校代币系统，热度由帖子、回复和点赞共同驱动
- 通过排行榜展示不同用户的市场表现
- 每日余额重置，保证玩法持续可体验

## 界面预览

| 首页信息流 | 评论区 |
| --- | --- |
| ![首页信息流](picture/feed.png) | ![评论区](picture/comments.png) |

| Heat Market | 排行榜 |
| --- | --- |
| ![Heat Market](picture/market.png) | ![排行榜](picture/leaderboard.png) |

## Demo 体验流程

1. 使用 Demo 账号登录
2. 选择 Member 或 Admin
3. 浏览不同学校频道中的帖子
4. 在成员模式下举报评论
5. 在管理员模式下进入审核队列处理举报
6. 在 Heat Market 中交易学校代币并查看排行榜

## Demo 账号

用户名：1234

密码：1234

## 技术栈

- Java
- Android SDK
- Gradle
- 自定义审核模块与数据结构模块

## 项目结构

- `android/`：Android 应用代码与资源文件
- `android/app/src/main/java/com/example/myapplication/`：页面、组件与主要业务逻辑
- `android/app/src/main/java/moderation/`：举报、隐藏、审核队列相关逻辑
- `android/app/src/main/java/backend/`：De-Moderation HTTP 接入、账号映射和按需内容镜像
- `app/src/`：仓库中保留的原始课程侧 Java 模块
- `picture/`：README 展示用项目截图

## 运行方式

用 Android Studio 打开 `android/`

等待 Gradle 同步完成

在模拟器或真机上运行应用

也可以使用命令行构建：

```bash
cd android
./gradlew assembleDebug
```

## 在线 LLM 审核

两个仓库仍然是两个独立应用，通过 De-Moderation REST API 通信。用户举报时，App 会按需把对应帖子/评论链以稳定的安装级账号镜像到后端，再把举报提交到持久审核队列；请求失败时不会在 UI 中假装已经成功举报。

1. 启动 De-Moderation 和 PostgreSQL，并配置后端的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`。
2. 如需 LLM 审核，在后端配置 `AI_CHAT_MODEL`、`GEMINI_API_KEY`、`GEMINI_MODELS` 和 `MODERATION_ENGINE`；未配置时仍可连通，但使用确定性规则引擎。
3. 在 Android App 打开“**设置 → 审核后端**”。模拟器使用 `http://10.0.2.2:8080`；真机需要可访问的 HTTPS 部署（debug 构建也可填写开发机局域网地址）。
4. 要使用在线管理员队列，需要填入后端管理员凭据。密码只保存在内存中，App 进程重启后需要重新输入。

管理员模式下有两个页面：**举报队列**列出引擎已分析、尚未人工处置的案件，并显示建议、置信度和理由；**已审核记录**列出已经处置过的案件，并且仍然可以修改决定——后端会按需恢复被隐藏的内容或解封被封禁的作者，并把这次修改追加到审计日志，而不是覆盖原来的记录。

在线模式默认开启。设置页会明确显示“LLM 已启用”“仅规则引擎”或“服务不可达”。如需原来的纯本机演示，可在此关闭在线模式。Release 构建不允许明文 HTTP；只有 debug manifest 为本地开发放开了 HTTP。
