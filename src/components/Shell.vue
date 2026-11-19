<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {LayoutDashboard, FolderKanban, ClipboardList, Bell, GraduationCap, Users, Upload, LogOut, CheckSquare, Layers, MessagesSquare, Library, ChevronLeft, ChevronRight} from 'lucide-vue-next'
import {useStore} from '../store'

import {roleName, run} from '../lib'
import {validOrigin} from '../navigation'
import {pendingSubmissionCount} from '../projectPresentation'


const store = useStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(localStorage.getItem('pbl-sidebar-folded')==='true')
function toggleSidebar(){collapsed.value=!collapsed.value;localStorage.setItem('pbl-sidebar-folded',String(collapsed.value))}
interface MenuItem {name: string; path: string; icon: unknown; match?: (path: string) => boolean}

const menus = computed<MenuItem[]>(() => {
  const role = store.user?.role
  const roleMenus: MenuItem[] = role === 'teacher'
    ? [
        {name: '实训班级', path: '/teacher/classes', icon: Users},
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
  return [
    {name: '工作台', path: `/${role}/dashboard`, icon: LayoutDashboard, match: p => p === `/${role}/dashboard`},
    ...roleMenus,
    ...(role !== 'admin' ? [{name: '项目讨论', path: `/${role}/discussions`, icon: MessagesSquare, match: (p:string) => p.startsWith(`/${role}/discussions`)}] : []),
    {name: '通知中心', path: '/notifications', icon: Bell, match: p => p === '/notifications'},
  ]
})

const unread = computed(() => store.state?.notifications.filter(n => !n.read).length || 0)
const pending=computed(()=>store.state && store.user?.role==='student'?store.state.projects.reduce((sum,p)=>sum+pendingSubmissionCount(store.state!,p,store.user!.id),0):0)

function active(item: MenuItem) {
  const origin=store.user?validOrigin(route.query.origin,store.user.role):undefined
  if(origin)return item.path===origin
  if(item.path==='/teacher/classes') return route.path.startsWith('/teacher/classes') && !route.path.includes('/reviews/')
  return item.match ? item.match(route.path) : route.path === item.path
}

</script>
<template>
  <div v-if="store.user" class="app-shell" :class="{collapsed,'discussion-mode':route.path.includes('/discussions')}">
    <aside>
      <RouterLink :to="`/${store.user.role}/dashboard`" class="brand">
        <span class="brand-mark"><Layers :size="20" /></span>
        <span v-if="!collapsed">安信工 · 项目管理</span>
      </RouterLink>
      <div v-if="!collapsed" class="side-caption">{{roleName[store.user.role]}}工作空间</div>
      <nav>
        <RouterLink v-for="item in menus" :key="item.path" :to="item.path" :title="item.name" :class="{active: active(item)}">
          <component :is="item.icon" :size="16" />
          <span v-if="!collapsed">{{item.name}}</span>
          <span v-if="item.path === '/notifications' && unread && !collapsed" class="count">{{unread}}</span>
          <span v-if="item.path === '/student/submissions' && pending" class="pending-count" :aria-label="`${pending} 项待完成任务`">{{pending}}</span>
        </RouterLink>
      </nav>
    </aside>
    <div class="sidebar-fold-zone"><button class="sidebar-fold-handle" :aria-label="collapsed?'展开导航栏':'收起导航栏'" :aria-expanded="!collapsed" :title="collapsed?'展开导航栏':'收起导航栏'" @click="toggleSidebar"><ChevronRight v-if="collapsed" :size="16" /><ChevronLeft v-else :size="16" /></button></div>
    <div class="app-main">
      <header>
        <div class="actions">
          <span class="muted">项目管理平台</span>
          <span>{{roleName[store.user.role]}}端</span>
        </div>
        <div class="actions">
          <RouterLink to="/notifications" class="icon-button" aria-label="通知中心">
            <Bell :size="20" /><span v-if="unread" class="dot" />
          </RouterLink>
          <span class="avatar">{{store.user.name.slice(0, 1)}}</span>
          <el-dropdown>
            <button class="user-button">{{store.user.name}}<span class="muted">{{roleName[store.user.role]}}</span></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile/settings')">当前用户信息</el-dropdown-item>
                <el-dropdown-item divided @click="run(async () => {await store.logout(); await router.push('/login')}, '已退出')">
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
