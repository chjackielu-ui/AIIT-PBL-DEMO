# 开发交接（2026-11-19）

## 当前判断

Demo 的主要业务闭环已经实现，并有真实 Java 后端与本地数据持久化。下一轮属于已有接口的规范化和扩展，不是从零接后端。当前适合继续开发和演示；生产化基础能力尚未完成。

## 功能进度

|模块|当前进度|实现位置|
|---|---|---|
|三角色与导航|学生/教师/管理员、会话、通知、个人信息、学期筛选|src/router.ts、store.ts、pages/|
|课程与班级|教学班创建、选课导入、扫码入班、大纲与参考资料|ClassEnrollment、CourseGrading、ApiController|
|任务与项目|草稿/发布、个人/团队、跨班规则、申请审核、批量审核、任务看板|PlatformService、pages/tasks、components/workspace|
|阶段提交与验收|整组/个人提交、材料清单、版本冲突、退回重交、教师评分|PlatformService、Grading、SubmissionRulesTest|
|课程成绩与学情|自定义维度、手工成绩、项目加权、个人成绩、匿名班级汇总|CourseGrading、LearningAnalytics|
|文件与讨论|上传下载、前端预览、附件/回复/代码讨论、@通知|ApiController、PlatformService、Discussion.vue|
|结项与展示|贡献比例、成员成绩、结项审核、归档只读、重新开放、公开成果白名单|PlatformService、projectPresentation.ts|
|个人档案与管理|历史经历、打印、分享二维码、五类基础导入、账号启停、重置演示数据|Portfolio/PrintPage、admin 页面、PlatformService|

文件预览、二维码、Excel 解析和打印主要在前端；上传、数据保存、权限和评分计算由后端执行。讨论采用轮询，不是 WebSocket。外部链接展示不代表已接入第三方业务服务。

## 后端实际接入

入口为 `backend/src/main/java/cn/axg/platform/ApiController.java`。前端统一从 `src/api.ts` 发起同源请求，Vite 开发时代理 `/api` 到 8080；生产构建由 Java 提供页面。

|方法与路径|用途|
|---|---|
|GET /api/health|健康检查|
|GET /api/accounts；POST /api/login、/api/logout|演示账号选择、HttpSession 会话|
|GET /api/state|按角色过滤业务状态、成绩与学情|
|POST /api/actions|30 个业务动作，见下方分组|
|GET /api/course-catalog、/api/portfolio|教师课程目录、学生档案|
|GET /api/class-invites/{token}|公开入班邀请信息|
|POST /api/classes/{cid}/students/preview|教师预览选课名单|
|GET /api/class-access-addresses|教师入班二维码候选局域网地址|
|POST /api/classes/{cid}/files；GET /api/classes/{cid}/files/{fid}|班级附件|
|POST /api/projects/{pid}/files；GET /api/projects/{pid}/files/{fid}|项目附件|
|GET /api/public/{shareId}、/api/public/{shareId}/images/{fid}|公开成果与允许公开的图片|

`/api/actions` 请求体为 `{op, ...业务字段}`，目前动作分组如下：

- 课程班级：saveCourseDetails、saveCourseGrades、createClassInvite、joinClass、importClassStudents、createTeachingClass、addClassStudents。
- 任务项目：saveTask、createProject、editProject、batchReviewProjects、reviewProject、addItem、itemStatus。
- 资料讨论：editFile、message。
- 提交评分：submitPhase、reviewPhase、saveContribution、submitFinal、reviewFinal。
- 档案展示：reopen、share、readNotices、historySave、historyDelete。
- 管理导入：toggleUser、importPreview、importConfirm、reset。

状态和附件保存到本地文件；`JsonStateRepository` 负责 JSON 读写与文件路径，保留临时文件替换机制。`PlatformService` 负责业务串行化及动作失败后的内存回滚，现有领域类负责入班、评分、学情和迁移。

## 下一轮后端开发顺序

1. 先为会话、课程班级、任务项目、提交审核、成绩和文件定义 DTO、错误格式与接口契约，再逐模块增加 REST 接口；已有 `/state` 和 `/actions` 可保留至前端迁移完成。
2. 逐域拆解 PlatformService 的大分支；控制器仅处理请求，领域服务处理状态变化、权限与评分，仓储处理持久化。不要为新业务继续无限扩展通用动作入口。
3. 接入关系型数据库，建立用户/班级/选课/任务/项目/成员/提交版本/评分/文件表及唯一约束；迁移现有 JSON 数据，并补事务和并发版本控制。当前仓储抽取提供边界，并不意味着数据库适配已经完成。
4. 接入正式认证，明确角色及资源权限；演示账号选择应仅在 Demo 配置开启。
5. 用本轮回归保住项目申请→阶段提交→评分→结项→归档的行为，再为新增接口补有意义的契约和事务测试。

## 已知限制

- PlatformService 仍是较大的集中业务服务，数据仍大量使用 JsonNode；尚未完成全领域拆分及强类型建模。
- 全量 `/state` + `/actions` 适合 Demo，但不利于分页、独立接口演进和大规模数据。
- JSON 和附件无跨资源数据库事务，进程锁只适用于单实例；未做压力、断电恢复或多实例一致性验证。
- Monaco、Element Plus 等构建块较大，Vite 有体积警告；本轮未调整页面布局或进行新一轮浏览器逐页验收。
- 工作区没有 Git 仓库；本轮修改前的源码备份在 `.runtime/backups/before-cleanup-20261119.zip`。建议下一轮先建立版本管理。
