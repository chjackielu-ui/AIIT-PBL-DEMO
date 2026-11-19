<script setup lang="ts">
import {useStore} from './store'

const store=useStore()
</script>
<template><div v-if="store.loadError" class="connection-error" role="alert">{{store.loadError}}<el-button @click="store.load()">重新连接</el-button></div><RouterView /></template>
