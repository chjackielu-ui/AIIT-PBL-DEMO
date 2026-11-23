<script setup lang="ts">
import MarkdownInput from '../components/MarkdownInput.vue'
import {computed, reactive, ref, watch} from 'vue'
import {Upload,LoaderCircle,Plus,Trash2} from 'lucide-vue-next'
import {useRoute} from 'vue-router'
import {useStore} from '../store'
import {api} from '../api'
import {run} from '../lib'
import AttachmentLink from '../components/AttachmentLink.vue'
import PageHead from '../components/PageHead.vue'
import TeacherContext from '../components/TeacherContext.vue'
import CourseGradebook from '../components/CourseGradebook.vue'
import {courseDimensions,courseFormula,courseRuleIssue} from '../courseGrading'
import type {CourseDimension} from '../types'
const props=defineProps<{embedded?:boolean;targetClass?:string;panel?:'resources'|'rules'}>()
const store = useStore(), route = useRoute()
const classId = ref(String(props.targetClass || route.params.classId || store.state?.teachingClasses[0]?.id || ''))
watch(() => route.params.classId, id => {if(id) classId.value=String(id)})
const tc = computed(() => store.state?.teachingClasses.find(c => c.id === classId.value))
const resourcesOnly = computed(() => props.panel==='resources' || !props.panel && route.path.endsWith('/resources'))
const student = computed(() => store.user?.role === 'student')
const form = reactive({courseDescription:'',dimensions:[] as CourseDimension[]})
watch(tc, c => {form.courseDescription=c?.courseDescription || '';form.dimensions=courseDimensions(c?.gradingRule)},{immediate:true})
const total = computed(() => Math.round(form.dimensions.reduce((sum,d)=>sum+Number(d.weight || 0),0)*100)/100)
const issue = computed(()=>courseRuleIssue(form.dimensions))
const changed = computed(()=>JSON.stringify(form.dimensions)!==JSON.stringify(courseDimensions(tc.value?.gradingRule)))
const saving=ref(false)
function addDimension(){form.dimensions.push({id:crypto.randomUUID(),name:'',weight:0,source:'manual'})}
const course = computed(() => store.state?.courses.find(c=>c.id===tc.value?.courseId))
const directory=ref('')
const directories=computed(()=>[...new Set(tc.value?.resources?.map(f=>f.directory || '根目录') || [])])
const resources=computed(()=>tc.value?.resources?.filter(f=>!directory.value || (f.directory || '根目录')===directory.value))
const busy = ref(false), allowDownload=ref(true)
async function upload(e:Event) {
 const input=e.target as HTMLInputElement, file=input.files?.[0];if(!file)return
 busy.value=true
 await run(async()=>{const data=new FormData();data.append('file',file);data.append('category','reference');data.append('downloadable',String(allowDownload.value));await api(`/classes/${classId.value}/files`,data);await store.load()},'课程资料已上传')
 busy.value=false;input.value=''
}
async function importRule(e:Event) {
 const input=e.target as HTMLInputElement, file=input.files?.[0];if(!file)return
 await run(async()=>{const r=JSON.parse(await file.text()),dimensions=courseDimensions(r);const error=courseRuleIssue(dimensions);if(error)throw Error(error);form.dimensions=dimensions;await save()},'评分规则已导入')
 input.value=''
}
async function save(){if(issue.value)throw Error(issue.value);saving.value=true;try{await store.act('saveCourseDetails',{classId:classId.value,courseDescription:form.courseDescription,gradingRule:{dimensions:form.dimensions}})}finally{saving.value=false}}
</script>
<template>
 <TeacherContext v-if="!embedded && store.user?.role==='teacher'" label="课程与资料" />
 <PageHead v-if="!embedded" :title="resourcesOnly ? '课程参考资料' : '课程详情与评分规则'"></PageHead>
 <el-select v-if="store.user?.role==='admin'" v-model="classId" aria-label="教学班"><el-option v-for="c in store.state?.teachingClasses" :key="c.id" :value="c.id" :label="c.name" /></el-select>
 <template v-if="tc"><section v-if="!resourcesOnly" class="section"><h2>{{course?.name}} · {{tc.name}}</h2><p v-if="course?.status">课程状态 {{course.status}}</p><p v-if="course?.plannedStart" class="muted">计划 {{course.plannedStart.replace('T', ' ')}} 至 {{course.plannedEnd?.replace('T', ' ')}}</p><el-form label-position="top"><el-form-item label="课程说明"><MarkdownInput v-model="form.courseDescription" :rows="3" :disabled="student" /></el-form-item><div class="section-head"><h3>课程总评分数构成</h3><el-button v-if="!student" :disabled="form.dimensions.length>=20" @click="addDimension"><Plus :size="15" />增加评分维度</el-button></div><div class="course-dimension-list"><div v-for="(d,index) in form.dimensions" :key="d.id" class="course-dimension-row"><el-input v-model="d.name" :aria-label="`第${index+1}个评分维度名称`" placeholder="例如：课堂表现" maxlength="30" :disabled="student" /><el-tag :type="d.source==='project'?'primary':'info'">{{d.source==='project'?'自动汇总':'教师录入'}}</el-tag><div class="dimension-weight"><el-input-number v-model="d.weight" :aria-label="`${d.name || '新维度'}权重`" :min="0" :max="100" :precision="2" :disabled="student" /><span>%</span></div><el-button v-if="!student" text type="danger" :disabled="form.dimensions.length<=1" :aria-label="`删除${d.name || '新维度'}维度`" @click="form.dimensions.splice(index,1)"><Trash2 :size="15" /></el-button></div></div><p class="course-rule-formula">{{courseFormula(form.dimensions)}}。</p><p class="muted">实训进度按个人已通过阶段与项目验收的评分汇总；点名、作业及新增维度由教师录入百分制成绩。</p><p v-if="!student && changed" class="muted">保存设置后，班级成绩表、学生课程说明与总评公式会同步更新；删除维度会同时移除该项课程成绩。</p><div v-if="!student" class="actions wrap"><el-button type="primary" :disabled="!!issue" :loading="saving" @click="run(save,'课程设置已保存，相关成绩与规则已同步')">保存设置</el-button><span :class="issue?'error-text':'muted'">合计 {{total}} / 100%</span><span v-if="issue" class="error-text">{{issue}}</span><label v-if="store.user?.role==='admin'" v-file-drop class="upload-label">选择或拖入规则 JSON<input type="file" accept=".json" @change="importRule" /></label></div><p v-if="store.user?.role==='admin'" class="muted">JSON 示例：{"dimensions":[{"id":"attendance","name":"点名","weight":40,"source":"manual"},{"id":"project","name":"实训进度","weight":60,"source":"project"}]}</p></el-form></section>
 <CourseGradebook v-if="!resourcesOnly" :class-id="tc.id" />

 <section v-if="panel!=='rules'" class="section"><div class="section-head"><h2>课程参考资料 · {{tc.resources?.length || 0}} 份</h2><el-checkbox v-if="store.user?.role==='teacher'" v-model="allowDownload">新资料允许下载</el-checkbox><label v-if="store.user?.role==='teacher'" v-file-drop class="upload-label" :class="{'is-busy':busy}"><LoaderCircle v-if="busy" :size="16" class="upload-spinner" /><Upload v-else :size="16" />{{busy?'上传中…':'选择或拖入参考资料'}}<input type="file" :disabled="busy" @change="upload($event)" /></label></div><el-select v-if="directories.length>1 || directories.some(d=>d!=='根目录')" v-model="directory" clearable placeholder="全部目录" aria-label="按资料目录筛选"><el-option v-for="dir in directories" :key="dir" :value="dir" /></el-select>
 <div v-if="tc.syllabus" class="list-row"><AttachmentLink :file="tc.syllabus" :url="`/api/classes/${tc.id}/files/${tc.syllabus.id}`" /><el-tag size="small">课程大纲</el-tag></div><div v-for="f in resources" :key="f.id" class="list-row"><AttachmentLink :file="f" :url="`/api/classes/${tc.id}/files/${f.id}`" /><el-checkbox v-if="store.user?.role==='teacher'" :model-value="f.downloadable!==false" @change="(v:unknown)=>run(()=>store.act('classDownload',{classId:tc!.id,fileId:f.id,downloadable:!!v}))">允许学生下载</el-checkbox><small v-else-if="f.downloadable===false">仅在线查看</small></div><el-empty v-if="!tc.resources?.length && !tc.syllabus" description="教师上传后，全班学生均可查看" :image-size="48" /></section></template>
 <el-empty v-else description="未找到可访问的教学班级" />
</template>
<style scoped>.course-dimension-list{display:grid;gap:10px;margin:12px 0 16px}.course-dimension-row{display:flex;align-items:center;gap:12px;max-width:720px;padding:10px 12px;border:1px solid var(--line);border-radius:6px;background:#fbfcfe}.course-dimension-row>.el-input{flex:1;min-width:100px}.dimension-weight{display:flex;align-items:center;gap:8px;flex:none;color:var(--muted)}.dimension-weight .el-input-number{width:150px}.course-rule-formula{color:#1e3a5f;line-height:1.8}.section>form>p{font-size:13px;line-height:1.7}@media(max-width:600px){.course-dimension-row{flex-wrap:wrap;gap:8px}.course-dimension-row>.el-input{flex-basis:calc(100% - 110px)}.dimension-weight{margin-left:auto}.dimension-weight .el-input-number{width:130px}}</style>

