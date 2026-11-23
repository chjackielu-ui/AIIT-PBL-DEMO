<script setup lang="ts">
import {computed,ref} from 'vue'
import {useStore} from '../store'
import {run} from '../lib'
import {uploadProjectFile} from '../projectUploads'
import type {ProjectFile} from '../types'
const props=defineProps<{upload?:boolean;permissions?:boolean}>()
const files=defineModel<ProjectFile[]>({required:true})
const busy=defineModel<boolean>('uploading',{default:false})
const store=useStore(), allow=ref(true)
const groups=computed(()=>{
 const state=store.state
 if(!state)return []
 const items=[{name:'个人文件',key:'personal',files:state.library.files}]
 for(const folder of state.library.folders){
   if(!folder.projectId)continue
   const project=state.projects.find(p=>p.id===folder.projectId)
   if(project)items.push({name:project.name,key:folder.id,files:project.files.map(f=>({...f,sourceProjectId:project.id}))})
 }
 const available=new Set(items.flatMap(g=>g.files.map(choiceKey)))
 const retained=files.value.filter(f=>!available.has(choiceKey(f)))
 if(retained.length)items.push({name:'已选附件',key:'retained',files:retained})
 return items.filter(g=>g.files.length)
})
function choiceKey(file:ProjectFile){return JSON.stringify([file.sourceProjectId || '',file.id])}
const selected=computed({
 get:()=>files.value.map(choiceKey),
 set:(keys:string[])=>{
   const options=new Map(groups.value.flatMap(g=>g.files).map(f=>[choiceKey(f),f]))
   for(const file of files.value)options.set(choiceKey(file),file)
   const chosen=new Map<string,ProjectFile>()
   for(const key of keys){
     const file=options.get(key)
     if(file)chosen.set(file.id,{...file})
   }
   files.value=[...chosen.values()]
 }
})
async function choose(e:Event){
 const input=e.target as HTMLInputElement, batch=Array.from(input.files||[])
 input.value=''
 busy.value=true
 await run(async()=>{
   try {for(const f of batch) files.value=[...files.value,await uploadProjectFile('',f,'',true,allow.value)]}
   finally {await store.load()}
 })
 busy.value=false
}
</script>
<template><div class="library-picker">
 <el-select v-model="selected" multiple filterable collapse-tags collapse-tags-tooltip placeholder="从我的总资料库选择" aria-label="从我的总资料库选择"><el-option-group v-for="group in groups" :key="group.key" :label="group.name"><el-option v-for="f in group.files" :key="choiceKey(f)" :label="f.name" :value="choiceKey(f)" /></el-option-group></el-select>
 <label v-if="upload" class="upload-label" v-file-drop>{{busy?'上传中…':'上传新附件'}}<input type="file" multiple :disabled="busy" @change="choose" /></label>
 <el-checkbox v-if="permissions && upload" v-model="allow">新附件允许下载</el-checkbox>
 <div v-if="props.permissions" class="reference-list"><div v-for="f in files" :key="f.id" class="list-row"><span>{{f.name}}</span><el-checkbox :model-value="f.downloadable!==false" @change="(v:unknown)=>f.downloadable=!!v">允许学生下载</el-checkbox></div></div>
 </div></template>
<style scoped>.library-picker{display:flex;flex-wrap:wrap;align-items:center;gap:8px;width:100%}.library-picker>.el-select{width:min(330px,100%)}.reference-list{flex-basis:100%}.reference-list:empty{display:none}</style>
