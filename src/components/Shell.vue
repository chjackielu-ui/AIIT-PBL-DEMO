<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {LayoutDashboard, FolderKanban, ClipboardList, Bell, GraduationCap, Users, Upload, LogOut, CheckSquare, BookOpen, MessagesSquare, Library, PanelLeftClose, PanelLeftOpen, ChevronRight} from 'lucide-vue-next'
import {useStore} from '../store'

import {roleName, run} from '../lib'
import {validOrigin} from '../navigation'
import {pendingSubmissionCount} from '../projectPresentation'
import AccountAvatar from './AccountAvatar.vue'
import NotificationBell from './NotificationBell.vue'


const store = useStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(localStorage.getItem('pbl-sidebar-folded')==='true')
function toggleSidebar(){collapsed.value=!collapsed.value;localStorage.setItem('pbl-sidebar-folded',String(collapsed.value))}
interface MenuItem {name: string; path: string; icon: unknown; match?: (path: string) => boolean}

const menuGroups = computed(() => {
  const role = store.user?.role
  const roleMenus: MenuItem[] = role === 'teacher'
    ? [
        {name: '实训班级', path: '/teacher/classes', icon: Users},
        {name:'资料库',path:'/teacher/library',icon:Library},
        {name: '实训任务总表', path: '/teacher/task-register', icon: ClipboardList},
        {name: '项目申请与审核', path: '/teacher/reviews/projects', icon: CheckSquare, match: p => p.startsWith('/teacher/reviews/projects')},
        {name: '成果审核与评分', path: '/teacher/reviews/achievements', icon: GraduationCap},
      ]
    : role === 'student'
      ? [
          {name: '我的课程与班级', path: '/student/classes', icon: Users},
          {name: '任务概览与提交', path: '/student/submissions', icon: CheckSquare},
          {name: '可参与的任务', path: '/student/tasks', icon: ClipboardList, match: p => p.startsWith('/student/tasks')},
          {name: '我的项目', path: '/student/projects', icon: FolderKanban, match: p => p.startsWith('/student/projects')},
          {name: '资料库', path: '/student/library', icon: Library},
          {name: '个人情况', path: '/student/personal', icon: GraduationCap, match: p => p.startsWith('/student/personal')},
        ]
      : [
          {name: '账号管理', path: '/admin/users', icon: Users, match: p => p === '/admin/users'},
          {name: '课程评分规则', path: '/admin/grading-rules', icon: GraduationCap},
          {name: '基础数据导入', path: '/admin/import', icon: Upload, match: p => p === '/admin/import'},
          {name: '项目列表', path: '/admin/projects', icon: FolderKanban, match: p => p === '/admin/projects' || p.startsWith('/admin/projects/')},
          {name: '小学期课程导入', path: '/admin/course-import', icon: ClipboardList},
        ]
  const groups: {label: string; items: MenuItem[]}[] = [
    {label: '', items: [{name: '工作台', path: `/${role}/dashboard`, icon: LayoutDashboard}]},
    {label: role === 'student' ? '课程与学习' : role === 'teacher' ? '教学管理' : '平台管理', items: roleMenus},
    {label: '消息与协作', items: [
      ...(role !== 'admin' ? [{name: '项目讨论', path: `/${role}/discussions`, icon: MessagesSquare, match: (p:string) => p.startsWith(`/${role}/discussions`)}] : []),
      {name: '通知中心', path: '/notifications', icon: Bell},
    ]},
  ]
  return groups
})

const unread = computed(() => store.state?.notifications.filter(n => !n.read).length || 0)
const pending=computed(()=>store.state && store.user?.role==='student'?store.state.projects.reduce((sum,p)=>sum+pendingSubmissionCount(store.state!,p,store.user!.id),0):0)

function active(item: MenuItem) {
  const origin=store.user?validOrigin(route.query.origin,store.user.role):undefined
  if(origin)return item.path===origin
  if(item.path==='/teacher/classes') return route.path.startsWith('/teacher/classes') && !route.path.includes('/reviews/')
  return item.match ? item.match(route.path) : route.path === item.path
}

