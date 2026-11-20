<script setup lang="ts">
import {computed} from 'vue'

const model = defineModel<string>({required:true})
const groups = {
  '开发方向': {
    '网页与服务': ['前端开发','后端开发','全栈开发'],
    '客户端': ['桌面应用','移动应用','小程序'],
    '智能与数据': ['人工智能','数据分析','数据可视化'],
    '系统与工程': ['物联网','网络安全','自动化测试'],
  },
  '技术栈': {
    'Web 前端': ['Vue','React','TypeScript'],
    '服务端': ['Java','Spring Boot','Python','Node.js','.NET'],
    '客户端框架': ['Qt','Flutter','Android','Electron'],
    '数据与工程': ['MySQL','PostgreSQL','Redis','Docker'],
  },
  '应用主题': {
    '校园与生活': ['校园服务','学习工具','健康管理'],
    '商业与协作': ['电子商务','团队协作','内容管理'],
    '其他方向': ['公益服务','其他主题'],
  },
}
const taxonomy = Object.entries(groups).map(([label, branches]) => ({
  label, value:label, children:Object.entries(branches).map(([label, leaves]) => ({
    label, value:label, children:leaves.map(value => ({label:value,value})),
  })),
}))
const known = new Set(taxonomy.flatMap(group => group.children.flatMap(branch => branch.children.map(item => item.value))))
const selected = computed({
  get: () => model.value.split(/\s+\/\s+/).filter(Boolean),
  set: (values: string[]) => { model.value = values.join(' / ') },
})
const options = computed(() => {
  const legacy = selected.value.filter(value => !known.has(value))
  return legacy.length ? [...taxonomy,{label:'已有标签',value:'已有标签',children:legacy.map(value=>({label:value,value}))}] : taxonomy
})
</script>

<template>
  <el-cascader v-model="selected" :options="options" popper-class="project-topics-menu"
    :props="{multiple:true,emitPath:false,expandTrigger:'click',checkOnClickLeaf:true}"
    :show-all-levels="false" filterable clearable collapse-tags collapse-tags-tooltip
    placeholder="选择方向、技术栈或应用主题" style="width:100%" />
</template>

<style>
.project-topics-menu .el-cascader-panel {max-width:calc(100vw - 24px);overflow-x:auto}
@media(max-width:550px) {
  .project-topics-menu .el-cascader-menu {width:calc((100vw - 30px) / 3);min-width:0}
  .project-topics-menu .el-cascader-node {padding-inline:8px 18px}
  .project-topics-menu .el-cascader-node__label {padding-inline:6px}
}
</style>
