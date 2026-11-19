# MySQL 演示交付

默认连接本机 `127.0.0.1:3306/pbl_demo`。启动时创建 `pbl_users` 和 `pbl_state`，空库不写入业务数据或演示账号。附件按上传者账号标识保存在 `D:/PBLDemoStorage/files/`，路径可用 `PBL_FILES_DIR` 调整。数据库保存附件元数据，备份时应同时保留库数据和附件。

`pbl_users` 独立保存用户，主键及学号唯一，密码使用随机盐与 PBKDF2。课程、教学班、选课、任务、项目、提交、评分、讨论及通知沿用现有结构，保存在 `pbl_state` 的 JSON 中。两张表在同一事务提交，版本校验拒绝覆盖另一进程提交的版本。适用单实例演示。

## 启动

在项目根目录使用 PowerShell 7.4+。

```powershell
pwsh -NoProfile -File ./start.ps1 -Build
# 第一次要登录管理员时执行，按提示输入自己设置的初始管理员密码
pwsh -NoProfile -File ./start.ps1 -InitAdmin
# 按需增加可选演示账号，也可以与 -InitAdmin 同时用
pwsh -NoProfile -File ./start.ps1 -DemoAccounts
pwsh -NoProfile -File ./stop.ps1
```

访问 `http://127.0.0.1:8080/`。总管理员账号名为 `admin`，`-InitAdmin` 仅在没有管理员时创建账号。演示学生登录学号为 `9900000001` 至 `9900000003`，演示教师账号为 `demo-teacher-1` 至 `demo-teacher-3`，演示学生和教师密码均为中文“学生”。按需创建演示账号不会创建任何班级、课程或项目。

项目机器使用当前 Windows 用户加密的本地凭据 `.runtime/mysql-credential.xml`。在其他环境使用 `PBL_DB_URL`、`PBL_DB_USER`、`PBL_DB_PASSWORD`。默认监听回环地址。需要可信局域网演示时可将 `PBL_BIND_ADDRESS` 设置为电脑的局域网地址，再使用该地址访问。

## 导入步骤

1. 登录管理员。在账号管理导入教师账号，随后在同一页设置初始密码及功能权限。如果开启可选演示账号，可以使用已有教师账号。
2. 打开“小学期课程导入”，下载 Excel 样例。模板优先填入当前教学班的任课教师。使用演示账号时，模板按丛林和坚冰、银河和青松及山峰、奋进分配给三位演示教师。提交前用实际教务安排核对教师、计划时间、课程状态和评分规则。
3. 选择“课程与教学班”，上传、预览、确认。每行创建一个课程和一个教学班，课程标题按 `安信工24级小学期_教学班_软件_专业技术综合实践_教师` 生成。同一教师账号可在多行复用。实际开始和结束时间可留空。名单中的“坚冰班”为准，课程导入接受“尖兵班”和“精兵班”作为别名。
4. 选择“24 级软件学生名单”并上传原始教务 Excel。系统读取 6 个软件教学班工作表，按教学班归属导入，不要求建立行政班。确认前只返回预览，确认后以学号匹配或创建学生及选课关系。已自主注册且姓名一致的账号可直接关联，原密码保留。仅从名单导入的账号由管理员设置初始密码后登录。
5. 教师发布大任务，在阶段发布面板配置节点、上级阶段、材料、截止日期、阶段及最终验收；学生基于已发布任务创建项目。首次获得通过评分之前，课程自动评分留空。阶段的未提交、待验收和退回状态分开显示。

## 接口映射

|端或领域|接口|行为|
|---|---|---|
|三端会话|POST `/api/login`、`/api/register`、`/api/logout`；GET `/api/state`|按角色和密码登录，学生按学号注册，退出注销会话。状态按角色读取。|
|管理员导入|POST `/api/admin/course-import/preview`、`/api/admin/course-import`、`/api/admin/roster-import/preview`、`/api/admin/roster-import`|课程与教学班、教务名单均支持预览和确认。整批校验通过后提交。|
|管理员账号|POST `/api/actions` 的 `setTeacherPermissions`、`setAccountPassword`、`toggleUser`|配置教师的任务、审核、评分、文件、讨论、选课和分享权限，设置账号密码及启停。|
|教师|POST `/api/actions` 的 `saveTask`、`addItem`、`reviewProject`、`batchReviewProjects`、`reviewPhase`、`reviewFinal`、`saveCourseGrades` 等|教师配置大任务、阶段分支、验收与评分。敏感写入按功能权限和教学班/项目范围授权。|
|学生|POST `/api/actions` 的 `createProject`、`editProject`、`submitPhase`、`submitFinal` 等；GET `/api/portfolio`|使用已发布任务建项目，提交阶段和结项，查看本人档案。|
|讨论、资料、分享|GET `/api/projects/{pid}/discussion`；POST `/api/actions` 的 `message`；班级和项目文件 GET/POST；GET `/api/public/{shareId}`|讨论区每 4 秒拉取本项目消息。消息及附件引用入库，文件内容保存在本机。|

本轮未对接教务系统在线身份核验或同步接口，使用人工上传教务表格。未接入 AI、独立头像上传、独立实时消息服务或图形化流程编排。业务写入继续使用现有 `/api/actions`，表结构未按每个领域展开。

`npm run check` 执行前端构建、已有 Java 测试及原业务隔离回归。真实 MySQL 回归可在空的 `pbl_verify_*` 专用库中执行 `pwsh -File ./scripts/verify-mysql.ps1 -Database <验证库名>`。该回归会写入验证库。
