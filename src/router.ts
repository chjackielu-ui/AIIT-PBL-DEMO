import {createRouter, createWebHistory} from 'vue-router'
import {useStore} from './store'

import {classPath, taskPath, projectPath} from './teacher'
import Login from './pages/Login.vue'
import Shell from './components/Shell.vue'
import NotFound from './pages/NotFound.vue'
import {ElMessage} from 'element-plus'
import {navigationRoot,validOrigin} from './navigation'

const TeacherHome = () => import('./pages/TeacherHome.vue')
const TeachingClasses = () => import('./pages/TeachingClasses.vue')
const TaskRegister = () => import('./pages/TaskRegister.vue')
const TaskList = () => import('./pages/tasks/TaskList.vue')
const TaskDetail = () => import('./pages/tasks/TaskDetail.vue')
const TaskForm = () => import('./pages/tasks/TaskForm.vue')
const ProjectCreate = () => import('./pages/tasks/ProjectCreate.vue')
const Dashboard = () => import('./pages/Dashboard.vue')
const Projects = () => import('./pages/Projects.vue')
const Workspace = () => import('./pages/Workspace.vue')
const Personal = () => import('./pages/Personal.vue')
const Notices = () => import('./pages/Notices.vue')
const Profile = () => import('./pages/Profile.vue')
const AdminUsers = () => import('./pages/admin/AdminUsers.vue')
const AdminImport = () => import('./pages/admin/AdminImport.vue')
const AdminCourseImport = () => import('./pages/admin/AdminCourseImport.vue')
const SharePage = () => import('./pages/SharePage.vue')
const PrintPage = () => import('./pages/PrintPage.vue')

type Role = 'student' | 'teacher' | 'admin'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {path: '/login', component: Login},
    {path: '/join/:token', component: () => import('./pages/JoinClass.vue')},
    {path: '/share/:id', component: SharePage},
    {path: '/print/:kind/:id?', component: PrintPage},
    {
      path: '/',
      component: Shell,
      children: [
        ...(['student','teacher','admin'] as const).map(role => ({path: `${role}/discussions/:id?`, component: () => import('./pages/Discussions.vue'), meta: {role}})),
        {path: '', redirect: () => {
          const store = useStore()
          return store.user ? `/${store.user.role}/dashboard` : '/login'
        }},
        // —— 学生端 ——
        {path: 'teacher/library', component: () => import('./pages/StudentLibrary.vue'), meta:{role:'teacher'}},
        {path: 'student/library', component: () => import('./pages/StudentLibrary.vue'), meta:{role:'student'}},
        {path: 'student/dashboard', component: Dashboard, meta: {role: 'student'}},
        {path: 'student/learning', redirect: {path:'/student/personal',query:{tab:'learning'}}},
        {path: 'student/submissions', component: () => import('./pages/Submissions.vue'), meta: {role: 'student'}},
        {path: 'teacher/submissions', redirect: '/teacher/reviews/achievements'},
        {path: 'teacher/reviews/projects/:id', component: () => import('./pages/ReviewCenter.vue'), meta: {role:'teacher'}},
        {path: 'teacher/reviews/:kind', component: () => import('./pages/ReviewCenter.vue'), meta: {role:'teacher'}},
        {path:'teacher/classes/:classId/resources',redirect:to=>({path:classPath(String(to.params.classId)),query:{module:'resources'}})},
        {path:'teacher/classes/:classId/settings',redirect:to=>({path:classPath(String(to.params.classId)),query:{module:'grading'}})},
        {path: 'admin/grading-rules', component: () => import('./pages/CourseDetails.vue'), meta: {role:'admin'}},
        {path: 'student/classes', component: () => import('./pages/StudentClasses.vue'), meta: {role:'student'}},
        {path: 'student/classes/:classId/details', component: () => import('./pages/CourseDetails.vue'), meta: {role:'student'}},
        {path: 'student/classes/:classId/resources', component: () => import('./pages/CourseDetails.vue'), meta: {role:'student'}},
        {path: 'student/projects/:id/submit/:phaseId', component: () => import('./pages/SubmitTask.vue'), meta: {role:'student'}},
        {path: 'student/tasks', component: TaskList, meta: {role: 'student'}},
        {path: 'student/tasks/:id', component: TaskDetail, meta: {role: 'student'}},
        {path: 'student/projects', component: Projects, meta: {role: 'student'}},
        {path: 'student/projects/create/:taskId', component: ProjectCreate, meta: {role: 'student'}},
        {path: 'student/projects/:id', component: Workspace, meta: {role: 'student'}},
        {path: 'student/personal/:id?', component: Personal, meta: {role: 'student'}},
        {path: 'student/portfolio/:id?', redirect: to=>({path:'/student/personal'+(to.params.id?'/'+to.params.id:''),query:{...to.query,tab:'portfolio'}})},
        // —— 教师端 ——
        {path: 'teacher/dashboard', component: TeacherHome, meta: {role: 'teacher'}},
        {path: 'teacher/classes', component: TeachingClasses, meta: {role: 'teacher'}},
        {path: 'teacher/classes/:classId', component: TaskList, meta: {role: 'teacher'}},
        {path: 'teacher/classes/:classId/tasks/create', component: TaskForm, meta: {role: 'teacher'}},
        {path: 'teacher/classes/:classId/tasks/:id/milestones/:phaseId', component: () => import('./pages/MilestoneReview.vue'), meta:{role:'teacher'}},
        {path: 'teacher/classes/:classId/tasks/:id', component: TaskDetail, meta: {role: 'teacher'}},
        {path: 'teacher/classes/:classId/tasks/:id/edit', component: TaskForm, meta: {role: 'teacher'}},
        {path: 'teacher/classes/:classId/projects', component: Projects, meta: {role: 'teacher'}},
        {path:'teacher/classes/:classId/projects/:id',component:Workspace,meta:{role:'teacher'}},
        {path: 'teacher/classes/:classId/reviews/:kind', component: () => import('./pages/ReviewCenter.vue'), meta: {role: 'teacher'}},
        {path: 'teacher/task-register', component: TaskRegister, meta: {role: 'teacher'}},
        // —— 管理员端 ——
        {path: 'admin/dashboard', component: Dashboard, meta: {role: 'admin'}},
        {path: 'admin/users', component: AdminUsers, meta: {role: 'admin'}},
        {path: 'admin/import', component: AdminImport, meta: {role: 'admin'}},
        {path: 'admin/course-import', component: AdminCourseImport, meta: {role: 'admin'}},
        {path: 'admin/projects', component: Projects, meta: {role: 'admin'}},
        {path: 'admin/projects/:id', component: Workspace, meta: {role: 'admin'}},
        // —— 公共页 ——
        {path: 'notifications', component: Notices},
        {path: 'profile', component: Profile},
        {path: 'profile/settings', redirect: '/profile'},
        // —— 旧地址兼容（声明式重定向，通知与历史书签依赖） ——
        {path: 'teacher/classes/:classId/course', redirect: (to) => classPath(String(to.params.classId)) + '/settings'},
        {path: 'teacher/classes/:classId/course/tasks/:id', redirect: (to) => taskPath(String(to.params.classId), String(to.params.id))},
        {path: 'teacher/classes/:classId/course/tasks/:id/edit', redirect: (to) => taskPath(String(to.params.classId), String(to.params.id)) + '/edit'},
        {path: 'teacher/classes/:classId/tasks', redirect: (to) => classPath(String(to.params.classId))},
        {path: 'teacher/tasks', redirect: '/teacher/task-register'},
        {path: 'teacher/tasks/create', redirect: '/teacher/classes?intent=publish'},
        {path: 'teacher/tasks/:id', component: TaskDetail, meta: {role:'teacher', legacy:'task'}},
        {path: 'teacher/tasks/:id/edit', component: TaskForm, meta: {role:'teacher', legacy:'task-edit'}},
        {path: 'teacher/projects', redirect: '/teacher/classes?intent=all'},
        {path: 'teacher/projects/:id', component: NotFound, meta: {role:'teacher', legacy:'project'}},
        {path: 'teacher/reviews/final/:id', component: NotFound, meta: {role:'teacher', legacy:'final'}},
        // —— 登录用户的 404 保留整体导航 ——
        {path: ':pathMatch(.*)*', component: NotFound},
      ],
    },
  ],
})

