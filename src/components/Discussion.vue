<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {AtSign, Braces, Check, ChevronRight, Code2, Download, FileText, Files, FolderOpen, GitCompareArrows, Image, MessageSquareReply, Paperclip, Pin, Send, Upload, X} from 'lucide-vue-next'
import hljs from 'highlight.js/lib/common'
import {ElMessage} from 'element-plus'
import {useStore} from '../store'
import {api, request} from '../api'
import type {Message, Project, ProjectFile} from '../types'
import {fileDownloadUrl, humanSize, run, stamp} from '../lib'
import {attachmentIds, codeLanguage, isCodeFile} from '../discussion'
import CodeWorkbench from './CodeWorkbench.vue'


const props=withDefaults(defineProps<{project:Project;readonly:boolean;search?:string}>(),{search:''})
const emit=defineEmits<{'clear-search':[]}>()
const search=computed(()=>props.search)
const inspectorOpen=ref(false), pinned=ref(false)
defineExpose({openMembers:()=>{memberDialog.value=true}})
function dismissPanel(){if(!pinned.value)inspectorOpen.value=false}
function showPanel(value:'preview'|'diff'|'files'){mode.value=value;inspectorOpen.value=true}
function togglePanel(value:'preview'|'diff'|'files'){inspectorOpen.value=mode.value!==value || !inspectorOpen.value;mode.value=value}
const store=useStore()
const text=ref(''), code=ref(''), codeName=ref('snippet.ts'), language=ref('typescript')
const pendingIds=ref<string[]>([]), replyToId=ref('')
const codeDialog=ref(false), memberDialog=ref(false)
const busy=ref(false), uploading=ref(false)
const mode=ref<'preview'|'diff'|'files'>('preview')
const fileScope=ref<'shared'|'library'>('shared')
const originalId=ref(''), modifiedId=ref(''), originalText=ref(''), modifiedText=ref('')
const inline=ref(false), changes=ref(0), loadingCode=ref(false), codeError=ref('')
const list=ref<HTMLElement>(), uploadInput=ref<HTMLInputElement>(), codeInput=ref<HTMLInputElement>()
const composer=ref<{textarea?:HTMLTextAreaElement}>()
const synced=ref(true), syncing=ref(false)
const filePreview=ref<ProjectFile>()
const draftKey=`project-chat-draft:${store.user!.id}:${props.project.id}`
try {const draft=JSON.parse(sessionStorage.getItem(draftKey) || '{}');text.value=draft.text || '';code.value=draft.code || '';codeName.value=draft.codeName || 'snippet.ts';language.value=draft.language || 'typescript';pendingIds.value=(draft.fileIds || []).filter((id:string)=>props.project.files.some(f=>f.id===id));replyToId.value=draft.replyToId || ''}catch{}
watch([text,code,codeName,language,pendingIds,replyToId],()=>{
 try{sessionStorage.setItem(draftKey,JSON.stringify({text:text.value,code:code.value,codeName:codeName.value,language:language.value,fileIds:pendingIds.value,replyToId:replyToId.value}))}catch{}
},{deep:true})

