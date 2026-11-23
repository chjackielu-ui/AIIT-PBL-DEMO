<script setup lang="ts">
import {useStore} from '../store'
const store=useStore()
import type {ProjectFile} from '../types'
import {Download} from 'lucide-vue-next'
import FilePreview from './FilePreview.vue'

defineProps<{file?:ProjectFile; url:string}>()
defineEmits<{close:[]}>()
</script>

<template>
  <el-dialog :model-value="!!file" :title="file?.name" width="min(900px, 94vw)" destroy-on-close @close="$emit('close')">
    <FilePreview v-if="file" :key="url" :file="file" :url="url" />
    <template #footer>
      <a v-if="file && (file.downloadable!==false || store.user?.role!=='student' || url.startsWith('/api/library/'))" class="actions file-preview-download" :href="url + '?download=true'" download><Download :size="15" />下载原文件</a>
      <el-button @click="$emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.file-preview-download{display:inline-flex;margin-right:14px}
</style>
