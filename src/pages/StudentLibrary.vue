<script setup lang="ts">
import {useStore} from '../store'
import PageHead from '../components/PageHead.vue'
import FileLibrary from '../components/FileLibrary.vue'

const store=useStore()
</script>

<template>
  <PageHead title="资料库" />
  <FileLibrary v-if="store.state" personal :project="{id:store.state.user.id,...store.state.library}" :readonly="false" />
</template>
