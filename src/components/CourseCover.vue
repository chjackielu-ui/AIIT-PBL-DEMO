<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {safeLink} from '../lib'
import type {Course} from '../types'

const {course} = defineProps<{course?: Course}>()
const assignedCover = computed(() => {
  const url = course?.coverUrl
  return url && (url.startsWith('/') && !url.startsWith('//') ? url : safeLink(url))
})
const failed = ref(false)
watch(assignedCover, () => { failed.value = false })
</script>

<template>
  <div class="course-cover">
    <img v-if="assignedCover && !failed" :src="assignedCover" alt="" loading="lazy" @error="failed = true">
    <div v-else class="notebook" aria-hidden="true"><i></i><b></b><em></em></div>
    <slot />
  </div>
</template>

<style scoped>
.course-cover {
  position:relative;
  isolation:isolate;
  width:100%;
  min-width:0;
  overflow:hidden;
  aspect-ratio:14/5;
  background:#34568b;
}
.course-cover img,.notebook {position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.notebook {
  overflow:hidden;
  background-color:#34568b;
  background-image:linear-gradient(#a9c7ff33 1px,transparent 1px),linear-gradient(90deg,#a9c7ff33 1px,transparent 1px);
  background-size:13px 13px;
}
.notebook i,.notebook b,.notebook em {position:absolute;display:block;transform:rotate(-24deg)}
.notebook i {
  height:135%;
  width:30%;
  left:16%;
  top:-15%;
  border:1px solid #b8d3ff;
  background:repeating-linear-gradient(90deg,#a4c7fa 0 1px,transparent 1px 6px);
}
.notebook b {width:37%;height:72%;right:13%;top:24%;border:3px double #dce8ff;border-radius:50%}
.notebook em {width:31%;height:10%;right:-5%;top:17%;background:#8daee6}
</style>
