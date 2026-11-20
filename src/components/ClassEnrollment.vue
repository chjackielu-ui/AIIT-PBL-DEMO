<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {Download,Upload,QrCode} from 'lucide-vue-next'
import QRCode from 'qrcode'
import {ElMessage} from 'element-plus'
import {useStore} from '../store'
import {api} from '../api'
import {run} from '../lib'
const props=defineProps<{classId:string}>(),open=defineModel<boolean>({default:false}),store=useStore()
const tab=ref('scan'),busy=ref(false),token=ref(''),base=ref(''),addresses=ref<string[]>([]),qr=ref(''),filename=ref('')
interface StudentRow {sequence:string;name:string;number:string;className:string;contact:string;row?:number;status?:string;error?:string}
const rows=ref<StudentRow[]>([]),sourceRows=ref<StudentRow[]>([])
const result=ref<{added:number;created:number;skipped:number}>()
const tc=computed(()=>store.state?.teachingClasses.find(c=>c.id===props.classId))
const roster=computed(()=>store.state?.enrollments.filter(e=>e.classId===props.classId).map(e=>store.state?.users.find(u=>u.id===e.studentId)).filter(u=>!!u) || [])
const url=computed(()=>{try{const root=new URL(base.value);if(!['http:','https:'].includes(root.protocol))return '';return new URL('/join/'+token.value,root.origin).href}catch{return ''}})
const errors=computed(()=>rows.value.filter(r=>r.error).length)
watch(url,async value=>{qr.value=value && token.value?await QRCode.toDataURL(value,{width:208,margin:2,color:{dark:'#1e3a5f',light:'#ffffff'}}):''})
watch(open,async value=>{
 if(!value)return
 tab.value='scan';token.value='';rows.value=[];sourceRows.value=[];filename.value='';result.value=undefined
 await run(async()=>{
  busy.value=true
  try{
   const invite=await store.act('createClassInvite',{classId:props.classId});token.value=invite.token
   addresses.value=await api<string[]>('/class-access-addresses')
   base.value=['localhost','127.0.0.1'].includes(location.hostname)?addresses.value[0] || location.origin:location.origin
  }finally{busy.value=false}
 },'')
})
async function sample(){await run(async()=>{
 const XLSX=await import('xlsx'),sheet=XLSX.utils.aoa_to_sheet([['序号','姓名','学号','班级','联系方式'],[1,'示例学生','2026000001','软件2601','13800000000']])
 sheet['!cols']=[{wch:8},{wch:16},{wch:24},{wch:22},{wch:24}]
 for(const key of ['C2','E2'])if(sheet[key])sheet[key].z='@'
 const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,sheet,'选课学生');XLSX.writeFile(book,'选课学生导入样表.xlsx')
},'')}
async function upload(e:Event){
 const input=e.target as HTMLInputElement,file=input.files?.[0];if(!file)return
 busy.value=true;rows.value=[];sourceRows.value=[];result.value=undefined;filename.value=''
 await run(async()=>{
  if(file.size>5*1024*1024)throw Error('表格请控制在 5 MB 以内')
  const XLSX=await import('xlsx'),book=XLSX.read(await file.arrayBuffer(),{type:'array'})
  const data=XLSX.utils.sheet_to_json<unknown[]>(book.Sheets[book.SheetNames[0]],{header:1,defval:'',raw:false}).filter(r=>r.some(v=>String(v).trim()))
  const columns=['序号','姓名','学号','班级','联系方式'],header=data[0]?.map(v=>String(v).trim()) || []
  if(columns.some(c=>!header.includes(c)))throw Error('表头需包含：序号、姓名、学号、班级、联系方式，请使用下载样表')
  const parsed=data.slice(1).map(r=>{const cell=(name:string)=>String(r[header.indexOf(name)] ?? '').trim();return {sequence:cell('序号'),name:cell('姓名'),number:cell('学号'),className:cell('班级'),contact:cell('联系方式')}})
  const check=await api<{rows:StudentRow[];errors:number}>(`/classes/${props.classId}/students/preview`,{rows:parsed})
  rows.value=check.rows;sourceRows.value=parsed;filename.value=file.name
 },'')
 busy.value=false;input.value=''
}
async function confirmImport(){busy.value=true;await run(async()=>{result.value=await store.act('importClassStudents',{classId:props.classId,rows:sourceRows.value});rows.value=[];sourceRows.value=[]},'选课学生导入完成');busy.value=false}
async function copy(){await run(async()=>{await navigator.clipboard.writeText(url.value);ElMessage.success('入班链接已复制')},'')}
</script>
<template>
 <el-dialog v-model="open" title="扫码或批量添加学生" width="min(920px,94vw)" append-to-body>
  <p class="muted">{{tc?.name}} · 当前 {{roster.length}} 名选课学生</p>
  <el-tabs v-model="tab"><el-tab-pane name="scan" label="扫码加入班级" /><el-tab-pane name="import" label="表格批量导入" /></el-tabs>
  <div v-if="tab==='scan'" v-loading="busy" class="class-invite-layout">
   <div class="class-invite-qr"><img v-if="qr" :src="qr" alt="扫码加入当前教学班" width="208" height="208" /><QrCode v-else :size="80" /><strong>{{tc?.name}}</strong></div>
   <div class="class-invite-info"><h3>学生扫码，确认后加入班级</h3><p class="muted">学生登录后确认自己的身份，即可加入本班并查看已发布任务；重复扫码不会重复添加。</p><el-form label-position="top"><el-form-item label="学生可访问的平台地址"><el-input v-model="base" placeholder="例如 http://192.168.1.100:8080" /></el-form-item><el-form-item label="入班链接"><el-input :model-value="url" readonly /></el-form-item></el-form><p class="muted">局域网演示时，学生设备与本机需在同一网络。可根据部署地址修改上方地址。</p><div class="actions wrap"><el-button :disabled="!url || !token" @click="copy">复制入班链接</el-button><a v-if="qr" :href="qr" download="班级入班二维码.png" class="upload-label"><Download :size="15" />下载二维码</a></div></div>
  </div>
  <template v-else-if="tab==='import'"><p>表格包含序号、姓名、学号、班级、联系方式。按学号识别已有账号，新学生自动建立账号并入班，重复入班自动跳过。</p><div class="actions wrap"><el-button @click="sample"><Download :size="15" />下载导入样表</el-button><label class="upload-label"><Upload :size="15" />{{busy?'读取中…':'上传学生表格'}}<input type="file" accept=".xlsx,.xls,.csv" :disabled="busy" @change="upload" /></label><span class="muted">{{filename}}</span></div><p class="muted">学号及联系方式请按文本填写，保留前导零；联系方式可留空。样表中的示例行请替换为实际名单。</p><el-alert v-if="result" :title="`已新增入班 ${result.added} 人，其中新建账号 ${result.created} 人；已在本班跳过 ${result.skipped} 人。`" type="success" :closable="false" /><el-table v-if="rows.length" :data="rows" max-height="340"><el-table-column prop="sequence" label="序号" width="65" /><el-table-column prop="name" label="姓名" width="100" /><el-table-column prop="number" label="学号" min-width="140" /><el-table-column prop="className" label="班级" min-width="120" /><el-table-column prop="contact" label="联系方式" min-width="140" /><el-table-column label="预览结果" min-width="180"><template #default="{row}"><span :class="row.error?'error-text':'muted'">{{row.error || row.status}}</span></template></el-table-column></el-table><p v-if="rows.length" :class="errors?'error-text':'muted'">共 {{rows.length}} 行，{{errors?errors+' 行需修正后重新上传':'确认名单后点击导入'}}</p><el-button v-if="rows.length" type="primary" :disabled="!!errors" :loading="busy" @click="confirmImport">确认导入 {{rows.length}} 名学生</el-button></template>
  <template #footer><el-button @click="open=false">关闭</el-button></template>
 </el-dialog>
</template>
<style scoped>
.class-invite-layout{display:grid;grid-template-columns:240px minmax(0,1fr);gap:28px;padding:12px 0}.class-invite-qr{display:flex;flex-direction:column;align-items:center;gap:12px;padding:12px;border:1px solid var(--line);border-radius:6px;align-self:start;color:#1e3a5f}.class-invite-qr img{max-width:100%;height:auto}.class-invite-info{min-width:0}.class-invite-info h3{margin-top:0}.class-invite-info p{font-size:13px;line-height:1.7}.actions{margin-bottom:12px}@media(max-width:650px){.class-invite-layout{grid-template-columns:1fr;gap:16px}.class-invite-qr{max-width:240px;justify-self:center}}
</style>
