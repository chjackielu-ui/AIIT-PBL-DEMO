<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {ArrowRight, Bell, CheckCheck} from 'lucide-vue-next'
import {useStore} from '../store'
import {useNotificationActions} from '../notifications'
import {stamp} from '../lib'
import type {Notice} from '../types'

const store = useStore(), route = useRoute()
const actions = useNotificationActions()
const shown = ref(false), trigger = ref<HTMLButtonElement>(), root = ref<HTMLElement>()
const unread = computed(() => store.state?.notifications.filter(n => !n.read) || [])
let closeTimer: ReturnType<typeof setTimeout>
let poll: ReturnType<typeof setInterval>
let fromHover = false

function show(hover = false) {
  clearTimeout(closeTimer)
  if (!shown.value) {
    shown.value = true
    fromHover = hover
    void store.refreshNotices()
  }
}
function close() {clearTimeout(closeTimer); shown.value = false; fromHover = false}
function toggle() {
  if (shown.value && !fromHover) close()
  else {show(); fromHover = false}
}
function leave() {
  closeTimer = setTimeout(() => {if (!root.value?.contains(document.activeElement)) close()}, 170)
}
function outside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) close()
}
function focusOut(event: FocusEvent) {
  if (!root.value?.contains(event.relatedTarget as Node)) close()
}
function escape() {close(); trigger.value?.focus()}
function openNotice(notice: Notice) {close(); void actions.open(notice)}
watch(() => route.fullPath, close)
onMounted(() => {
  document.addEventListener('pointerdown', outside)
  poll = setInterval(() => {if (!document.hidden) void store.refreshNotices()}, 12000)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', outside)
  clearInterval(poll)
  clearTimeout(closeTimer)
})
</script>

<template>
  <div ref="root" class="notification-bell" @mouseenter="show(true)" @mouseleave="leave" @focusout="focusOut" @keydown.esc.stop="escape">
    <button ref="trigger" class="icon-button bell-trigger" type="button" :aria-label="unread.length ? '通知，'+unread.length+' 条未读' : '通知'" aria-controls="header-notices" :aria-expanded="shown" @click="toggle">
      <Bell :size="20" aria-hidden="true" /><span v-if="unread.length" class="bell-count">{{unread.length > 99 ? '99+' : unread.length}}</span>
    </button>
    <Transition name="bell-panel">
      <section v-if="shown" id="header-notices" class="bell-panel-card" aria-label="未读通知">
        <div class="bell-panel-heading"><strong>未读通知 <span>{{unread.length}}</span></strong><button type="button" class="text-button" :disabled="!unread.length" @click="actions.readAll"><CheckCheck :size="15" />全部已读</button></div>
        <div v-if="unread.length" class="bell-items">
          <button v-for="notice in unread.slice(0,6)" :key="notice.id" type="button" class="bell-item" @click="openNotice(notice)">
            <span class="bell-item-dot" aria-hidden="true"></span>
            <span><strong>{{notice.title}}</strong><small>{{notice.text}}</small><time :datetime="notice.time">{{stamp(notice.time)}}</time></span>
          </button>
        </div>
        <p v-else class="bell-empty">没有未读通知</p>
        <RouterLink to="/notifications" class="bell-center" @click="close">进入通知中心<ArrowRight :size="16" /></RouterLink>
      </section>
    </Transition>
  </div>
</template>

<style scoped>
.notification-bell{position:relative;flex:none}
.bell-trigger{position:relative}
.bell-count{position:absolute;right:-5px;top:-5px;padding:0 4px;border:2px solid #fff;border-radius:12px;background:#2862af;color:#fff;font-size:10px;font-weight:700;line-height:15px;min-width:17px;text-align:center}
.bell-panel-card{position:absolute;top:calc(100% + 8px);right:-8px;z-index:100;width:min(352px,calc(100vw - 30px));background:white;border:1px solid #dbe5f2;border-radius:11px;box-shadow:0 14px 36px #213e602b;color:#203856}
.bell-panel-card::before{content:'';position:absolute;inset:-10px 0 auto;height:10px}
.bell-panel-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:13px 14px;border-bottom:1px solid #e5ecf5}
.bell-panel-heading strong>span{margin-left:5px;color:#61748b;font-variant-numeric:tabular-nums}
.bell-panel-heading button{font-size:12px}.bell-panel-heading button:disabled{opacity:.45;cursor:default}
.bell-items{max-height:min(48vh,370px);overflow:auto}
.bell-item{display:grid;grid-template-columns:8px minmax(0,1fr);gap:8px;width:100%;padding:9px 14px;border:0;background:#fff;text-align:left;color:inherit;cursor:pointer}
.bell-item:hover,.bell-item:focus-visible{background:#f1f6ff}
.bell-item-dot{margin-top:7px;width:6px;height:6px;border-radius:100%;background:#4b86d6}
.bell-item strong,.bell-item small,.bell-item time{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.bell-item strong{font-size:13px}.bell-item small,.bell-item time{font-size:11px;color:#61748b}.bell-item time{margin-top:4px;font-variant-numeric:tabular-nums}
.bell-empty{text-align:center;padding:18px;color:#61748b}
.bell-center{display:flex;align-items:center;justify-content:center;gap:8px;padding:10px;border-top:1px solid #e5ecf5;font-size:13px;font-weight:600}
.bell-panel-enter-active,.bell-panel-leave-active{transition:opacity .14s,transform .14s}
.bell-panel-enter-from,.bell-panel-leave-to{opacity:0;transform:translateY(-4px)}
@media(max-width:600px){.bell-panel-card{position:fixed;inset:56px 10px auto auto}}
@media(prefers-reduced-motion:reduce){.bell-panel-enter-active,.bell-panel-leave-active{transition:none}}
</style>
