<script setup lang="ts">
import {ref} from 'vue'
import {UploadCloud,LoaderCircle} from 'lucide-vue-next'
import {useStore} from '../store'
import {api} from '../api'
import {run} from '../lib'
import type {ProjectFile} from '../types'
const props = defineProps<{projectId:string}>()
const emit = defineEmits<{uploaded:[file:ProjectFile]}>()
const busy = ref(false)
const store = useStore()
async function upload(e:Event) {
 const input = e.target as HTMLInputElement
 const files=Array.from(input.files || [])
 if(!files.length)return
 busy.value = true
 await run(async () => {
  if(files.some(file=>file.size>20*1024*1024))throw Error('每个文件不能超过 20 MB，请重新选择')
  try{for (const file of files) {
   const data = new FormData(); data.append('file',file)
   data.append('category',file.type.startsWith('image/') ? '图片' : /\.(zip|rar|7z)$/i.test(file.name) ? '压缩包' : /\.(js|ts|py|java|vue)$/i.test(file.name) ? '代码' : '文档')
   const saved = await api<ProjectFile>(`/projects/${props.projectId}/files`,data)
   emit('uploaded',saved)
  }}finally{await store.load()}
 },'附件已上传并自动收录到项目资料库')
 busy.value = false; input.value = ''
}
</script>
<template><label class="direct-upload" :class="{'is-busy':busy}"><span class="direct-upload-icon"><LoaderCircle v-if="busy" :size="23" class="upload-spinner" /><UploadCloud v-else :size="23" /></span><span class="direct-upload-copy"><strong>{{busy ? '正在上传…' : '选择文件上传'}}</strong><small>支持多文件，每个不超过 20 MB · 自动收录到资料库</small></span><input type="file" multiple :disabled="busy" @change="upload" /></label></template>
