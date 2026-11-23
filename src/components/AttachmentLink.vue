<script setup lang="ts">
import {ref} from 'vue'
import {FileText, Download} from 'lucide-vue-next'
import {useStore} from '../store'
const store=useStore()
import type {ProjectFile} from '../types'
import FilePreviewDialog from './FilePreviewDialog.vue'

defineProps<{file:ProjectFile; url:string}>()
const open = ref(false)
</script>

<template>
  <span class="attachment-link">
    <button class="text-button" type="button" :title="file.name" @click="open=true"><FileText :size="15" /><span>{{file.name}}</span></button>
    <a v-if="file.downloadable!==false || store.user?.role!=='student' || url.startsWith('/api/library/')" :href="url + '?download=true'" :aria-label="'下载 ' + file.name" :title="'下载 ' + file.name" download><Download :size="15" /></a>
    <FilePreviewDialog v-if="open" :file="file" :url="url" @close="open=false" />
  </span>
</template>

<style scoped>
.attachment-link{display:inline-flex;align-items:center;gap:10px;max-width:100%;min-width:0}
.attachment-link button{min-width:0;text-align:left}
.attachment-link button span{overflow-wrap:anywhere}
.attachment-link svg{flex:none}
</style>
