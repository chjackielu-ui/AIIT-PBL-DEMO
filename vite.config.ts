import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import {Agent} from 'node:http'
export default defineConfig({plugins:[vue()],server:{port:5173,strictPort:true,proxy:{'/api':{target:'http://127.0.0.1:8080',changeOrigin:true,agent:new Agent({keepAlive:false})}}},build:{rollupOptions:{output:{manualChunks:{'ui':['element-plus'],'excel':['xlsx'],'code-preview':['highlight.js/lib/common'],'vue':['vue','vue-router','pinia']}}}}})
