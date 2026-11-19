<script setup lang="ts">
import {useStore} from '../store'

const store = useStore()
</script>
<template>
  <div class="standalone">
    <el-result title="页面不存在" sub-title="请检查链接或返回工作台。">
      <template #extra>
        <RouterLink :to="store.user ? `/${store.user.role}/dashboard` : '/login'">
          <el-button type="primary">返回工作台</el-button>
        </RouterLink>
      </template>
    </el-result>
  </div>
</template>
