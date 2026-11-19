<script setup lang="ts">
import {computed} from 'vue'
import {useStore} from '../store'

import {projectCourseScore,projectProgress} from '../projectPresentation'
import {taskPath} from '../teacher'
const props=defineProps<{classId:string}>(),store=useStore()
const rows=computed(()=>store.state!.projects.filter(p=>!['待审核','待修改','已关闭'].includes(p.status)).flatMap(p=>{
 const task=store.state!.tasks.find(t=>t.id===p.taskId && t.classId===props.classId)
 return task?[{p,task,score:projectCourseScore(p,task.milestones),progress:projectProgress(store.state!,p)}]:[]
}))
</script>
<template><section class="section"><div class="section-head"><h2>班级项目评分</h2><div class="actions"><span class="muted">{{rows.length}} 个项目</span></div></div><p class="muted">按已通过任务及其权重累计项目成绩，首次通过前留空。成员分别提交的任务在此展示团队均分，个人成绩在任务批改中查看。</p><el-table :data="rows"><el-table-column label="所属任务" min-width="180"><template #default="{row}">{{row.task.name}}</template></el-table-column><el-table-column label="项目" min-width="160"><template #default="{row}">{{row.p.name}}</template></el-table-column><el-table-column label="完成进度" width="130"><template #default="{row}">{{row.progress}}%</template></el-table-column><el-table-column label="项目成绩" width="110"><template #default="{row}">{{row.score === undefined ? "—" : row.score + " / 100"}}</template></el-table-column><el-table-column label="操作" width="120"><template #default="{row}"><el-button text type="primary" @click="$router.push(taskPath(classId,row.task.id)+'?view=grading')">进入任务评分</el-button></template></el-table-column><template #empty>暂无可评分项目。学生参与任务后在此汇总。</template></el-table></section></template>
