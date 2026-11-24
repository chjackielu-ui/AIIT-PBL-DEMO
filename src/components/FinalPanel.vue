<script setup lang="ts">
import MarkdownText from './MarkdownText.vue'
import MarkdownInput from './MarkdownInput.vue'
import {computed, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import AttachmentLink from './AttachmentLink.vue'
import {useStore} from '../store'

import type {FinalResult, Project} from '../types'
import {run, safeLink, stamp} from '../lib'
import Status from './Status.vue'
import DirectUpload from './DirectUpload.vue'
import ReviewWorkbench from './ReviewWorkbench.vue'
import DeliverablePicker from './DeliverablePicker.vue'
import {courseEnded, beforeDeadline, taskStarted} from '../taskMode'
import {phaseStatus, projectPhases} from '../projectPresentation'
import {deliverableFiles,validateDeliverables,submissionAttachmentIssue} from '../deliverables'

const props = defineProps<{project: Project; readonly: boolean}>()
const store = useStore()
const teacher = computed(() => store.user?.role === 'teacher')
const member = computed(() => props.project.memberIds.includes(store.user?.id || ''))
const task = computed(() => store.state?.tasks.find(t => t.id === props.project.taskId))
const canSubmit=computed(()=>!courseEnded(store.state?.courses.find(c=>c.id===task.value?.courseId)) && member.value && props.project.leaderId===store.user?.id && !props.readonly && ['进行中','结项待修改','结项待审核'].includes(props.project.status) && taskStarted(task.value) && (beforeDeadline(finalMilestone.value?.due) || !task.value?.startAt && props.project.status==='结项待修改'))
const dialog = ref(false)
const busy = ref(false)
const grading = ref(false)
const returnComment = ref('')
const returnDialog = ref(false)

const finalMilestone = computed(() => task.value?.milestones.find(m => m.isFinal))
const materialRequired = computed(() =>
  task.value?.milestones.find(m => m.isFinal)?.requireMaterials !== false)

const empty = (): FinalResult => ({
  summary: '', description: '', fileIds: [], imageIds: [], link: '', deliverables:{},
  contributions: JSON.parse(JSON.stringify(props.project.contributions)),
  expertName: '', expertOrg: '', expertComment: '', comment: '', approved: false,
})

const form = reactive<FinalResult>(empty())
const attachmentIssue=computed(()=>submissionAttachmentIssue(finalMilestone.value?.deliverables || [],form.deliverables || {},[...form.fileIds,...deliverableFiles(form.deliverables || {})],materialRequired.value))
/** 比例合计：容差比较，展示值保留 1 位小数，避免浮点卡死提交。 */
const total = computed(() => form.contributions.reduce((n, c) => n + (Number(c.percent) || 0), 0))
const totalOk = computed(() => Math.abs(total.value - 100) < 0.01)

const allPassed = computed(() =>
  projectPhases(store.state?.tasks.find(t => t.id === props.project.taskId), props.project)
    .filter(m => !m.isFinal && m.requiresSubmission !== false)
    .every(m => phaseStatus(props.project,m) === '已通过'))

/** 打开弹窗：材料取已提交的 final 快照，贡献以当前 project.contributions 为准
 *  （成员在提交后仍可能更新职责说明，提交时以最新为准；提交成员填写比例）。 */
function open(grade = false) {
  grading.value = grade
  const prev = JSON.parse(JSON.stringify(props.project.final || {}))
  Object.assign(form, empty(), prev)
  form.contributions = JSON.parse(JSON.stringify(props.project.contributions))
  dialog.value = true
}

async function submit() {
  if (!form.summary.trim() || !form.description.trim()) {
    ElMessage.warning('请填写项目总结与最终成果说明')
    return
  }
  busy.value = true
  await run(async () => {
    if(attachmentIssue.value)throw Error(attachmentIssue.value)
    validateDeliverables(finalMilestone.value?.deliverables || [],form.deliverables || {})
    form.fileIds=[...new Set([...form.fileIds,...deliverableFiles(form.deliverables || {})])]
    await store.act('submitFinal', {projectId: props.project.id, final: form})
    dialog.value = false
  }, '结项材料已提交')
  busy.value = false
}

async function reject() {
  if (!returnComment.value.trim()) {
    ElMessage.warning('请填写退回原因')
    return
  }
  await run(async () => {
    await store.act('reviewFinal', {projectId: props.project.id, pass: false, comment: returnComment.value})
    returnDialog.value = false
  }, '结项材料已退回')
}

const name = (uid: string) => store.state?.users.find(u => u.id === uid)?.name
const files = (ids: string[]) => props.project.files.filter(f => ids.includes(f.id))
</script>
<template>
  <div class="section-head">
    <h2>最终项目验收</h2>
    <div class="actions">
      <el-button v-if="teacher && project.status === '结项待审核'" @click="returnDialog = true; returnComment = ''">退回材料</el-button>
      <el-button v-if="teacher && project.status === '结项待审核'" type="primary" @click="open(true)">贡献确认与评分</el-button>
      <el-button v-if="canSubmit" type="primary" :disabled="!allPassed || !finalMilestone" @click="open()">{{project.status === '结项待修改' ? '修改并重新提交' : project.status==='结项待审核'?'修改提交内容':'提交最终验收'}}</el-button>
    </div>
  </div>
  <el-alert v-if="!project.final && !allPassed" title="请先通过其他阶段验收；随后由组长统一提交最终成果并填写成员贡献比例。" type="info" :closable="false" />
  <el-alert v-if="project.final?.approved && project.status !== '已归档'" title="项目已重新开放，公开分享已关闭。旧结项档案保留作历史记录；如需更新成果请重新提交最终验收。" type="warning" :closable="false" />
  <template v-if="project.final">
    <section class="section">
      <div class="section-head"><h2>团队成果</h2><Status :value="project.status" /></div>
      <h3>项目总结</h3>
      <MarkdownText :text="project.final.summary" />
      <h3>最终成果说明</h3>
      <MarkdownText :text="project.final.description" />
      <div class="actions wrap">
        <AttachmentLink v-for="f in files(project.final.fileIds)" :key="f.id" :file="f" :url="`/api/projects/${project.id}/files/${f.id}`" />
      </div>
      <div class="result-images">
        <img v-for="f in files(project.final.imageIds)" :key="f.id" :src="`/api/projects/${project.id}/files/${f.id}`" :alt="f.name" />
      </div>
      <a v-if="safeLink(project.final.link)" :href="safeLink(project.final.link)" target="_blank" rel="noopener noreferrer">打开外部作品链接</a>
      <div v-if="project.final.comment" class="feedback">
        <strong>教师意见</strong>
        <MarkdownText :text="project.final.comment" />
        <p class="muted" v-if="project.final.reviewTime">评分人：{{name(project.final.reviewerId || '')}} · {{stamp(project.final.reviewTime)}}</p>
      </div>
    </section>
    <section class="section">
      <h2>成员贡献与成绩</h2>
      <div class="table-scroll">
        <table class="plain-table">
          <thead><tr><th>成员</th><th>职责与贡献</th><th>确认比例</th><th>个人成绩</th><th>评语</th></tr></thead>
          <tbody>
            <tr v-for="c in project.contributions" :key="c.userId">
              <td>{{name(c.userId)}}</td>
              <td>{{c.role}}<MarkdownText :text="c.description" /><small class="muted">佐证：{{c.evidence}}</small></td>
              <td>{{c.percent}}%</td>
              <td>{{c.score == null ? '待确认' : `${c.score} 分`}}</td>
              <td>{{c.comment || '—'}}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="project.final.projectScore != null">项目总评：{{project.final.projectScore}} 分</p>
    </section>
  </template>
  <el-empty v-else description="尚未提交最终成果" :image-size="64" />
  <el-collapse v-if="project.finalHistory.length">
    <el-collapse-item title="历史结项提交记录">
      <article v-for="(record, i) in project.finalHistory" :key="i" class="phase-block">
        <strong>第 {{i + 1}} 次提交</strong>
        <MarkdownText :text="record.summary" />
        <MarkdownText :text="record.comment || '已提交'" />
      </article>
    </el-collapse-item>
  </el-collapse>
  <el-dialog :model-value="dialog && grading" title="结项成果批阅" width="min(1200px, 96vw)" destroy-on-close @close="dialog=false"><ReviewWorkbench v-if="dialog && grading && finalMilestone" :project="project" :milestone="finalMilestone" @done="dialog=false" /></el-dialog>
  <el-dialog :model-value="dialog && !grading" @close="dialog=false" title="提交结项材料" width="min(1000px,96vw)">
    <el-form label-position="top">
      <h2>成果材料</h2>
      <el-form-item label="项目总结 / 项目介绍" required><MarkdownInput v-model="form.summary" :rows="3" /></el-form-item>
      <el-form-item label="最终成果说明" required><MarkdownInput v-model="form.description" :rows="3" /></el-form-item>
      <DirectUpload :project-id="project.id" @uploaded="f=>{if(f.type.startsWith('image/'))form.imageIds.push(f.id);else form.fileIds.push(f.id)}" />
      <div class="form-grid">
        <el-form-item :label="materialRequired ? (task?.startAt ? '最终附件（至少上传 1 个附件）' : '最终附件材料（含代码 / 压缩包）') : '最终附件材料（可选）'">
          <el-select v-model="form.fileIds" multiple>
            <el-option v-for="f in project.files" :key="f.id" :value="f.id" :label="`${f.name} · ${f.category}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="成果截图（可选）">
          <el-select v-model="form.imageIds" multiple>
            <el-option v-for="f in project.files.filter(f => f.type.startsWith('image/'))" :key="f.id" :value="f.id" :label="f.name" />
          </el-select>
        </el-form-item>
      </div>
      <DeliverablePicker v-model="form.deliverables" :requirements="finalMilestone?.deliverables || []" :files="project.files" />
      <el-form-item label="外部作品链接（可选）"><el-input v-model="form.link" placeholder="https://" /></el-form-item>
      <div class="section-head">
        <h2>成员职责与贡献</h2>
        <span :class="totalOk ? 'success-text' : 'error-text'">比例合计 {{total.toFixed(1)}}% / 100%</span>
      </div>
      <p class="muted">职责与贡献取自成员当前填写，可在此补充完善；提交时本表内容将作为最终提交版本保存。</p>
      <div v-for="c in form.contributions" :key="c.userId" class="phase-form">
        <div class="section-head">
          <strong>{{name(c.userId)}}</strong>
          <div class="actions"><span>贡献比例</span><el-input-number v-model="c.percent" :min="0" :max="100" :precision="1" />%</div>
        </div>
        <div class="form-grid">
          <el-form-item label="角色 / 职责" required><el-input v-model="c.role" /></el-form-item>
          <el-form-item label="个人贡献说明" required><el-input v-model="c.description" /></el-form-item>
        </div>
        <el-form-item label="佐证材料说明 / 对应文件" required><el-input v-model="c.evidence" /></el-form-item>
      </div>
      <el-alert v-if="!totalOk" title="贡献比例合计必须为 100% 才能提交。" type="error" :closable="false" />
      <el-alert v-if="attachmentIssue" :title="attachmentIssue" type="warning" :closable="false" />
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :disabled="!totalOk || !!attachmentIssue" :loading="busy" @click="submit">提交结项审核</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="returnDialog" title="退回结项材料" width="560px">
    <MarkdownInput v-model="returnComment" :rows="4" placeholder="填写需要补充或修改的内容" />
    <template #footer>
      <el-button @click="returnDialog = false">取消</el-button>
      <el-button type="primary" @click="reject">确认退回</el-button>
    </template>
  </el-dialog>
</template>



