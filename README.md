# 安信工项目管理平台

Vue 3、TypeScript 和 Spring Boot。当前演示使用本机 MySQL 存储业务记录，附件保存到 D 盘。默认以空库启动，通过管理员导入课程及名单。

在项目根目录使用 **PowerShell 7.4+**，安装前端依赖后构建。

```powershell
npm ci
pwsh -NoProfile -File ./start.ps1 -Build
pwsh -NoProfile -File ./start.ps1 -InitAdmin
```

打开 [本地平台](http://127.0.0.1:8080/)。管理员账号为 `admin`，初始密码在启动时输入。使用 `-DemoAccounts` 可按需创建学生和教师账号。详细的数据库配置、导入样例、角色流程及已接接口见 [MySQL 演示交付](docs/MYSQL-DEMO.md)。

前端源码在 `src/`，Java 后端在 `backend/src/main/`，验证和启动脚本在 `scripts/`。本轮前的交接和整理见 [开发交接](docs/DEVELOPMENT.md) 与 [整理记录](docs/CLEANUP-20261119.md)。`releases/` 保存历史发布包。

`npm run check` 构建前端和后端，运行既有测试及隔离回归。停止本项目服务使用 `pwsh -File ./stop.ps1`。前端热更新使用 `pwsh -File ./start.ps1 -Dev`。
