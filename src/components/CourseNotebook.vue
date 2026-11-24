<script setup lang="ts">
import {BookOpen, ChevronRight} from 'lucide-vue-next'
import type {RouteLocationRaw} from 'vue-router'
import CourseCover from './CourseCover.vue'
import type {Course} from '../types'

defineProps<{name:string; course?:Course; term:string; level?:string; count:number; to:RouteLocationRaw}>()
</script>

<template>
  <RouterLink class="cover" :to="to">
    <CourseCover :course="course" class="art" />
    <span v-if="level" class="level-tab" :class="{advanced: level === '2L2D'}">{{ level }}</span>
    <span class="spine" aria-hidden="true"></span>
    <span class="label">
      <small v-if="term">{{ term }}</small>
      <strong>{{ name }}</strong>
      <span v-if="course?.credits != null" class="metadata">{{ course.credits }} 学分</span>
    </span>
    <span class="foot"><BookOpen :size="16" /> {{ count }} 个项目 <ChevronRight :size="16" /></span>
  </RouterLink>
</template>

<style scoped>
.cover {
  position:relative;box-sizing:border-box;overflow:hidden;width:100%;max-width:216px;height:100%;
  display:flex;flex-direction:column;align-items:flex-start;justify-content:space-between;
  padding:57px 19px 18px 25px;border-radius:4px 11px 11px 4px;
  text-decoration:none;background:#243f68;color:#fff;
  box-shadow:1px 2px 0 #d9dfdf,2px 4px 0 #f6f4ec,3px 5px 0 #a3adc0,3px 9px 16px #263c5b20;
}
.cover:hover {color:#fff;text-decoration:none}
.cover:focus-visible {outline:3px solid #b6934e;outline-offset:3px}
.cover::before {content:'';position:absolute;inset:0;background:repeating-linear-gradient(0deg,#ffffff07 0 1px,transparent 1px 3px)}
.art {position:absolute;inset:auto 0 0;height:38%;aspect-ratio:auto;pointer-events:none;opacity:.72;mask-image:linear-gradient(transparent,#000 40%)}
.spine {position:absolute;inset:0 auto 0 0;width:12px;background:linear-gradient(90deg,#122742,#29456b 60%,#577092 82%,#172d50)}
.label,.foot {position:relative}
.label {display:grid;gap:11px;width:100%}
.label::before {content:'';width:27px;height:2px;background:#d8b676}
.label small,.metadata {font-size:12px;color:#e1e9f4}
.label strong {display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;font-size:20px;line-height:1.45;text-wrap:balance;letter-spacing:.01em;overflow-wrap:anywhere}
.foot {align-self:stretch;display:flex;align-items:center;gap:7px;padding-top:9px;border-top:1px solid #ffffff55;font-size:12px;text-shadow:0 1px 3px #173455}
.foot svg:last-child {margin-left:auto}
.level-tab {position:absolute;z-index:1;top:0;right:17px;padding:8px 9px 13px;background:#e2edfc;color:#183c72;font:700 14px/1.1 ui-monospace,monospace;letter-spacing:.07em;clip-path:polygon(0 0,100% 0,100% 100%,50% 87%,0 100%)}
.level-tab.advanced {background:#f4d28a;color:#423318}
</style>
