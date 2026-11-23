<script setup lang="ts">
import LibraryPicker from './LibraryPicker.vue'
import {ref} from 'vue'
import {UploadCloud,LoaderCircle} from 'lucide-vue-next'
import {useStore} from '../store'
import {run} from '../lib'
import {uploadProjectFile} from '../projectUploads'
import type {ProjectFile} from '../types'

const props = defineProps<{projectId:string}>()
const emit = defineEmits<{uploaded:[file:ProjectFile]}>()
const busy = ref(false)
const store = useStore()
const picked=ref<ProjectFile[]>([])
async function attach(){
  busy.value=true
  await run(async()=>{
    while(picked.value.length) {
      const file=await store.act('copyPersonalToProject',{projectId:props.projectId,fileId:picked.value[0].id,sourceProjectId:picked.value[0].sourceProjectId})
      emit('uploaded',file)
      picked.value.shift()
    }
  })
  busy.value=false
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (busy.value || !files.length) return
  busy.value = true
  await run(async () => {
    if (files.some(file => !file.size || file.size > 20 * 1024 * 1024)) throw Error('请选择 1 字节至 20 MB 的文件')
    try {
      for (const file of files) emit('uploaded', await uploadProjectFile(props.projectId, file))
    } finally {await store.load()}
  }, '附件已上传并自动收录到项目资料库')
  busy.value = false
}
</script>

<template>
  <label v-file-drop class="direct-upload" :class="{'is-busy':busy}">
    <span class="direct-upload-icon"><LoaderCircle v-if="busy" :size="23" class="upload-spinner" /><UploadCloud v-else :size="23" /></span>
    <span class="direct-upload-copy"><strong>{{busy ? '正在上传…' : '点击选择或拖入文件'}}</strong><small>支持多文件，每个不超过 20 MB · 自动收录到资料库</small></span>
    <input type="file" multiple :disabled="busy" @change="upload" />
  </label>
  <div class="actions wrap"><LibraryPicker v-model="picked" /><el-button v-if="picked.length" :loading="busy" @click="attach">添加所选附件</el-button></div>
</template>