const userName=(id:string)=>store.state?.users.find(u=>u.id===id)?.name || id
const members=computed(()=>{
 const task=store.state?.tasks.find(t=>t.id===props.project.taskId)
 const teachers=store.state?.teachingClasses.find(c=>c.id===task?.classId)?.teacherIds || []
 return store.state?.users.filter(u=>u.enabled && (props.project.memberIds.includes(u.id) || teachers.includes(u.id))) || []
})
const visibleMessages=computed(()=>props.project.messages.filter(m=>(m.text+' '+m.code+' '+m.codeName+' '+userName(m.userId)+' '+attachmentIds(m).map(id=>props.project.files.find(f=>f.id===id)?.name).join(' ')).toLowerCase().includes(search.value.toLowerCase())))
const highlighted=computed(()=>new Map(props.project.messages.filter(m=>m.code).map(m=>[m.id,hljs.highlightAuto(m.code.slice(0,3000)).value])))
const reply=computed(()=>props.project.messages.find(m=>m.id===replyToId.value))
const replyOf=(m:Message)=>props.project.messages.find(p=>p.id===m.replyToId)
const filesOf=(m:Message)=>props.project.files.filter(f=>attachmentIds(m).includes(f.id))
const pendingFiles=computed(()=>props.project.files.filter(f=>pendingIds.value.includes(f.id)))
const sharedIds=computed(()=>new Set(props.project.messages.flatMap(attachmentIds)))
const groupFiles=computed(()=>props.project.files.filter(f=>sharedIds.value.has(f.id)))
const shownFiles=computed(()=>(fileScope.value==='shared'?groupFiles.value:props.project.files).filter(f=>f.name.toLowerCase().includes(search.value.toLowerCase())).slice().reverse())
const fileMessage=(fid:string)=>props.project.messages.find(m=>attachmentIds(m).includes(fid))
const codeSources=computed(()=>{
 const sources=[
 ...props.project.files.filter(isCodeFile).map(f=>({id:`file:${f.id}`,name:f.name,label:`${f.name} · ${userName(f.userId)} · ${stamp(f.time)}`,language:codeLanguage(f.name),file:f,message:undefined as Message|undefined})),
 ...props.project.messages.filter(m=>m.code).map(m=>({id:`snippet:${m.id}`,name:m.codeName || '代码片段',label:`${m.codeName || '代码片段'} · ${userName(m.userId)} · ${stamp(m.time)}`,language:m.codeLanguage || codeLanguage(m.codeName || ''),file:undefined as ProjectFile|undefined,message:m})),
 ].sort((a,b)=>(a.file?.time || a.message!.time).localeCompare(b.file?.time || b.message!.time))
 const versions=new Map<string,number>()
 return sources.map(s=>{
  const version=(versions.get(s.name) || 0)+1;versions.set(s.name,version)
  return {...s,label:`${s.name} · v${version} · ${userName(s.file?.userId || s.message!.userId)} · ${stamp(s.file?.time || s.message!.time)}`}
 })
})
const modified=computed(()=>codeSources.value.find(s=>s.id===modifiedId.value))
const original=computed(()=>codeSources.value.find(s=>s.id===originalId.value))
const canSend=computed(()=>!props.readonly && !busy.value && !uploading.value && !!(text.value.trim() || code.value.trim() || pendingIds.value.length))
const dateLabel=(time:string)=>time.slice(0,10)
const timeLabel=(time:string)=>time.slice(11,16)
const shouldShowDate=(index:number)=>index===0 || dateLabel(visibleMessages.value[index].time)!==dateLabel(visibleMessages.value[index-1].time)

watch(codeSources,sources=>{
 if(!modifiedId.value && sources.length)modifiedId.value=sources.at(-1)!.id
 if(!originalId.value && sources.length)originalId.value=sources[0].id
},{immediate:true})
watch(modifiedId,id=>{
 const sources=codeSources.value,index=sources.findIndex(s=>s.id===id),current=sources[index]
 if(!current)return
 const prior=sources.slice(0,index).filter(s=>s.name===current.name).at(-1)
 if(prior && (originalId.value===id || original.value?.name!==current.name))originalId.value=prior.id
},{immediate:true})
let codeRequest=0
async function sourceText(id:string){
 const source=codeSources.value.find(s=>s.id===id)
 if(!source)return ''
 if(source.message)return source.message.code
 if(source.file!.size>2*1024*1024)throw Error('代码文件超过 2 MB，请下载查看或选择较小文件')
 const res=await request(`/api/projects/${props.project.id}/files/${source.file!.id}`)
 if(!res.ok)throw Error('代码读取失败，请刷新后重试')
 const blob=await res.blob();if(blob.size>2*1024*1024)throw Error('代码文件超过预览上限')
 return blob.text()
}
watch([modifiedId,originalId,mode],async()=>{
 if(mode.value==='files')return
 const seq=++codeRequest;loadingCode.value=true;codeError.value=''
 try{
  const [after,before]=await Promise.all([sourceText(modifiedId.value),mode.value==='diff'?sourceText(originalId.value):Promise.resolve('')])
  if(seq!==codeRequest)return
  modifiedText.value=after;originalText.value=before
 }catch(e){if(seq===codeRequest)codeError.value=(e as Error).message}
 finally{if(seq===codeRequest)loadingCode.value=false}
},{immediate:true})

