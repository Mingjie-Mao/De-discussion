# 完整 Demo 数据导入

仅用于已明确授权的面试数据库。不是应用启动脚本，也不是生产数据库重置工具。

## 数据来源

`fixtures/legacy-demo.json` 是原 App 的完整非敏感样本：47 帖、108 评论、11 个显示身份、分类、2 个置顶、原互动分数、关注收藏，以及 4 校 × 6 段 OHLC 和 8 位虚拟市场成员。帖子图片仍引用原资源文件，通过普通媒体 API 存入 R2。

`import-legacy-forum.py` 首先使用正常 API 导入账号、内容、父子评论和图片，生成私有 `.local/forum-import-state.json` 映射。新数据库必须先完成此步骤。当前在线数据库已完成；不要删除检查点或重新注册同名账号。

## 完整导入

后端必须先迁移到 V15。`CompleteDemoImport.java` 只补齐完整场景，采用一个事务、advisory lock 和 `demo_data_batches` 批次标记 `legacy-full-v1`。

- 原成员 `campusviewer` 的内容归到 `1234`，`modmentor` 内容归到 `12345`。账号权限和密码不被修改。
- 还原分类、置顶和相对时间。若原帖正文已被编辑，事务中止，要求人工协调。
- 42 个普通账号的真实投票关系恢复原分数；现有投票保留。
- 原关注和收藏，以及当前成员的个人内容、互动通知都保存于数据库。
- 8 位原市场成员有真实账号、钱包和闭合交易账本。24 段原图表历史、导入交易均标记 `IMPORTED_DEMO`。正常操作标记 `LIVE`，积分是虚拟游戏资产。
- 保留后续新增内容、钱包、交易和所有真实审核处置与审计。旧的模拟举报处罚不伪装成人工审批记录。

使用后端 Maven 依赖获取 Jackson、PostgreSQL JDBC 和 Spring Security Crypto 的 classpath：

```bash
# 在后端目录执行
mvn dependency:build-classpath -Dmdep.outputFile=/absolute/private/path/import-classpath.txt
# 在 Android 仓库根目录执行；使用 Java 21+
javac -cp "$(cat /absolute/private/path/import-classpath.txt)" -d .local/complete-demo-classes scripts/CompleteDemoImport.java
```

在进程环境中安全配置 `DEMO_DATABASE_URL`（完整 JDBC URL，保留 SSL 参数）、`DEMO_DATABASE_USER`、`DEMO_DATABASE_PASSWORD`。不要把密码写在命令行、文档或聊天中。先执行回滚演练：

```bash
java -cp ".local/complete-demo-classes:$(cat /absolute/private/path/import-classpath.txt)" CompleteDemoImport \
  scripts/fixtures/legacy-demo.json .local/forum-import-state.json .local/complete-demo-credentials.json --dry-run
```

确认摘要后将最后参数改成 `--apply-interview-demo`。只有此明确参数会提交数据库事务。随机密码保存在忽略的私有文件，权限为 0600。已完成批次再次运行返回 `alreadyApplied=true`，不会重复导入。

## 验收

```bash
python3 scripts/verify-legacy-forum.py scripts/fixtures/legacy-demo.json --base-url YOUR_API_URL --interview-aliases
python3 scripts/verify-complete-demo.py --base-url YOUR_API_URL
```

它读回全部原帖、评论、作者映射、父子关系、分类、置顶和图片；不会输出令牌或生成账号密码。再验证关系表、每位榜单成员的现金账本、`IMPORTED_DEMO` 历史和正常交易路径。个人登录后检查 Feed / Alerts / Market / Board / You，重启 App 后仍应从服务器加载。

不要在应用启动时自动灌数据，不要通过清库达到“像 Demo 一样”的效果，也不要声称导入的场景是有机用户或实测收益。


2026-10-06 当前环境验收：数据库共 66 账号、50 帖和 115 评论（包括已有软删除数据）；公开可见 47 帖。成员 1234 可见 3 篇原帖、16 条评论，9 篇已点赞帖子、8 个收藏、3 个关注和 10 位关注者。原 47/108 的内容、父子关系、分类置顶和 6 个图片对象读回一致；原互动分数由真实投票关系匹配。8 位市场成员的余额、80 条闭合历史交易、交易日数和账本连续性均匹配，24 段图表历史均标记导入来源。重复导入跳过；普通/管理员/导入普通账号与市场账号认证通过。数量随之后真实操作变化。
