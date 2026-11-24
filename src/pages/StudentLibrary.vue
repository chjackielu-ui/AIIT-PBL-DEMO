<script setup lang="ts">
import {useStore} from '../store'
import PageHead from '../components/PageHead.vue'
import FileLibrary from '../components/FileLibrary.vue'

const store=useStore()
</script>

<template>
  <div class="student-library"><PageHead title="资料库" />
  <FileLibrary v-if="store.state" personal :project="{id:store.state.user.id,...store.state.library}" :readonly="false" />
  </div>
</template>

<style>
.app-shell:has(.student-library) .app-main{height:100dvh;min-height:0}
.app-shell:has(.student-library) main,.student-library,.student-library .file-desk{min-height:0;display:flex;flex:1;flex-direction:column;overflow:hidden}
.student-library .desk-body{min-height:0;flex:1;overflow:auto}
.student-library :is(.desk-tree,.desk-content){min-height:0;overflow:auto;max-height:none}
@media(max-width:760px){.student-library .desk-body.split{grid-template-rows:auto minmax(0,1fr)}.student-library .desk-tree{max-height:18dvh}}
</style>