function scrollBottom(){list.value?.scrollTo({top:list.value.scrollHeight,behavior:'smooth'})}
watch(()=>props.project.messages.at(-1)?.id,async()=>{
 const nearBottom=!list.value || list.value.scrollHeight-list.value.scrollTop-list.value.clientHeight<140
 await nextTick();if(nearBottom)scrollBottom()
})
function insertMention(name:string){
 const el=composer.value?.textarea,start=el?.selectionStart ?? text.value.length,end=el?.selectionEnd ?? start
 text.value=text.value.slice(0,start)+`@${name} `+text.value.slice(end)
 nextTick(()=>el?.focus())
}
function quote(m:Message){replyToId.value=m.id;nextTick(()=>composer.value?.textarea?.focus())}
function jump(id:string){
 emit('clear-search')
 nextTick(()=>list.value?.querySelector(`[data-message-id="${id}"]`)?.scrollIntoView({behavior:'smooth',block:'center'}))
}
function attach(f:ProjectFile){
 if(props.readonly)return
 if(pendingIds.value.includes(f.id))return
 if(pendingIds.value.length>=10){ElMessage.warning('每条消息最多 10 个附件');return}
 pendingIds.value=[...pendingIds.value,f.id]
}
function dragFile(event:DragEvent,f:ProjectFile){
 event.dataTransfer?.setData('application/x-project-file',JSON.stringify({projectId:props.project.id,fileId:f.id}))
 event.dataTransfer?.setData('text/plain',f.name)
 if(event.dataTransfer)event.dataTransfer.effectAllowed='copy'
}
async function upload(files:FileList|File[]){
 if(props.readonly || uploading.value || busy.value)return
 const batch=Array.from(files);if(!batch.length)return
 if(batch.length+pendingIds.value.length>10){ElMessage.warning('每条消息最多 10 个附件');return}
 if(batch.some(f=>!f.size || f.size>20*1024*1024)){ElMessage.warning('请选择 1 字节至 20 MB 的文件');return}
 uploading.value=true
 await run(async()=>{
  for(const file of batch){
   const data=new FormData();data.append('file',file)
   data.append('category',file.type.startsWith('image/')?'图片':/\.(zip|rar|7z)$/i.test(file.name)?'压缩包':isCodeFile({name:file.name,type:file.type} as ProjectFile)?'代码':'文档')
   const saved=await api<ProjectFile>(`/projects/${props.project.id}/files`,data)
   // Keep completed uploads visible even if a later file fails.
   if(!props.project.files.some(f=>f.id===saved.id))props.project.files.push(saved)
   attach(saved)
   if(isCodeFile(saved)){modifiedId.value=`file:${saved.id}`;showPanel('preview')}
  }
  await store.load()
 },'文件已加入待发送附件，并收录到项目资料库')
 uploading.value=false
}
async function choose(event:Event){const input=event.target as HTMLInputElement;if(input.files)await upload(input.files);input.value=''}
function drop(event:DragEvent){
 if(props.readonly)return
 const data=event.dataTransfer
 if(data?.files.length){void upload(data.files);return}
 const raw=data?.getData('application/x-project-file');if(!raw)return
 try{const item=JSON.parse(raw);if(item.projectId!==props.project.id)throw Error();const f=props.project.files.find(f=>f.id===item.fileId);if(f)attach(f)}catch{ElMessage.warning('只能发送当前项目的资料')}
}
async function send(){
 if(!canSend.value)return
 const outgoing={text:text.value,code:code.value,fileIds:[...pendingIds.value],replyToId:replyToId.value,codeName:codeName.value,codeLanguage:language.value}
 busy.value=true
 await run(async()=>{
  await store.act('message',{projectId:props.project.id,...outgoing})
  if(text.value===outgoing.text)text.value=''
  if(code.value===outgoing.code)code.value=''
  pendingIds.value=pendingIds.value.filter(id=>!outgoing.fileIds.includes(id))
  if(replyToId.value===outgoing.replyToId)replyToId.value=''
  await nextTick();scrollBottom()
 },'')
 busy.value=false
}
function openCode(m:Message){modifiedId.value=`snippet:${m.id}`;showPanel('preview')}
function compareCode(m:Message){
 const source=codeSources.value.find(s=>s.id===`snippet:${m.id}`)
 const previous=codeSources.value.slice(0,codeSources.value.findIndex(s=>s.id===source?.id)).filter(s=>s.name===source?.name).at(-1)
 modifiedId.value=`snippet:${m.id}`;originalId.value=previous?.id || codeSources.value.find(s=>s.id!==modifiedId.value)?.id || '';showPanel('diff')
}
function openFile(f:ProjectFile){
 if(isCodeFile(f)){modifiedId.value=`file:${f.id}`;showPanel('preview')}
 else filePreview.value=f
}
function confirmSnippet(){
 if(!code.value.trim()){ElMessage.warning('请填写代码');return}
 if(!codeName.value.trim()){ElMessage.warning('请填写代码文件名');return}
 codeDialog.value=false
}
async function sync(){
 if(syncing.value || busy.value || uploading.value || document.hidden)return
 syncing.value=true
 const id=props.project.id, uid=store.user?.id
 try {
  const data = await api<Pick<Project,'messages'|'files'|'status'>>(`/projects/${id}/discussion`)
  const current = store.state?.projects.find(p => p.id === id)
  if (current && store.user?.id === uid && props.project.id === id) {
   Object.assign(current, data)
   synced.value = true
  }
 } catch { synced.value = false }
 finally{syncing.value=false}
}
let poll:ReturnType<typeof setInterval>
onMounted(()=>{nextTick(scrollBottom);poll=setInterval(sync,4000)})
onBeforeUnmount(()=>{clearInterval(poll);codeRequest++})
</script>

