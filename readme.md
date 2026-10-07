# De

**De** 是一个校园讨论社区 Android 应用 Demo，由 **404 Sleep Not Found** 团队开发。

它把校园论坛、内容审核和学校热度代币玩法结合在一起。用户可以在不同学校频道浏览帖子、发布图片和参与多层评论；管理员可以处理举报、申诉并查看审计记录；成员还可以参与 Heat Market 和排行榜。

App 已接入 [De-Moderation 后端](https://github.com/Mingjie-Mao/De-moderation-backend)。账号、帖子、评论、图片、社交互动和市场数据均保存到真实服务器与 PostgreSQL，用户操作会写入数据库。规则引擎与 LLM 提供审核建议，最终处置由管理员决定。

## 项目亮点

- 多学校频道切换，不同频道有不同视觉风格
- 支持发帖、图片上传、多层评论与回复展开 / 折叠
- 点赞、关注、收藏和通知接入真实后端
- 成员与管理员使用真实账号登录，退出 App 进程后可恢复会话
- 管理员在 App 或网页中处理举报、模型建议、申诉和审计
- 支持中英文界面，中文模式下可查看内容译文及举报原文
- 学校热度由帖子、回复和点赞共同驱动，Heat Market 支持虚拟交易与 K 线展示
- 排行榜展示用户的市场表现
- 每日 UTC 日期首次访问时，现金低于 1000 才补足，持仓和历史记录保留

## 界面预览

| 首页信息流 | 评论区 |
| --- | --- |
| ![首页信息流](picture/feed.png) | ![评论区](picture/comments.png) |

| Heat Market | 排行榜 |
| --- | --- |
| ![Heat Market](picture/market.png) | ![排行榜](picture/leaderboard.png) |

截图为原始 Demo 界面参考，当前在线内容、行情和余额会随实际操作变化。热度代币是虚拟游戏积分。

## Demo 体验流程

1. 选择 **Member**，使用成员账号登录，也可以创建自己的账号。
2. 浏览学校频道，发布帖子、图片和评论，体验点赞、关注与收藏。
3. 举报帖子或评论，在设置中退出后选择 **Admin**，登录管理员账号。
4. 进入审核工作台，查看案件与模型建议，处理举报和申诉。
5. 在 **Heat Market** 中交易学校代币，查看 K 线、持仓和排行榜。

## Demo 账号

| 身份 | 用户名 | 密码 |
| --- | --- | --- |
| 成员 Member | `1234` | `1234` |
| 管理员 Admin | `12345` | `12345` |

两个账号都经过服务器认证；选择 Admin 按钮后仍需登录真实管理员账号。

## 技术栈

- 客户端：Java、Android SDK、Gradle、RecyclerView
- 接入与会话：REST API、JWT、Refresh Token、Android Keystore
- 后端：Java 21、Spring Boot、PostgreSQL、Spring AI / Gemini、S3 兼容图片存储
- 保留原课程项目中的自定义数据结构与审核模块

## 项目结构

| 目录 | 说明 |
| --- | --- |
| `android/` | Android 应用代码与资源 |
| `android/app/src/main/java/com/example/myapplication/` | 页面、组件与主要业务逻辑 |
| `android/app/src/main/java/backend/` | 后端 API、账号会话、媒体上传与数据同步 |
| `android/app/src/main/java/moderation/` | 原课程项目保留的审核策略与迭代器 |
| `app/src/` | 原始课程侧 Java 模块 |
| `picture/` | README 界面截图 |
| `scripts/` | 数据导入、验收与发布脚本 |

## 运行方式

1. 用 Android Studio 打开 `android/`。
2. 等待 Gradle 同步完成。
3. 在模拟器或真机上运行应用，默认连接已部署的演示后端。

也可以使用命令行构建：

```bash
cd android
./gradlew assembleDebug
```
