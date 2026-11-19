import {defineStore} from 'pinia'
import {ref,computed} from 'vue'
import type {State,User} from './types'
import {api, request} from './api'
export const useStore=defineStore('platform',()=>{
 const state=ref<State>();const loaded=ref(false);const loadError=ref('')
 const user=computed(()=>state.value?.user)
 async function load(){try{const res=await request('/api/state');if(res.status===401){state.value=undefined;loadError.value='';return}if(!res.ok)throw Error('服务暂时不可用，请稍后重试');state.value=await res.json();loadError.value=''}catch(e){loadError.value=e instanceof Error?e.message:String(e)}finally{loaded.value=true}}
 async function login(id:string,role:User['role'],password:string){await api('/login',{id,role,password});await load();if(loadError.value)throw Error(loadError.value)}
 async function logout(){await api('/logout',{});state.value=undefined}
 async function act(op:string,data:Record<string,unknown>={}){const result=await api('/actions',{op,...data});await load();if(loadError.value)throw Error('操作已完成，但页面刷新失败，请重新连接后查看');return result}
 return {state,user,loaded,loadError,load,login,logout,act}
})
