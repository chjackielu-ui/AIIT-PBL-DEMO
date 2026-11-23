import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import {Agent} from 'node:http'
import {cpSync} from 'node:fs'
import {resolve} from 'node:path'
import {createRequire} from 'node:module'

const require = createRequire(import.meta.url)
const pdfAssets = {
  name:'pdf-preview-assets',
  writeBundle({dir}: {dir?:string}) {
    const source = resolve(require.resolve('pdfjs-dist/package.json'), '..')
    for (const folder of ['cmaps','iccs','standard_fonts','wasm']) {
      cpSync(resolve(source,folder), resolve(dir || 'dist','pdf-assets',folder), {recursive:true})
    }
  },
}

export default defineConfig({plugins:[vue(),pdfAssets],server:{port:5173,strictPort:true,proxy:{'/api':{target:'http://127.0.0.1:8080',changeOrigin:true,agent:new Agent({keepAlive:false})}}},build:{rollupOptions:{output:{manualChunks:{'ui':['element-plus'],'excel':['xlsx'],'code-preview':['highlight.js/lib/common'],'vue':['vue','vue-router','pinia']}}}}})
