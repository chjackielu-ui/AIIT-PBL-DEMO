<script setup lang="ts">
import MarkdownInput from './MarkdownInput.vue'
import {computed,ref} from 'vue'
import {useStore} from '../store'
import {run,stamp} from '../lib'
import LibraryPicker from './LibraryPicker.vue'
import MarkdownText from './MarkdownText.vue'
import AttachmentLink from './AttachmentLink.vue'
import type {ProjectFile,TeachingClass} from '../types'
const props=defineProps<{tc:TeachingClass}>()
const store=useStore(),active=ref(''),writing=ref(false),busy=ref(false)
const uploading=ref(false)
const title=ref(''),body=ref(''),files=ref<ProjectFile[]>([])
const topic=computed(()=>props.tc.topics?.find(t=>t.id===active.value))
function select(id:string,create=false){
 active.value=id;writing.value=create;title.value='';body.value='';files.value=[]
}
async function save(){
 if(busy.value || uploading.value)return
 busy.value=true
 try {await run(async()=>{
   const created=writing.value
   const result=await store.act(created?'publishTopic':'replyTopic',{classId:props.tc.id,topicId:active.value,title:title.value,body:body.value,fileIds:files.value.map(f=>f.id),sourceProjects:Object.fromEntries(files.value.map(f=>[f.id,f.sourceProjectId || '']))})
   select(created?result.id:active.value)
 },'发表成功')} finally{busy.value=false}
}
const author=(id:string)=>store.state?.users.find(u=>u.id===id)?.name || '课程成员'
</script>
<template><section class="section">
 <div class="section-head"><h2>话题讨论</h2><div class="actions">
 <el-button v-if="topic || writing" @click="select('')">返回话题列表</el-button>
 <el-button v-if="!writing && store.user?.role==='teacher'" type="primary" @click="select('',true)">发布话题</el-button></div></div>
 <template v-if="!topic && !writing"><div v-for="t in tc.topics" :key="t.id" class="list-row"><button class="text-button" @click="select(t.id)">{{t.title}}</button><small>{{t.replies.length}} 条回复 · {{stamp(t.time)}}</small></div><el-empty v-if="!tc.topics?.length" description="本班暂无讨论话题" :image-size="48" /></template>
 <template v-if="topic"><h3>{{topic.title}}</h3><article v-for="post in [topic,...topic.replies]" :key="post.id" class="forum-post"><small class="muted">{{author(post.userId)}} · {{stamp(post.time)}}</small><MarkdownText :text="post.body"/><div v-for="f in post.files" :key="f.id"><AttachmentLink :file="f" :url="'/api/classes/'+tc.id+'/topics/'+topic.id+'/'+(post.id===topic.id?'topic':post.id)+'/files/'+f.id" /></div></article></template>
 <el-form v-if="topic || writing" label-position="top" @submit.prevent="save"><el-form-item v-if="writing" label="话题标题"><el-input v-model="title" maxlength="120"/></el-form-item>
 <el-form-item :label="writing?'话题内容':'我的回复'"><MarkdownInput v-model="body" :rows="3" maxlength="30000" placeholder="支持 Markdown，可附图片或文件"/></el-form-item>
 <LibraryPicker v-model="files" v-model:uploading="uploading" upload /><el-button class="post-submit" type="primary" :loading="busy" :disabled="uploading || files.length>10 || (writing?!title.trim():!body.trim()&&!files.length)" @click="save">{{writing?'发布话题':'发表回复'}}</el-button></el-form>
 </section></template>
<style scoped>.forum-post{padding:10px 0;border-bottom:1px solid var(--line)}form,.post-submit{margin-top:12px}</style>