<template>
 <div class="collaboration-canvas" @keydown.esc="dismissPanel">
 <section class="chat-channel" aria-label="项目聊天窗格" @pointerdown="dismissPanel">
  <div ref="list" class="channel-messages" aria-live="polite">
   <template v-for="(m,index) in visibleMessages" :key="m.id">
    <div v-if="shouldShowDate(index)" class="chat-date"><span>{{dateLabel(m.time)}}</span></div>
    <article class="channel-message" :class="{mine:m.userId===store.user?.id}" :data-message-id="m.id">
     <span class="chat-avatar" :class="{teacher:store.state?.users.find(u=>u.id===m.userId)?.role==='teacher'}">{{userName(m.userId).slice(0,1)}}</span>
     <div class="channel-message-body"><div class="channel-message-meta"><strong>{{userName(m.userId)}}</strong><span v-if="store.state?.users.find(u=>u.id===m.userId)?.role==='teacher'" class="teacher-mark">教师</span><time :title="stamp(m.time)">{{timeLabel(m.time)}}</time><button v-if="!readonly" class="message-reply-button" aria-label="回复此消息" @click="quote(m)"><MessageSquareReply :size="14" /></button></div>
      <button v-if="replyOf(m)" class="quoted-message" @click="jump(m.replyToId!)"><MessageSquareReply :size="13" /><span>{{userName(replyOf(m)!.userId)}}：{{replyOf(m)!.text || replyOf(m)!.codeName || '文件附件'}}</span></button>
      <p v-if="m.text" class="chat-message-text">{{m.text}}</p>
      <div v-if="m.code" class="chat-code-card"><button class="chat-code-title" @click="openCode(m)"><Code2 :size="14" /><strong>{{m.codeName || '代码片段'}}</strong><span>{{m.codeLanguage || 'CODE'}}</span><ChevronRight :size="14" /></button><pre><code class="hljs" v-html="highlighted.get(m.id)" /></pre><div class="chat-code-actions"><button @click="openCode(m)">在代码区查看</button><button @click="compareCode(m)"><GitCompareArrows :size="13" />对比版本</button></div></div>
      <div v-if="filesOf(m).length" class="chat-attachments"><div v-for="f in filesOf(m)" :key="f.id" class="chat-file-card" draggable="true" @dragstart="dragFile($event,f)"><button @click="openFile(f)"><Code2 v-if="isCodeFile(f)" :size="20" /><Image v-else-if="f.type.startsWith('image/')" :size="20" /><FileText v-else :size="20" /><span><strong>{{f.name}}</strong><small>{{humanSize(f.size)}} · {{f.category}}</small></span></button><a :href="fileDownloadUrl(project.id,f.id)" :aria-label="'下载 '+f.name"><Download :size="15" /></a></div></div>
     </div>
    </article>
   </template>
   <el-empty v-if="search && !visibleMessages.length" description="没有匹配的消息" :image-size="40" />
  </div>
  <div v-if="!readonly" class="chat-composer-wrap" @dragover.prevent @drop.prevent="drop">
   <div v-if="reply" class="composer-reply"><MessageSquareReply :size="14" /><span>回复 {{userName(reply.userId)}}：{{reply.text || reply.codeName || '附件'}}</span><button aria-label="取消回复" @click="replyToId=''"><X :size="14" /></button></div>
   <div class="chat-composer"><div class="chat-composer-tools"><el-dropdown trigger="click" @command="insertMention"><button title="提及成员" aria-label="提及成员"><AtSign :size="17" /></button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="u in members.filter(u=>u.id!==store.user?.id)" :key="u.id" :command="u.name">{{u.name}}</el-dropdown-item></el-dropdown-menu></template></el-dropdown><button title="上传附件" aria-label="上传聊天附件" :disabled="uploading" @click="uploadInput?.click()"><Paperclip :size="17" /></button><button title="从项目资料库选择" aria-label="从资料库选择附件" @click="showPanel('files');fileScope='library'"><FolderOpen :size="17" /></button><button title="代码片段" aria-label="编写代码片段" @click="codeDialog=true"><Braces :size="18" /></button><small>拖入文件即可添加附件</small></div>
    <el-input ref="composer" v-model="text" type="textarea" :autosize="{minRows:3,maxRows:6}" placeholder="发送项目消息，或拖入代码、文档…" maxlength="10000" resize="none" @keydown.ctrl.enter.prevent="send" @keydown.meta.enter.prevent="send" />
    <div v-if="code" class="pending-snippet"><Code2 :size="14" /><button @click="codeDialog=true">{{codeName}} · {{code.split('\n').length}} 行代码</button><button aria-label="移除代码片段" @click="code=''"><X :size="13" /></button></div>
    <div v-if="pendingFiles.length" class="pending-files"><div v-for="f in pendingFiles" :key="f.id"><Paperclip :size="13" /><span>{{f.name}}</span><button :aria-label="'移除 '+f.name" @click="pendingIds=pendingIds.filter(id=>id!==f.id)"><X :size="13" /></button></div></div>
    <div class="chat-composer-footer"><small>{{uploading?'正在上传附件…':'Ctrl / ⌘ + Enter 发送 · 附件最多 10 个'}}</small><el-button type="primary" :disabled="!canSend" :loading="busy || uploading" size="small" @click="send"><Send :size="14" />发送</el-button></div>
   </div>
  </div>
  <div v-else class="chat-readonly">{{project.status==='已归档'?'项目已归档，讨论和群文件可继续查看。':'当前项目只读，可查看讨论与群文件。'}}</div>
  <input ref="uploadInput" type="file" multiple hidden @change="choose" />
 </section>

 <nav class="collaboration-tools" aria-label="协作工具">
  <button :class="{selected:inspectorOpen && mode==='preview'}" :aria-expanded="inspectorOpen && mode==='preview'" title="代码" @click="togglePanel('preview')"><Code2 :size="19" /><span>代码</span></button>
  <button :class="{selected:inspectorOpen && mode==='diff'}" :aria-expanded="inspectorOpen && mode==='diff'" title="代码对比" @click="togglePanel('diff')"><GitCompareArrows :size="19" /><span>对比</span></button>
  <button :class="{selected:inspectorOpen && mode==='files'}" :aria-expanded="inspectorOpen && mode==='files'" title="群文件" @click="togglePanel('files')"><Files :size="19" /><span>群文件</span></button>
 </nav>
 <div v-show="inspectorOpen" class="collaboration-inspector-panel">
 <section class="chat-inspector" aria-label="代码与群文件" @dragover.prevent @drop.prevent="drop">
  <template v-if="mode!=='files'">
   <div class="inspector-heading"><div><h3>{{mode==='diff'?'代码版本对比':'代码展示区'}}</h3></div><div class="inspector-heading-tools"><button class="inspector-pin" :class="{selected:pinned}" :aria-pressed="pinned" :aria-label="pinned?'取消固定面板':'固定面板'" :title="pinned?'取消固定':'固定面板'" @click="pinned=!pinned"><Pin :size="16" /></button><button v-if="!readonly" class="inspector-upload" @click="codeInput?.click()" title="上传代码文件" aria-label="上传代码文件"><Upload :size="16" /></button></div></div>
   <div class="code-version-selectors"><label v-if="mode==='diff'"><span>原版本</span><el-select v-model="originalId" filterable placeholder="选择原版本" size="small" aria-label="原版本"><el-option v-for="s in codeSources" :key="s.id" :value="s.id" :label="s.label" /></el-select></label><label><span>{{mode==='diff'?'修改版本':'文件 / 代码片段'}}</span><el-select v-model="modifiedId" filterable placeholder="选择代码" size="small" aria-label="修改版本"><el-option v-for="s in codeSources" :key="s.id" :value="s.id" :label="s.label" /></el-select></label></div>
   <div v-if="mode==='diff'" class="diff-toolbar"><el-switch v-model="inline" active-text="合并视图" inactive-text="左右对比" size="small" /><span>{{originalId===modifiedId?'已选择相同版本':changes+' 处差异'}}</span></div>
   <div v-if="mode==='diff' && original && modified && original.language!==modified.language" class="diff-note">两个版本的文件类型不同，请确认选择的是同一份代码的不同版本。</div>
   <div class="code-file-heading"><Code2 :size="14" /><strong>{{modified?.name || '尚未选择代码'}}</strong><a v-if="modified?.file" :href="fileDownloadUrl(project.id,modified.file.id)" aria-label="下载当前代码"><Download :size="14" /></a></div>
   <div v-loading="loadingCode" class="inspector-code-body"><el-empty v-if="codeError" :description="codeError" :image-size="48" /><CodeWorkbench v-else-if="modified && (mode!=='diff' || original)" :value="modifiedText" :original="originalText" :name="modified.name" :language="modified.language" :diff="mode==='diff'" :inline="inline" @changes="changes=$event" /><el-empty v-else :description="mode==='diff'?'选择两份代码文件或代码片段开始对比':'上传代码或在聊天中发送代码片段'" :image-size="60" /></div>
   <div class="inspector-footnote"><Check :size="12" />只读审阅 · 原文件保持不变</div>
  </template>
  <template v-else>
   <div class="inspector-heading"><div><h3>群文件</h3></div><div class="inspector-heading-tools"><button class="inspector-pin" :class="{selected:pinned}" :aria-pressed="pinned" :aria-label="pinned?'取消固定面板':'固定面板'" :title="pinned?'取消固定':'固定面板'" @click="pinned=!pinned"><Pin :size="16" /></button><button v-if="!readonly" class="inspector-upload" aria-label="上传群文件" @click="uploadInput?.click()"><Upload :size="16" /></button></div></div>
   <div class="group-file-controls"><el-radio-group v-model="fileScope" size="small"><el-radio-button value="shared">聊天群文件 · {{groupFiles.length}}</el-radio-button><el-radio-button value="library">项目资料库 · {{project.files.length}}</el-radio-button></el-radio-group></div>
   <div class="group-file-list"><article v-for="f in shownFiles" :key="f.id" class="group-file-row" draggable="true" @dragstart="dragFile($event,f)"><div class="group-file-top"><span class="group-file-icon"><Code2 v-if="isCodeFile(f)" :size="19" /><FileText v-else :size="19" /></span><button @click="openFile(f)"><strong>{{f.name}}</strong><small>{{humanSize(f.size)}} · {{userName(f.userId)}} · {{stamp(f.time)}}</small></button></div><div class="group-file-actions"><button v-if="!readonly" @click="attach(f)"><Paperclip :size="12" />添加到消息</button><button v-if="fileMessage(f.id)" @click="jump(fileMessage(f.id)!.id)">定位消息</button><a :href="fileDownloadUrl(project.id,f.id)"><Download :size="12" />下载</a></div></article><el-empty v-if="!shownFiles.length" :description="fileScope==='shared'?'尚无群文件，发送附件后会自动归集':'没有匹配的项目资料'" :image-size="56" /></div>
   <div v-if="!readonly" class="group-drop-hint"><Upload :size="17" /><span>把电脑文件拖到这里，上传后加入待发送附件</span></div>
  </template>
  <input ref="codeInput" type="file" multiple hidden accept=".js,.jsx,.ts,.tsx,.vue,.py,.java,.json,.css,.html,.sql,.md,.txt,.c,.cpp,.go,.rs,.yml,.yaml" @change="choose" />
 </section>
 </div>
 </div>

 <el-dialog v-model="codeDialog" title="分享代码片段" width="min(760px, 94vw)" append-to-body><el-form label-position="top"><div class="form-grid"><el-form-item label="文件名"><el-input v-model="codeName" maxlength="180" placeholder="例如 src/main.ts" /></el-form-item><el-form-item label="代码语言"><el-select v-model="language"><el-option v-for="v in ['typescript','javascript','python','java','html','css','json','sql','cpp','go','rust','plaintext']" :key="v" :value="v" /></el-select></el-form-item></div><el-form-item label="代码内容"><el-input v-model="code" type="textarea" :rows="14" maxlength="50000" class="code-input" placeholder="粘贴代码，保留缩进。相同文件名的片段可在右侧对比版本。" /></el-form-item></el-form><template #footer><el-button @click="codeDialog=false">返回消息</el-button><el-button type="primary" @click="confirmSnippet">添加到消息</el-button></template></el-dialog>
 <el-dialog v-model="memberDialog" title="频道成员" width="420px"><div v-for="u in members" :key="u.id" class="list-row"><span>{{u.name}}</span><el-tag size="small">{{u.role==='teacher'?'任课教师':u.id===project.leaderId?'项目负责人':'项目成员'}}</el-tag></div></el-dialog>
 <el-dialog :model-value="!!filePreview" :title="filePreview?.name" width="min(900px, 94vw)" @close="filePreview=undefined"><div v-if="filePreview" class="chat-file-preview"><img v-if="filePreview.type.startsWith('image/')" :src="`/api/projects/${project.id}/files/${filePreview.id}`" :alt="filePreview.name" /><iframe v-else-if="filePreview.type==='application/pdf' || /\.pdf$/i.test(filePreview.name)" :src="`/api/projects/${project.id}/files/${filePreview.id}`" title="群文件预览" /><el-empty v-else description="此格式请下载后查看" :image-size="56" /><a :href="fileDownloadUrl(project.id,filePreview.id)">下载原文件</a></div></el-dialog>
</template>