router.onError((error, to) => {
  if (/Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk/i.test(error.message)) {
    const last = Number(sessionStorage.getItem('pbl-asset-recovery') || 0)
    if (navigator.onLine && Date.now() - last > 60000) {
      sessionStorage.setItem('pbl-asset-recovery', String(Date.now()))
      window.location.assign(to.fullPath)
      return
    }
    ElMessage.error('页面资源加载失败，请确认服务已启动后刷新页面')
  }
})

router.beforeEach(async (to,from) => {
  const store = useStore()
  if (!store.loaded) await store.load()
  if (to.path === '/login') {
    if (store.user) await store.load()
    return store.user ? {path: `/${store.user.role}/dashboard`, replace: true} : true
  }
  if (to.path.startsWith('/share/') || to.path.startsWith('/join/')) return true
  if (!store.user) {
    return {path: '/login', query: {redirect: to.fullPath}}
  }
  const required = to.meta.role as Role | undefined
  if (required && required !== store.user.role) {
    return `/${store.user.role}/dashboard`
  }
  // 旧书签在状态加载后再解析，避免刷新页面时丢失任务与项目目标。
  if (store.user.role === 'teacher' && to.meta.legacy) {
    const kind = to.meta.legacy
    if (kind === 'task' || kind === 'task-edit') {
      const task = store.state?.tasks.find(t => t.id === to.params.id)
      return {path: task ? taskPath(task.classId, task.id) + (kind === 'task-edit' ? '/edit' : '') : '/teacher/classes', query: to.query}
    }
    const destination = projectPath(store.state!, String(to.params.id), kind === 'final' ? 'final' : String(to.query.tab || ''))
    const url = new URL(destination, window.location.origin)
    return {path: url.pathname, query: {...to.query, ...Object.fromEntries(url.searchParams)}}
  }
  const workflow=/\/(projects|tasks)\/[^/]+/.test(to.path) || /\/milestones\//.test(to.path) || (to.path==='/student/tasks' && !!to.query.classId)
  if(workflow && !validOrigin(to.query.origin,store.user.role)){
    const origin=validOrigin(from.query.origin,store.user.role) || navigationRoot(from.path,store.user.role)
    if(origin)return {...to,query:{...to.query,origin},replace:true}
  }
  return true
})

window.addEventListener('pageshow', async event => {
  if (!event.persisted) return
  const store = useStore()
  await store.load()
  const path = router.currentRoute.value.path
  if (store.user && path === '/login') await router.replace(`/${store.user.role}/dashboard`)
  if (!store.user && !/^\/(login|join|share)(\/|$)/.test(path)) await router.replace({path:'/login',query:{redirect:path}})
})
