<script setup lang="ts">defineProps<{title: string}>()</script>
<template>
  <div class="page-head">
    <div>
      <h1>{{title}}</h1>
    </div>
    <div class="actions"><slot /></div>
  </div>
</template>
