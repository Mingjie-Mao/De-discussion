# 项目经历简历稿

面向 Java 后端／AI 应用岗位，依据两个工作目录中的代码、测试、评测报告和现有验收记录整理。项目起止时间与个人职责未确认，时间保留占位；以下描述仅应保留本人实际参与的部分。代码链接指向现有 Git 远端，部分 Android 新功能尚未提交到远端。

## 精简版：每个项目两条，参照截图

**De-Moderation，LLM 内容审核后端**　　　　　　　　　**YYYY.MM – YYYY.MM**

- 基于 **Java 21、Spring Boot、PostgreSQL 和 Spring AI（Gemini）**开发内容审核后端，使用 **SKIP LOCKED** 实现持久化队列的并发领取与异常回收，支持规则降级、人工复核、申诉及审计日志。
- 在 **192 条中英双语样本**上迭代版本化 Prompt，将 **Macro-F1 从 0.617 提升至 0.924**，人工介入类别召回率达到 **0.778**；在 **72 条留出样本**上三次评测平均 Macro-F1 为 **0.984**，使用 **Testcontainers** 验证数据库工作流。[代码](https://github.com/Mingjie-Mao/De-moderation-backend)

**De-discussion，Android 校园社区应用**　　　　　　　　**YYYY.MM – YYYY.MM**

- 基于 **Java、Android SDK、RecyclerView 和 REST API**开发多校校园社区，实现图文发帖、多层评论、社交互动及虚拟热度市场；对接审核后端，打通举报、管理员裁决、申诉与通知流程。
- 使用 **Android Keystore／AES-256-GCM** 实现加密会话持久化与令牌轮换；通过**乐观点赞回滚、评论缓存、图片请求合并及交易请求 ID 复用**处理网络失败和重复请求，以单元测试和设备测试覆盖关键流程。[代码](https://github.com/MingjieMao/De-discussion)

## 扩展版：适合突出 AI 应用能力

**De-Moderation，LLM 内容审核与案件调查后端**　　　　　　**YYYY.MM – YYYY.MM**

- 基于 **Java 21、Spring Boot、PostgreSQL 和 Spring AI（Gemini）**构建审核工作流，通过**部分唯一索引、原子计数及 SKIP LOCKED**实现并发举报聚合和队列领取；结合超时、熔断、限流退避与规则降级，支持人工裁决、改判、申诉及追加式审计。
- 建立版本化 Prompt 与统一评测流程，在 **192 条中英双语样本**上将 **Macro-F1 从 0.617 提升至 0.924**，人工介入类别召回率达到 **0.778**；另以 **72 条留出样本、36 组最小对**进行三次评测，平均 Macro-F1 为 **0.984**。
- 实现基于 **Tool Calling** 的案件调查助手，通过 **4 个只读工具**查询案件证据、作者历史、同规则先例及规则文本，结合有界调用和引用校验生成调查简报，并接入审核工作台；使用 **Testcontainers** 覆盖并发聚合、队列恢复及工具事务边界。[代码](https://github.com/Mingjie-Mao/De-moderation-backend)

**De-discussion，Android 校园社区应用**　　　　　　　　**YYYY.MM – YYYY.MM**

- 基于 **Java、Android SDK、RecyclerView 和 REST API**开发覆盖 **4 个学校频道**的校园社区，支持图文发帖、多层评论、点赞收藏、关注及虚拟热度市场；对接审核后端，形成举报、人工裁决、申诉和通知闭环。
- 使用 **Android Keystore／AES-256-GCM** 保存成员与管理员会话，实现进程重启恢复及 Refresh Token 轮换；通过**乐观点赞回滚、15 秒评论缓存、图片请求合并和交易请求 ID 复用**减少重复请求并处理失败重试。
- 将 **47 篇帖子、108 条评论及 6 张图片**迁入后端，保留作者映射和评论父子关系；以 **64 项单元测试**及设备测试覆盖会话、成员读写、管理员审核和交互恢复流程。[代码](https://github.com/MingjieMao/De-discussion)

## 可按岗位替换的要点

- Java 后端：实现 **JWT、Refresh Token 轮换和 tokenVersion 校验**，使封禁、角色变更及全端退出及时生效；使用 **keyset 游标分页**与查询数量测试控制列表查询开销。
- Java 后端／平台：实现本地文件与 **S3 兼容对象存储**抽象、图片解码归一化及孤儿媒体扫描；提供 **Docker、Prometheus、Alertmanager、Grafana、备份与恢复演练**配置和脚本。
- AI 应用：在 **16 个调查场景、每场景三次运行**的测试中，默认调查 Prompt 的可接受建议比例为 **87.5%**，逐场景建议稳定率为 **93.75%**。这些指标分别衡量建议是否属于预先标注的可接受动作、三次是否给出同一动作，不能替换成“准确率”或“无幻觉率”。
- Android：通过后台执行器隔离论坛、评论和市场请求，保留列表及分页状态；图片使用弱引用、下载大小限制与采样解码，防止旧页面被异步任务持有，并控制图片内存开销。
- Java 基础：课程模块实现 **AVL 树、BST 和有序数组列表**，提供排序、索引访问与区间迭代；已有 State、Strategy、Factory 和 Observer 等设计模式实现。未核实个人分工或量化性能收益，建议仅在确实负责相关模块时使用。

## 事实依据与使用口径

本次没有重新运行应用测试、模型评测或操作线上服务。评测数字已依据仓库保存的 JSON 混淆矩阵复算；测试结果来自已有 XML 或项目验收记录。

| 简历表述 | 依据 | 口径 |
| --- | --- | --- |
| Java 21、Spring Boot、Spring AI、PostgreSQL、Testcontainers | `/Users/mingjie/Desktop/De-moderation/pom.xml` | 后端依赖；Android 使用 Java 17 编译选项，不写成 Java 21 Android 应用。 |
| 并发举报聚合、原子计数 | `src/main/java/com/campusguard/moderation/ModerationCaseService.java`、`ModerationCaseRepository.java`、`src/main/resources/db/migration/V3__moderation.sql` | 部分唯一索引约束同一目标只有一个未关闭案件；不能据此宣称所有故障下模型调用 exactly-once。 |
| SKIP LOCKED、异常案件回收 | `ModerationWorker.java`、`ModerationCaseProcessor.java`、`ModerationCaseRepository.java` | 领取事务在分析前提交；失联案件重新入队。未宣称经过大规模线上压测。 |
| 超时、熔断、限流退避与规则降级 | `moderation/engine/ai/ResiliencePolicy.java`、`GeminiModerationEngine.java`、`ModerationCaseProcessor.java` | 模型故障时保留人工复核流程，输出字段接受校验与有限纠正重试。 |
| 192 条样本，0.617 → 0.924，召回 0.778 | `/Users/mingjie/Desktop/De-moderation/docs/evaluation.json`、`evaluation.md` | 同模型不同 Prompt 的离线结果。样本用于 Prompt 迭代，不能当作线上准确率；v2 ESCALATE 为 28/36。 |
| 72 条留出样本，三次平均 0.984 | `docs/evaluation-heldout.json`、`evaluation-heldout.md` | v2 三次平均为 0.9840749；半极差约 0.003，并非置信区间。某次运行的供应商错误导致部分样本没有判定，不宣称所有运行均零错误。 |
| 36 组最小对 | 同上，以及 `HeldOutDatasetTest.java` | 代表运行中 35/36 组两半均答对；97.2% 是该次成对准确率，三次平均约 97.0%。 |
| 4 个只读调查工具、引用校验 | `moderation/investigation/ToolRegistry.java`、`CaseInvestigator.java`、`BriefParser.java` 及四个工具实现 | 调查默认关闭、需管理员触发。校验引用是否确由工具返回，不代表能够验证全部自然语言事实。未实现向量 RAG 或模型长期记忆。 |
| 调查建议 87.5%、稳定率 93.75% | `docs/investigation-benchmark-inv-v4.json` | 16 个编写场景，每个三次运行；小样本离线评测。 |
| 加密会话与进程恢复 | `/Users/mingjie/Desktop/De-discussion/android/app/src/main/java/backend/EncryptedSessionStore.java`、`BackendUserSession.java`、`BackendAdminSession.java` | 使用 Android Keystore AES-256-GCM、AtomicFile 和 noBackupFilesDir；密码不落盘。 |
| 点赞回滚、15 秒评论缓存、图片请求合并 | `android/app/src/main/java/com/example/myapplication/ServerFeatures.java`、`ThreadRefreshCache.java`、`RemoteImageLoader.java` | 评论缓存保留分页元数据；图片仅合并进行中的同 URL 请求，不宣称存在跨页面持久图片缓存。 |
| 交易请求 ID 复用 | `android/app/src/main/java/com/example/myapplication/MarketFragment.java`、`android/app/src/androidTest/java/com/example/myapplication/AdminWorkflowTest.java` | 在网络中断或 5xx 后重试保留原请求 ID 和报价；这是客户端对接现有服务的行为，当前 De-moderation 工作目录不包含市场服务实现。 |
| 47 帖、108 评论、6 图片迁移 | `scripts/fixtures/legacy-demo.json`、`scripts/complete-demo-import.md`、`docs/project-recheck.zh-CN.md` | 原 Demo 内容的迁移及读回验收，不能写成真实用户规模、自然增长或真实交易收益。 |
| 64 项单元测试 | `android/app/build/test-results/testDebugUnitTest/TEST-*.xml` | 现有 14 份报告合计 64 项，失败、错误及跳过均为 0。设备报告可能被后续运行覆盖，不把一份当前报告当作全部历史设备验证总数。 |

后端表内相对路径以 `/Users/mingjie/Desktop/De-moderation` 为根；Android 表内相对路径以 `/Users/mingjie/Desktop/De-discussion` 为根。

### 阅读时发现的资料差异

- 后端 README 的 v1 ESCALATE 召回写为 0.056；当前原始 JSON 和自动报告为 1/36，即 0.028。简历采用核对一致的 v2 结果，并省去有冲突的旧召回基线。
- Android README 仍保留“会话只存内存”的旧文字；当前代码已实现 Keystore 加密持久化，详见 `docs/account-session-settings.zh-CN.md`。
- Android 账号设置文档记录服务端头像、语言和主题同步仍待 V16 部署。当前所读后端目录只有 V1–V9 迁移，因此简历不宣称该同步已线上生效，也不将文档提及的 V15/V16 市场和账号实现归为此后端工作目录内代码。
- Kubernetes、告警投递、异地备份和 k6 脚本属于已提供的实现与部署资产；没有本次线上运行证据的内容，不写成已在生产环境大规模落地或达到某个 QPS／SLA。