const currentArea = computed(() => route.path === '/profile' ? '个人主页' : menuGroups.value.flatMap(group => group.items).find(active)?.name)
</script>
<template>
  <div v-if="store.user" class="app-shell" :class="{collapsed,'discussion-mode':route.path.includes('/discussions')}">
    <aside class="workspace-sidebar">
      <RouterLink :to="`/${store.user.role}/dashboard`" class="brand" aria-label="返回工作台">
        <span class="brand-mark"><BookOpen :size="19" :stroke-width="1.8" aria-hidden="true" /></span>
        <span v-if="!collapsed" class="brand-copy"><strong>安信工</strong><small>项目管理平台</small></span>
      </RouterLink>
      <nav class="workspace-nav" aria-label="工作空间导航">
        <div v-for="group in menuGroups" :key="group.label" class="nav-group">
          <p v-if="group.label && !collapsed" class="nav-heading">{{group.label}}</p>
          <RouterLink v-for="item in group.items" :key="item.path" :to="item.path" :aria-label="item.name"
            :title="item.name" :aria-current="active(item) ? 'page' : undefined" :class="{active: active(item)}">
            <component :is="item.icon" :size="17" :stroke-width="1.65" aria-hidden="true" />
            <span v-if="!collapsed">{{item.name}}</span>
            <span v-if="item.path === '/notifications' && unread" class="nav-count">{{unread}}</span>
            <span v-if="item.path === '/student/submissions' && pending" class="nav-count">{{pending}}</span>
          </RouterLink>
        </div>
      </nav>
      <RouterLink v-if="store.user.role !== 'admin'" to="/profile" class="sidebar-account" aria-label="我的个人主页" title="个人主页">
        <AccountAvatar :user="store.user" :size="32" />
        <span v-if="!collapsed" class="sidebar-account-text"><strong>{{store.user.name}}</strong><small>{{roleName[store.user.role]}}</small></span>
        <ChevronRight v-if="!collapsed" class="sidebar-account-arrow" :size="15" aria-hidden="true" />
      </RouterLink>
      <button class="nav-toggle" :aria-label="collapsed?'展开导航':'收起导航'" :aria-expanded="!collapsed" @click="toggleSidebar">
        <component :is="collapsed ? PanelLeftOpen : PanelLeftClose" :size="17" aria-hidden="true" />
        <span v-if="!collapsed">收起导航</span>
      </button>
    </aside>
    <div class="app-main">
      <header>
        <div class="workspace-location">
          <span>{{roleName[store.user.role]}}工作空间</span>
          <template v-if="currentArea"><ChevronRight :size="13" aria-hidden="true" /><span>{{currentArea}}</span></template>
        </div>
        <div class="actions">
          <NotificationBell />
          <el-dropdown>
            <button class="user-button"><AccountAvatar :user="store.user" :size="30" />{{store.user.name}}<span class="muted">{{roleName[store.user.role]}}</span></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人主页</el-dropdown-item>
                <el-dropdown-item divided @click="run(async () => {await store.logout(); await router.replace('/login')}, '已退出')">
                  <LogOut :size="16" />退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main><RouterView :key="route.path" /></main>
      <footer>安信工项目管理平台</footer>
    </div>
  </div>
</template>

<style scoped>
.sidebar-account {display:flex; flex:none; align-items:center; gap:9px; padding:8px; margin:0 0 6px; color:#465263; border:1px solid transparent; border-radius:6px}
.sidebar-account:hover, .sidebar-account.router-link-active {background:white; border-color:#d5dce6; color:#223d64}
.sidebar-account-text {display:grid; flex:1; min-width:0; font-size:13px}
.sidebar-account-text strong {overflow:hidden; text-overflow:ellipsis; white-space:nowrap}
.sidebar-account-text small {font-size:11px; color:var(--muted)}
.sidebar-account-arrow {flex:none}
.collapsed .sidebar-account {justify-content:center; padding-inline:0}
@media (max-width:760px) {
  .sidebar-account {justify-content:center; padding-inline:0}
  .sidebar-account-text, .sidebar-account-arrow {display:none}
}
</style>
